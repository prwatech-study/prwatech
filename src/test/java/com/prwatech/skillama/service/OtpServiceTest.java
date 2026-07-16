package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.OtpVerifyResponseDTO;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.repository.EmailOtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock private EmailOtpRepository emailOtpRepository;
    @Mock private EmailServiceImpl emailService;
    @Mock private PasswordEncode passwordEncode;
    @Mock private UserService userService;
    @Mock private UserContactService userContactService;

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(emailOtpRepository, emailService, passwordEncode,
                userService, userContactService);
        when(emailOtpRepository.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncode.getEncryptedPassword(anyString())).thenReturn("otp-hash");
    }

    private EmailOtp otpRecord(String hash, LocalDateTime expiresAt, EmailOtp.OtpPurpose purpose) {
        return EmailOtp.builder()
                .email("u@x.com").otpHash(hash).purpose(purpose)
                .expiresAt(expiresAt).createdAt(LocalDateTime.now()).build();
    }

    // ---------- sendOtp ----------

    @Test
    void sendOtpRejectsInvalidEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> service.sendOtp("not-an-email", EmailOtp.OtpPurpose.SIGNUP));
    }

    @Test
    void sendOtpStoresHashedCodeAndEmailsUser() {
        service.sendOtp("User@X.com", EmailOtp.OtpPurpose.LOGIN);

        ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
        verify(emailOtpRepository).save(captor.capture());
        assertEquals("user@x.com", captor.getValue().getEmail()); // normalized (trim + lowercase)
        assertEquals("otp-hash", captor.getValue().getOtpHash()); // stored hashed, not raw
        assertEquals(EmailOtp.OtpPurpose.LOGIN, captor.getValue().getPurpose());
        verify(emailService).sendEmail(any(EmailSendDto.class));
    }

    @Test
    void sendOtpSucceedsEvenIfEmailFails() {
        doThrow(new RuntimeException("smtp")).when(emailService).sendEmail(any(EmailSendDto.class));
        service.sendOtp("u@x.com", null); // purpose null → defaults to SIGNUP, must not throw
        verify(emailOtpRepository).save(any(EmailOtp.class));
    }

    // ---------- verifyOtp ----------

    @Test
    void verifyOtpRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp(null, "123456"));
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp("u@x.com", null));
    }

    @Test
    void verifyOtpFailsWhenNoRecord() {
        when(emailOtpRepository.findTopByEmailOrderByCreatedAtDesc("u@x.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp("u@x.com", "123456"));
    }

    @Test
    void verifyOtpFailsWhenExpired() {
        when(emailOtpRepository.findTopByEmailOrderByCreatedAtDesc("u@x.com"))
                .thenReturn(Optional.of(otpRecord("h", LocalDateTime.now().minusMinutes(1), EmailOtp.OtpPurpose.SIGNUP)));
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp("u@x.com", "123456"));
    }

    @Test
    void verifyOtpFailsOnWrongCode() {
        when(emailOtpRepository.findTopByEmailOrderByCreatedAtDesc("u@x.com"))
                .thenReturn(Optional.of(otpRecord("h", LocalDateTime.now().plusMinutes(5), EmailOtp.OtpPurpose.SIGNUP)));
        when(passwordEncode.compare("000000", "h")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp("u@x.com", "000000"));
    }

    @Test
    void verifyOtpSuccessIssuesVerificationToken() {
        EmailOtp record = otpRecord("h", LocalDateTime.now().plusMinutes(5), EmailOtp.OtpPurpose.PASSWORD_RESET);
        when(emailOtpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc("u@x.com", EmailOtp.OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(record));
        when(passwordEncode.compare("123456", "h")).thenReturn(true);

        OtpVerifyResponseDTO res = service.verifyOtp("u@x.com", "123456", EmailOtp.OtpPurpose.PASSWORD_RESET);

        assertNotNull(res.getVerificationToken());
        assertEquals(res.getVerificationToken(), record.getVerificationToken());
        verify(emailOtpRepository).save(record);
    }

    // ---------- validateVerificationToken ----------

    @Test
    void validateTokenFailsWhenNotFound() {
        when(emailOtpRepository.findByVerificationToken("tok")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.validateVerificationToken("u@x.com", "tok"));
    }

    @Test
    void validateTokenFailsOnEmailMismatch() {
        EmailOtp record = otpRecord("h", LocalDateTime.now().plusMinutes(5), EmailOtp.OtpPurpose.PASSWORD_RESET);
        record.setVerificationToken("tok");
        when(emailOtpRepository.findByVerificationToken("tok")).thenReturn(Optional.of(record));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateVerificationToken("other@x.com", "tok"));
    }

    @Test
    void validateTokenFailsOnPurposeMismatch() {
        EmailOtp record = otpRecord("h", LocalDateTime.now().plusMinutes(5), EmailOtp.OtpPurpose.SIGNUP);
        record.setVerificationToken("tok");
        when(emailOtpRepository.findByVerificationToken("tok")).thenReturn(Optional.of(record));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateVerificationToken("u@x.com", "tok", EmailOtp.OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void validateTokenFailsWhenExpired() {
        EmailOtp record = otpRecord("h", LocalDateTime.now().minusMinutes(1), EmailOtp.OtpPurpose.PASSWORD_RESET);
        record.setVerificationToken("tok");
        when(emailOtpRepository.findByVerificationToken("tok")).thenReturn(Optional.of(record));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateVerificationToken("u@x.com", "tok", EmailOtp.OtpPurpose.PASSWORD_RESET));
    }

    @Test
    void validateTokenPassesForMatchingUnexpiredToken() {
        EmailOtp record = otpRecord("h", LocalDateTime.now().plusMinutes(5), EmailOtp.OtpPurpose.PASSWORD_RESET);
        record.setVerificationToken("tok");
        when(emailOtpRepository.findByVerificationToken("tok")).thenReturn(Optional.of(record));
        service.validateVerificationToken("u@x.com", "tok", EmailOtp.OtpPurpose.PASSWORD_RESET); // no throw
    }
}
