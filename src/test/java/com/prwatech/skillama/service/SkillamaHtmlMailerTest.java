package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowCreditNoticeTest {

    @Test
    void notifiesOnceAtThreeQuartersAndAgainAfterALargerWallet() {
        assertFalse(LowCreditNotice.shouldNotify(0.7, 1.0, null));
        assertTrue(LowCreditNotice.shouldNotify(0.75, 1.0, null));
        assertFalse(LowCreditNotice.shouldNotify(0.9, 1.0, 1.0));
        assertTrue(LowCreditNotice.shouldNotify(1.6, 2.0, 1.0));
    }

    @Test
    void formatsWholeCreditsWithoutADecimal() {
        assertEquals("7", LowCreditNotice.formatCredits(7.0));
        assertEquals("7.4", LowCreditNotice.formatCredits(7.36));
    }
}
