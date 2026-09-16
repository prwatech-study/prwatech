package com.prwatech.skillama.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillamaAnonymousRequestMatchersTest {

    @Test
    void publicAndGuestAndInternalRoutesAreAnonymous() {
        assertTrue(anon("POST", "/skillama/users/login"));
        assertTrue(anon("POST", "/skillama/users/register"));
        assertTrue(anon("POST", "/skillama/users/auth/google"));
        assertTrue(anon("GET", "/skillama/platform/ai-settings"));
        assertTrue(anon("GET", "/skillama/platform/org/acme/branding"));
        assertTrue(anon("POST", "/skillama/leads/sales-interest"));
        assertTrue(anon("POST", "/skillama/issues/report"));
        assertTrue(anon("GET", "/skillama/courses"));
        assertTrue(anon("GET", "/skillama/courses/guest"));
        assertTrue(anon("GET", "/skillama/courses/abc/share"));
        assertTrue(anon("GET", "/skillama/courses/abc/curriculum"));
        assertTrue(anon("POST", "/skillama/user-profile/guest/init"));
        assertTrue(anon("GET", "/skillama/user-profile/access-control"));
        assertTrue(anon("POST", "/skillama/ai-utility/text-to-audio"));
        assertTrue(anon("POST", "/skillama/internal/ai-usage/record"));
        assertTrue(anon("GET", "/skillama/internal/ai-usage/budget-check"));
        assertTrue(anon("GET", "/skillama/billing/plans"));
        assertTrue(anon("GET", "/skillama/review"));
    }

    @Test
    void protectedSkillamaRoutesAreNotAnonymous() {
        assertFalse(anon("GET", "/skillama/users/session"));
        assertFalse(anon("POST", "/skillama/courses"));
        assertFalse(anon("PUT", "/skillama/courses/abc"));
        assertFalse(anon("GET", "/skillama/curriculum/mod-1"));
        assertFalse(anon("POST", "/skillama/users/admin/migrate-passwords"));
        assertFalse(anon("POST", "/skillama/user-profile/guest/migrate"));
        assertFalse(anon("GET", "/skillama/billing/subscription"));
        assertFalse(anon("POST", "/skillama/review"));
        assertFalse(anon("GET", "/skillama/review/mine"));
        assertFalse(anon("GET", "/skillama/api/admin/check-access"));
        assertFalse(anon("POST", "/skillama/api/org/users"));
        assertFalse(anon("PUT", "/skillama/platform/ai-settings"));
    }

    @Test
    void skillamaPublicPatternsDoNotUseRecursiveWildcards() {
        for (SkillamaAnonymousRequestMatchers.Route route : SkillamaAnonymousRequestMatchers.routes()) {
            assertFalse(route.pattern().contains("/**"),
                    () -> "public matcher must not use /** : " + route.method() + " " + route.pattern());
        }
    }

    private static boolean anon(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setServletPath(path);
        return SkillamaAnonymousRequestMatchers.isAnonymousSkillama(request);
    }
}
