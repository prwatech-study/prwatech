package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.TimeWalletAdjustRequestDTO;
import com.prwatech.skillama.dto.TimeWalletDTO;
import com.prwatech.skillama.exception.TimeBudgetLimitException;
import com.prwatech.skillama.model.TimeWalletAdjustmentEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.TimeWalletAdjustmentEventRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeWalletServiceTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private TimeWalletAdjustmentEventRepository adjustmentEventRepository;
    @Mock private com.prwatech.skillama.repository.TimeConsumptionEventRepository consumptionEventRepository;

    private TimeWalletService service;

    @BeforeEach
    void setUp() {
        service = new TimeWalletService(userRepository, adjustmentEventRepository, consumptionEventRepository);
    }

    private User timeUser(Double allocated, Double consumed) {
        return User.builder().id("u1").email("u@x.com").role(User.UserRole.USER)
                .timeAllocatedMinutes(allocated)
                .timeConsumedMinutes(consumed)
                .build();
    }

    // ---------- activation ----------

    @Test
    void walletInactiveForNullZeroOrNegativeAllocation() {
        assertFalse(service.isTimeWalletActive(timeUser(null, 0.0)));
        assertFalse(service.isTimeWalletActive(timeUser(0.0, 0.0)));
        assertFalse(service.isTimeWalletActive(timeUser(-10.0, 0.0)));
        assertFalse(service.isTimeWalletActive(null));
    }

    @Test
    void walletActiveForPositiveAllocation() {
        assertTrue(service.isTimeWalletActive(timeUser(2400.0, 0.0)));
    }

    // ---------- budget assertion ----------

    @Test
    void withinBudgetWhenTimeRemains() {
        assertDoesNotThrow(() -> service.assertWithinTimeBudget(timeUser(2400.0, 2399.9)));
        assertTrue(service.hasRemainingTime(timeUser(2400.0, 2399.9)));
    }

    @Test
    void blocksAtExactlyTheLimit() {
        User user = timeUser(2400.0, 2400.0);
        assertFalse(service.hasRemainingTime(user));
        TimeBudgetLimitException e =
                assertThrows(TimeBudgetLimitException.class, () -> service.assertWithinTimeBudget(user));
        assertEquals(2400.0, e.getTimeUsedMinutes());
        assertEquals(2400.0, e.getTimeLimitMinutes());
    }

    @Test
    void nonTimeUsersAreNeverBlocked() {
        assertDoesNotThrow(() -> service.assertWithinTimeBudget(timeUser(null, 999999.0)));
    }

    @Test
    void nullConsumedTreatedAsZero() {
        assertTrue(service.hasRemainingTime(timeUser(60.0, null)));
    }

    // ---------- consumption ----------

    @Test
    void consumeAccumulatesMinutesFromSeconds() {
        User user = timeUser(2400.0, 10.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        service.consumeTimeSeconds("u1", 90); // 1.5 minutes

        assertEquals(11.5, user.getTimeConsumedMinutes());
        verify(userRepository).save(user);
    }

    @Test
    void consumeIsNoOpForNonTimeUsers() {
        User user = timeUser(null, 0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        service.consumeTimeSeconds("u1", 600);

        verify(userRepository, never()).save(any());
    }

    @Test
    void consumeIgnoresNonPositiveSecondsAndNullUser() {
        service.consumeTimeSeconds("u1", 0);
        service.consumeTimeSeconds("u1", -5);
        service.consumeTimeSeconds(null, 60);
        verify(userRepository, never()).save(any());
    }

    // ---------- active-time heartbeat ----------

    @Test
    void heartbeatChargesClaimedSecondsOnFirstBeat() {
        User user = timeUser(2400.0, 0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        TimeWalletDTO result = service.consumeActiveTime("u1", 30, "ai_tutor");

        assertEquals(0.5, result.getConsumedMinutes());
        verify(consumptionEventRepository).save(any());
    }

    @Test
    void heartbeatClampsOversizedClaims() {
        User user = timeUser(2400.0, 0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        TimeWalletDTO result = service.consumeActiveTime("u1", 100000, "ai_tutor");

        // First beat has no elapsed anchor — clamped to MAX_BEAT_SECONDS (90s = 1.5m).
        assertEquals(1.5, result.getConsumedMinutes());
    }

    @Test
    void heartbeatCappedByElapsedWallClockSinceLastBeat() {
        // Last accepted beat 10 seconds ago — a 30s claim (e.g. a second tab
        // beating in parallel) may charge at most 10s.
        User user = timeUser(2400.0, 0.0);
        user.setLastTimeConsumeAt(IndiaTime.now().minusSeconds(10));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        TimeWalletDTO result = service.consumeActiveTime("u1", 30, "ai_tutor");

        assertTrue(result.getConsumedMinutes() <= 10 / 60.0 + 0.02,
                "charge must be capped by elapsed seconds, was " + result.getConsumedMinutes());
    }

    @Test
    void heartbeatNoOpForNonTimeUsersAndNonPositiveClaims() {
        User creditUser = timeUser(null, 0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(creditUser));
        TimeWalletDTO inactive = service.consumeActiveTime("u1", 30, "ai_tutor");
        assertFalse(inactive.isActive());

        User timeUser = timeUser(2400.0, 5.0);
        when(userRepository.findById("u2")).thenReturn(Optional.of(timeUser));
        TimeWalletDTO unchanged = service.consumeActiveTime("u2", 0, "ai_tutor");
        assertEquals(5.0, unchanged.getConsumedMinutes());
        verify(consumptionEventRepository, never()).save(any());
    }

    // ---------- admin adjust ----------

    @Test
    void adjustRequiresNonZeroDeltaAndReason() {
        TimeWalletAdjustRequestDTO noDelta = new TimeWalletAdjustRequestDTO();
        noDelta.setReason("r");
        assertThrows(IllegalArgumentException.class, () -> service.adjust("u1", noDelta, "admin"));

        TimeWalletAdjustRequestDTO zeroDelta = new TimeWalletAdjustRequestDTO();
        zeroDelta.setDeltaMinutes(0.0);
        zeroDelta.setReason("r");
        assertThrows(IllegalArgumentException.class, () -> service.adjust("u1", zeroDelta, "admin"));

        TimeWalletAdjustRequestDTO blankReason = new TimeWalletAdjustRequestDTO();
        blankReason.setDeltaMinutes(60.0);
        blankReason.setReason("  ");
        assertThrows(IllegalArgumentException.class, () -> service.adjust("u1", blankReason, "admin"));
    }

    @Test
    void adjustAddsMinutesAndWritesAuditEvent() {
        User user = timeUser(120.0, 30.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        TimeWalletAdjustRequestDTO request = new TimeWalletAdjustRequestDTO();
        request.setDeltaMinutes(2400.0);
        request.setReason("B2B top-up");

        TimeWalletDTO result = service.adjust("u1", request, "admin1");

        assertEquals(2520.0, result.getAllocatedMinutes());
        assertEquals(30.0, result.getConsumedMinutes());
        assertEquals(2490.0, result.getRemainingMinutes());

        ArgumentCaptor<TimeWalletAdjustmentEvent> captor =
                ArgumentCaptor.forClass(TimeWalletAdjustmentEvent.class);
        verify(adjustmentEventRepository).save(captor.capture());
        TimeWalletAdjustmentEvent event = captor.getValue();
        assertEquals("u1", event.getUserId());
        assertEquals("admin1", event.getAdminId());
        assertEquals(2400.0, event.getDeltaMinutes());
        assertEquals(120.0, event.getAllocatedBeforeMinutes());
        assertEquals(2520.0, event.getAllocatedAfterMinutes());
        assertEquals("B2B top-up", event.getReason());
    }

    @Test
    void adjustFloorsAllocationAtZero() {
        User user = timeUser(60.0, 10.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        TimeWalletAdjustRequestDTO request = new TimeWalletAdjustRequestDTO();
        request.setDeltaMinutes(-500.0);
        request.setReason("revoke");

        TimeWalletDTO result = service.adjust("u1", request, "admin1");

        assertEquals(0.0, result.getAllocatedMinutes());
        assertFalse(result.isActive());
    }
}
