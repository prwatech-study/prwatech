package com.prwatech.skillama.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Explicit Skillama permitAll routes (method + path). A newly added controller method
 * is authenticated or denied until it is added here.
 *
 * <p>Do not use {@code /skillama/.../**} public prefixes.
 */
public final class SkillamaAnonymousRequestMatchers {

    public record Route(HttpMethod method, String pattern) {
        RequestMatcher matcher() {
            return new AntPathRequestMatcher(pattern, method.name());
        }
    }

    private static final List<Route> ROUTES = List.of(
            // users — public auth
            new Route(HttpMethod.POST, "/skillama/users/register"),
            new Route(HttpMethod.POST, "/skillama/users/login"),
            new Route(HttpMethod.POST, "/skillama/users/demo-login/otp/send"),
            new Route(HttpMethod.POST, "/skillama/users/demo-login"),
            new Route(HttpMethod.POST, "/skillama/users/otp/email/send"),
            new Route(HttpMethod.POST, "/skillama/users/otp/email/verify"),
            new Route(HttpMethod.POST, "/skillama/users/contact/check-availability"),
            new Route(HttpMethod.GET, "/skillama/users/register/freemium/courses"),
            new Route(HttpMethod.POST, "/skillama/users/register/freemium"),
            new Route(HttpMethod.POST, "/skillama/users/auth/google"),
            new Route(HttpMethod.POST, "/skillama/users/auth/apple"),
            new Route(HttpMethod.POST, "/skillama/users/auth/email/continue"),
            new Route(HttpMethod.POST, "/skillama/users/auth/otp/continue"),
            new Route(HttpMethod.POST, "/skillama/users/login/otp"),
            new Route(HttpMethod.POST, "/skillama/users/migrate/freemium/otp/send"),
            new Route(HttpMethod.POST, "/skillama/users/migrate/freemium/confirm"),
            new Route(HttpMethod.POST, "/skillama/users/forgot-password"),
            new Route(HttpMethod.POST, "/skillama/users/reset-password"),
            // org login
            new Route(HttpMethod.POST, "/skillama/api/org/auth/login"),
            new Route(HttpMethod.POST, "/skillama/api/org/auth/google"),
            new Route(HttpMethod.POST, "/skillama/api/org/auth/microsoft"),
            // platform public GETs
            new Route(HttpMethod.GET, "/skillama/platform/demo-video"),
            new Route(HttpMethod.GET, "/skillama/platform/upgrade-contact"),
            new Route(HttpMethod.GET, "/skillama/platform/freemium-offering"),
            new Route(HttpMethod.GET, "/skillama/platform/ai-settings"),
            new Route(HttpMethod.GET, "/skillama/platform/theme-settings"),
            new Route(HttpMethod.GET, "/skillama/platform/public-stats"),
            new Route(HttpMethod.GET, "/skillama/platform/referral-share"),
            new Route(HttpMethod.GET, "/skillama/platform/org/*/branding"),
            new Route(HttpMethod.GET, "/skillama/platform/org/discover"),
            new Route(HttpMethod.GET, "/skillama/platform/org/resolve"),
            // marketing
            new Route(HttpMethod.POST, "/skillama/leads/sales-interest"),
            new Route(HttpMethod.POST, "/skillama/issues/report"),
            new Route(HttpMethod.POST, "/skillama/issues/attachments"),
            new Route(HttpMethod.POST, "/skillama/public/theme-preference"),
            new Route(HttpMethod.GET, "/skillama/billing/plans"),
            new Route(HttpMethod.GET, "/skillama/review"),
            // courses public / guest catalog
            new Route(HttpMethod.GET, "/skillama/courses"),
            new Route(HttpMethod.GET, "/skillama/courses/guest"),
            new Route(HttpMethod.GET, "/skillama/courses/guest/curriculum"),
            new Route(HttpMethod.GET, "/skillama/courses/demo"),
            new Route(HttpMethod.GET, "/skillama/courses/demo/curriculum"),
            new Route(HttpMethod.GET, "/skillama/courses/public"),
            new Route(HttpMethod.GET, "/skillama/courses/public/first"),
            new Route(HttpMethod.GET, "/skillama/courses/*/share"),
            new Route(HttpMethod.GET, "/skillama/courses/*/curriculum"),
            // guest user-profile (cookie or Bearer in the controller)
            new Route(HttpMethod.POST, "/skillama/user-profile/guest/init"),
            new Route(HttpMethod.GET, "/skillama/user-profile/access-control"),
            new Route(HttpMethod.GET, "/skillama/user-profile/lectures/*/access"),
            new Route(HttpMethod.POST, "/skillama/user-profile/lectures/complete"),
            new Route(HttpMethod.POST, "/skillama/user-profile/lectures/progress"),
            new Route(HttpMethod.POST, "/skillama/user-profile/module-quiz/sessions"),
            new Route(HttpMethod.POST, "/skillama/user-profile/module-quiz/attempts"),
            new Route(HttpMethod.POST, "/skillama/user-profile/module-quiz/skip"),
            new Route(HttpMethod.GET, "/skillama/user-profile/module-quiz/attempts"),
            new Route(HttpMethod.GET, "/skillama/user-profile/chat/history"),
            new Route(HttpMethod.GET, "/skillama/user-profile/chat/history/*/audio"),
            new Route(HttpMethod.POST, "/skillama/user-profile/chat/ask"),
            new Route(HttpMethod.POST, "/skillama/user-profile/chat/track"),
            // AI utility — optional learner JWT
            new Route(HttpMethod.POST, "/skillama/ai-utility/introduce-tutor"),
            new Route(HttpMethod.POST, "/skillama/ai-utility/lecture-start-instruction"),
            new Route(HttpMethod.POST, "/skillama/ai-utility/text-to-audio"),
            new Route(HttpMethod.POST, "/skillama/ai-utility/audio-to-text"),
            new Route(HttpMethod.POST, "/skillama/ai-utility/confirmation"),
            new Route(HttpMethod.POST, "/skillama/ai-utility/get-user-name"),
            // internal service key (controller validates X-AI-Usage-Key)
            new Route(HttpMethod.POST, "/skillama/internal/ai-usage/record"),
            new Route(HttpMethod.GET, "/skillama/internal/ai-usage/budget-check")
    );

    private static final List<RequestMatcher> MATCHERS =
            ROUTES.stream().map(Route::matcher).toList();

    private SkillamaAnonymousRequestMatchers() {
    }

    public static List<Route> routes() {
        return ROUTES;
    }

    public static List<RequestMatcher> matchers() {
        return MATCHERS;
    }

    public static boolean isAnonymousSkillama(HttpServletRequest request) {
        for (RequestMatcher matcher : MATCHERS) {
            if (matcher.matches(request)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSkillamaApi(HttpServletRequest request) {
        String path = path(request);
        return path.startsWith("/skillama/");
    }

    public static boolean isSwagger(HttpServletRequest request) {
        String path = path(request);
        return path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/swagger-resources/")
                || path.equals("/v2/api-docs")
                || path.startsWith("/webjars/");
    }

    static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }
}
