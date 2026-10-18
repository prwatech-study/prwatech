package com.prwatech.skillama.billing;

import com.prwatech.common.configuration.AppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentProviderTest {

    /** Matches Razorpay docs sample HMAC for order_IEIa…|pay_IH4N… */
    private static final String SECRET = "EnLs21M47BllR3X8PSFtjtbd";
    private static final String ORDER_ID = "order_IEIaMR65cu6nz3";
    private static final String PAYMENT_ID = "pay_IH4NVgf4Dreq1l";
    private static final String VALID_SIGNATURE =
            "0d4e745a1838664ad6c9c9902212a32d627d68e917290b0ad5f08ff4561bc50f";

    @Mock
    private AppContext appContext;

    private RazorpayPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RazorpayPaymentProvider(appContext);
    }

    @Test
    void providerNameIsRazorpay() {
        assertEquals("RAZORPAY", provider.providerName());
    }

    @Test
    void verifyAcceptsValidSignature() {
        when(appContext.getRazorpaySecret()).thenReturn(SECRET);
        PaymentProvider.PaymentVerification v =
                provider.verifyAndCapture(ORDER_ID, PAYMENT_ID, VALID_SIGNATURE);

        assertTrue(v.success());
        assertEquals(ORDER_ID, v.orderId());
        assertEquals(PAYMENT_ID, v.paymentId());
        assertEquals(VALID_SIGNATURE, v.signature());
    }

    @Test
    void verifyRejectsTamperedSignature() {
        when(appContext.getRazorpaySecret()).thenReturn(SECRET);
        PaymentProvider.PaymentVerification v =
                provider.verifyAndCapture(ORDER_ID, PAYMENT_ID, "deadbeef");

        assertFalse(v.success());
        assertTrue(v.failureReason().toLowerCase().contains("signature"));
    }

    @Test
    void verifyRejectsMissingFields() {
        PaymentProvider.PaymentVerification v =
                provider.verifyAndCapture(ORDER_ID, null, VALID_SIGNATURE);

        assertFalse(v.success());
        assertTrue(v.failureReason().toLowerCase().contains("missing"));
    }

    @Test
    void buildReceiptRespectsMaxLength() {
        String receipt = RazorpayPaymentProvider.buildReceipt(
                "very-long-user-id-that-exceeds-limits-aaaaaaaa", "QUANTUM");
        assertTrue(receipt.length() <= 40);
    }
}
