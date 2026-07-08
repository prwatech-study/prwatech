package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.AppleAuthRequestDTO;
import com.prwatech.skillama.dto.EmailContinueRequestDTO;
import com.prwatech.skillama.dto.GoogleAuthRequestDTO;
import com.prwatech.skillama.dto.OnboardingCompleteRequestDTO;
import com.prwatech.skillama.dto.OtpContinueRequestDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.EmailValidation;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuthAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthAuthService.class);
    private static final String GOOGLE_TOKEN_INFO = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final SkillamaUserRepository userRepository;
    private final UserService userService;
    private final UserContactService userContactService;
    private final FreemiumService freemiumService;
    private final OnboardingService onboardingService;
    private final OtpService otpService;
    private final PasswordEncode passwordEncode;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${skillama.oauth.google.client-id:}")
    private String googleClientId;

    @Value("${skillama.oauth.apple.client-id:}")
    private String appleClientId;

    @Transactional
    public User authenticateWithGoogle(GoogleAuthRequestDTO request) {
        if (request.getIdToken() == null || request.getIdToken().isBlank()) {
            throw new IllegalArgumentException("Google idToken is required");
        }
        GoogleProfile profile = verifyGoogleIdToken(request.getIdToken().trim());
        return resolveOAuthUser(
                profile.email,
                profile.name,
                profile.picture,
                User.AuthProvider.GOOGLE,
                profile.sub,
                null);
    }

    @Transactional
    public User authenticateWithApple(AppleAuthRequestDTO request) {
        if (request.getIdentityToken() == null || request.getIdentityToken().isBlank()) {
            throw new IllegalArgumentException("Apple identityToken is required");
        }
        AppleProfile profile = verifyAppleIdentityToken(request.getIdentityToken().trim());
        String name = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : profile.email != null ? profile.email.split("@")[0] : "Learner";
        return resolveOAuthUser(
                profile.email,
                name,
                null,
                User.AuthProvider.APPLE,
                null,
                profile.sub);
    }

    @Transactional
    public User emailContinue(EmailContinueRequestDTO request) {
        EmailValidation.assertValidFormat(request.getEmail());
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        String email = userContactService.normalizeEmail(request.getEmail());
        Optional<User> existing = userService.findByEmail(email);
        if (existing.isEmpty()) {
            existing = userRepository.findByEmailIgnoreCase(email);
        }

        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isActive()) {
                throw new IllegalStateException("Account is not activated. Please contact admin.");
            }
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                throw new IllegalStateException(
                        "This account uses social sign-in. Continue with Google or Apple.");
            }
            if (!userService.validatePassword(request.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid email or password");
            }
            return user;
        }

        EmailValidation.assertPasswordLength(request.getPassword());
        if (request.getVerificationToken() == null || request.getVerificationToken().isBlank()) {
            throw new IllegalArgumentException("Please verify your email with OTP first.");
        }
        otpService.validateVerificationToken(
                email, request.getVerificationToken(), EmailOtp.OtpPurpose.SIGNUP);

        User user = new User();
        user.setEmail(email);
        user.setName(email.split("@")[0]);
        user.setPassword(passwordEncode.getEncryptedPassword(request.getPassword()));
        user.setActive(true);
        user.setEmailVerified(true);
        user.setRole(User.UserRole.USER);
        user.setPlanTier(User.PlanTier.FREEMIUM);
        user.setQueryCreditsUsed(0);
        user.setQueryCreditsLimit(FreemiumService.FREEMIUM_QUERY_LIMIT);
        user.setEnabledModules(new ArrayList<>(FreemiumService.FREEMIUM_BASE_MODULES));
        user.setReferralCode(FreemiumService.generateReferralCode());
        user.setAuthProvider(User.AuthProvider.EMAIL);
        user.setOnboardingCompleted(false);
        user.setCreatedAt(IndiaTime.now());
        user.setUpdatedAt(IndiaTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User otpContinue(OtpContinueRequestDTO request) {
        EmailValidation.assertValidFormat(request.getEmail());
        String email = userContactService.normalizeEmail(request.getEmail());

        if (request.getVerificationToken() != null && !request.getVerificationToken().isBlank()) {
            otpService.validateVerificationToken(email, request.getVerificationToken());
        } else if (request.getOtp() != null && !request.getOtp().isBlank()) {
            otpService.verifyOtp(email, request.getOtp());
        } else {
            throw new IllegalArgumentException("OTP or verificationToken required");
        }

        Optional<User> existing = userService.findByEmail(email);
        if (existing.isEmpty()) {
            existing = userRepository.findByEmailIgnoreCase(email);
        }

        if (existing.isEmpty()) {
            throw new IllegalArgumentException(
                    "No account found for this email. Please sign up to create an account.");
        }

        User user = existing.get();
        if (!user.isActive()) {
            throw new IllegalStateException("Account is not activated. Please contact admin.");
        }
        return user;
    }

    @Transactional
    public User completeOnboarding(String userId, OnboardingCompleteRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        FreemiumService.validatePhone(request.getPhone());
        userContactService.assertContactUnique(
                user.getEmail(),
                request.getPhone(),
                user.getId());

        user.setName(request.getName().trim());
        user.setPhone(FreemiumService.normalizePhone(request.getPhone()));

        if (user.getPlanTier() == null || user.getPlanTier() == User.PlanTier.FREEMIUM) {
            if (user.getPlanTier() == null) {
                user.setPlanTier(User.PlanTier.FREEMIUM);
                user.setQueryCreditsUsed(0);
                user.setQueryCreditsLimit(FreemiumService.FREEMIUM_QUERY_LIMIT);
                user.setEnabledModules(new ArrayList<>(FreemiumService.FREEMIUM_BASE_MODULES));
                if (user.getReferralCode() == null) {
                    user.setReferralCode(FreemiumService.generateReferralCode());
                }
            }
            freemiumService.applyFreemiumCourseOnOnboarding(user, request.getFreemiumCourseId());
            if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
                freemiumService.applyReferralOnOnboarding(user, request.getReferralCode());
            }
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())
                || user.getAuthProvider() == User.AuthProvider.GOOGLE
                || user.getAuthProvider() == User.AuthProvider.APPLE) {
            user.setEmailVerified(true);
        }
        user.setActive(true);
        onboardingService.markOnboardingComplete(user);
        user.setOnboardingCompletedAt(IndiaTime.now());
        user.setUpdatedAt(IndiaTime.now());
        User saved = userRepository.save(user);
        freemiumService.enrollFreemiumCourseIfNeeded(saved, request.getFreemiumCourseId());
        return saved;
    }

    private User resolveOAuthUser(
            String email,
            String name,
            String picture,
            User.AuthProvider provider,
            String googleSub,
            String appleSub) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email not provided by " + provider.name() + ". Allow email sharing and try again.");
        }
        String normalizedEmail = userContactService.normalizeEmail(email);

        Optional<User> byProvider = findByProviderSub(provider, googleSub, appleSub);
        if (byProvider.isPresent()) {
            return updateOAuthProfile(byProvider.get(), name, picture);
        }

        Optional<User> byEmail = userService.findByEmail(normalizedEmail);
        if (byEmail.isEmpty()) {
            byEmail = userRepository.findByEmailIgnoreCase(normalizedEmail);
        }
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            if (isLinkedToProvider(user, provider, googleSub, appleSub)) {
                return updateOAuthProfile(user, name, picture);
            }
            if (hasConflictingProviderSub(user, provider, googleSub, appleSub)) {
                throw new IllegalStateException(
                        "This email is already linked to a different "
                                + provider.name().toLowerCase()
                                + " account.");
            }
            linkProviderToUser(user, provider, googleSub, appleSub);
            if (user.getAuthProvider() == null && isPasswordAccount(user)) {
                user.setAuthProvider(User.AuthProvider.EMAIL);
            }
            return updateOAuthProfile(user, name, picture);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setName(name != null && !name.isBlank() ? name.trim() : normalizedEmail.split("@")[0]);
        user.setProfileImageUrl(picture);
        user.setAuthProvider(provider);
        user.setGoogleSub(googleSub);
        user.setAppleSub(appleSub);
        user.setActive(true);
        user.setEmailVerified(true);
        user.setRole(User.UserRole.USER);
        user.setPlanTier(User.PlanTier.FREEMIUM);
        user.setQueryCreditsUsed(0);
        user.setQueryCreditsLimit(FreemiumService.FREEMIUM_QUERY_LIMIT);
        user.setEnabledModules(new ArrayList<>(FreemiumService.FREEMIUM_BASE_MODULES));
        user.setReferralCode(FreemiumService.generateReferralCode());
        user.setOnboardingCompleted(false);
        user.setCreatedAt(IndiaTime.now());
        user.setUpdatedAt(IndiaTime.now());
        return userRepository.save(user);
    }

    private Optional<User> findByProviderSub(
            User.AuthProvider provider, String googleSub, String appleSub) {
        if (provider == User.AuthProvider.GOOGLE && googleSub != null) {
            return userRepository.findByGoogleSub(googleSub);
        }
        if (provider == User.AuthProvider.APPLE && appleSub != null) {
            return userRepository.findByAppleSub(appleSub);
        }
        return Optional.empty();
    }

    private void linkProviderToUser(
            User user, User.AuthProvider provider, String googleSub, String appleSub) {
        if (provider == User.AuthProvider.GOOGLE) {
            user.setGoogleSub(googleSub);
        } else if (provider == User.AuthProvider.APPLE) {
            user.setAppleSub(appleSub);
        }
    }

    private boolean isLinkedToProvider(
            User user, User.AuthProvider provider, String googleSub, String appleSub) {
        if (provider == User.AuthProvider.GOOGLE) {
            return googleSub != null && googleSub.equals(user.getGoogleSub());
        }
        if (provider == User.AuthProvider.APPLE) {
            return appleSub != null && appleSub.equals(user.getAppleSub());
        }
        return false;
    }

    private boolean isPasswordAccount(User user) {
        return user.getPassword() != null && !user.getPassword().isBlank();
    }

    private boolean hasConflictingProviderSub(
            User user, User.AuthProvider provider, String googleSub, String appleSub) {
        if (provider == User.AuthProvider.GOOGLE) {
            return user.getGoogleSub() != null
                    && googleSub != null
                    && !googleSub.equals(user.getGoogleSub());
        }
        if (provider == User.AuthProvider.APPLE) {
            return user.getAppleSub() != null
                    && appleSub != null
                    && !appleSub.equals(user.getAppleSub());
        }
        return false;
    }

    private User updateOAuthProfile(User user, String name, String picture) {
        if (name != null && !name.isBlank()
                && (user.getName() == null || user.getName().isBlank()
                        || user.getName().equals(user.getEmail().split("@")[0]))) {
            user.setName(name.trim());
        }
        if (picture != null && !picture.isBlank()) {
            user.setProfileImageUrl(picture);
        }
        user.setUpdatedAt(IndiaTime.now());
        return userRepository.save(user);
    }

    private GoogleProfile verifyGoogleIdToken(String idToken) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    GOOGLE_TOKEN_INFO + idToken, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Invalid Google token");
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            if (node.has("error_description")) {
                throw new IllegalArgumentException(node.path("error_description").asText("Invalid Google token"));
            }
            String aud = node.path("aud").asText(null);
            if (googleClientId != null && !googleClientId.isBlank()
                    && aud != null && !googleClientId.equals(aud)) {
                throw new IllegalArgumentException("Google token audience mismatch");
            }
            return new GoogleProfile(
                    node.path("sub").asText(null),
                    node.path("email").asText(null),
                    node.path("name").asText(null),
                    node.path("picture").asText(null));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Google token verification failed", e);
            throw new IllegalArgumentException("Invalid Google token");
        }
    }

    private AppleProfile verifyAppleIdentityToken(String identityToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(identityToken);
            String kid = signedJWT.getHeader().getKeyID();
            JWKSet jwkSet = JWKSet.load(new URL(APPLE_JWKS_URL));
            JWK jwk = jwkSet.getKeyByKeyId(kid);
            if (jwk == null) {
                throw new IllegalArgumentException("Apple signing key not found");
            }
            RSAPublicKey publicKey = jwk.toRSAKey().toRSAPublicKey();
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new IllegalArgumentException("Invalid Apple token signature");
            }
            var claims = signedJWT.getJWTClaimsSet();
            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw new IllegalArgumentException("Invalid Apple token issuer");
            }
            if (appleClientId != null && !appleClientId.isBlank()) {
                Object aud = claims.getAudience() != null && !claims.getAudience().isEmpty()
                        ? claims.getAudience().get(0)
                        : null;
                if (aud != null && !appleClientId.equals(aud.toString())) {
                    throw new IllegalArgumentException("Apple token audience mismatch");
                }
            }
            if (claims.getExpirationTime() != null
                    && claims.getExpirationTime().toInstant().isBefore(java.time.Instant.now())) {
                throw new IllegalArgumentException("Apple token expired");
            }
            return new AppleProfile(
                    claims.getSubject(),
                    claims.getStringClaim("email"));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Apple token verification failed", e);
            throw new IllegalArgumentException("Invalid Apple token");
        }
    }

    private String generateReferralCode() {
        return FreemiumService.generateReferralCode();
    }

    private static final class GoogleProfile {
        final String sub;
        final String email;
        final String name;
        final String picture;

        GoogleProfile(String sub, String email, String name, String picture) {
            this.sub = sub;
            this.email = email;
            this.name = name;
            this.picture = picture;
        }
    }

    private static final class AppleProfile {
        final String sub;
        final String email;

        AppleProfile(String sub, String email) {
            this.sub = sub;
            this.email = email;
        }
    }
}
