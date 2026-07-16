package com.prwatech.skillama.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider = new MockPaymentProvider();

    @Test
    void providerNameIsMock() {
        assertEquals("MOCK", provider.providerName());
    }

    @Test
    void createOrderReturnsMockOrderInInr() {
        PaymentProvider.PaymentOrder order =
                provider.createOrder("u1", "PULSE", BigDecimal.valueOf(999));

        assertNotNull(order.orderId());
        assertTrue(order.orderId().startsWith("mock_order_"));
        assertEquals("MOCK", order.provider());
        assertEquals(BigDecimal.valueOf(999), order.amountInr());
        assertEquals("INR", order.currency());
    }

    @Test
    void createOrderGeneratesUniqueOrderIds() {
        String a = provider.createOrder("u1", "PULSE", BigDecimal.TEN).orderId();
        String b = provider.createOrder("u1", "PULSE", BigDecimal.TEN).orderId();
        assertNotEquals(a, b);
    }

    @Test
    void verifyAlwaysSucceedsAndSynthesisesIdsWhenMissing() {
        PaymentProvider.PaymentVerification v =
                provider.verifyAndCapture("mock_order_x", null, null);

        assertTrue(v.success());
        assertEquals("mock_order_x", v.orderId());
        assertTrue(v.paymentId().startsWith("mock_pay_"));
        assertTrue(v.signature().startsWith("mock_sig_"));
        assertNull(v.failureReason());
    }

    @Test
    void verifyPreservesSuppliedPaymentIdAndSignature() {
        PaymentProvider.PaymentVerification v =
                provider.verifyAndCapture("order1", "pay_real", "sig_real");

        assertTrue(v.success());
        assertEquals("pay_real", v.paymentId());
        assertEquals("sig_real", v.signature());
    }

    @Test
    void verifyBlankPaymentIdIsTreatedAsMissing() {
        PaymentProvider.PaymentVerification v =
                provider.verifyAndCapture("order1", "  ", "");
        assertTrue(v.paymentId().startsWith("mock_pay_"));
        assertTrue(v.signature().startsWith("mock_sig_"));
    }
}
