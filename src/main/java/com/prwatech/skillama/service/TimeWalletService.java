package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.TimeWalletAdjustRequestDTO;
import com.prwatech.skillama.dto.TimeWalletDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.TimeBudgetLimitException;
import com.prwatech.skillama.model.TimeConsumptionEvent;
import com.prwatech.skillama.model.TimeWalletAdjustmentEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.TimeConsumptionEventRepository;
import com.prwatech.skillama.repository.TimeWalletAdjustmentEventRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Time-based (B2B seat) wallet: a user granted N learning minutes is gated by TIME
 * instead of the USD credit wallet. This service is the single enforcement point —
 * AiUsageService.assertWithinBudget delegates here for AI calls, and
 * UserCourseServiceImpl.updateProgress consumes minutes as watch time is recorded.
 * UI checks are cosmetic only; the invariant lives here.
 */
@Service
@RequiredArgsConstructor
public class TimeWalletService {

    private final SkillamaUserRepository userRepository;
    private final TimeWalletAdjustmentEventRepository adjustmentEventRepository;
    private final TimeConsumptionEventRepository consumptionEventRepository;

    /** A single heartbeat may never charge more than this (client beats every ~30s). */
    private static final int MAX_BEAT_SECONDS = 90;

    /** Time-based seat = a positive minute allocation exists. */
    public boolean isTimeWalletActive(User user) {
        return user != null
                && user.getTimeAllocatedMinutes() != null
                && user.getTimeAllocatedMinutes() > 0;
    }

    /** True when a time-based user still has minutes left (false for non-time users too). */
    public boolean hasRemainingTime(User user) {
        return isTimeWalletActive(user)
                && consumedMinutes(user) < user.getTimeAllocatedMinutes();
    }

    /** Throws TimeBudgetLimitException when a time-based user has no minutes left. */
    public void assertWithinTimeBudget(User user) {
        if (!isTimeWalletActive(user)) {
            return;
        }
        double allocated = user.getTimeAllocatedMinutes();
        double consumed = consumedMinutes(user);
        if (consumed >= allocated) {
            throw new TimeBudgetLimitException(
                    "Learning time limit reached — ask your administrator for more hours",
                    round(consumed), round(allocated));
        }
    }

    public void assertWithinTimeBudget(String userId) {
        userRepository.findById(userId).ifPresent(this::assertWithinTimeBudget);
    }

    /**
     * Records consumed learning time for time-based users; no-op otherwise.
     * Deliberately does NOT throw when the balance dips below zero mid-session —
     * the block lands on the next assertWithinTimeBudget call.
     */
    public void consumeTimeSeconds(String userId, int seconds) {
        if (userId == null || seconds <= 0) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (!isTimeWalletActive(user)) {
            return;
        }
        user.setTimeConsumedMinutes(round(consumedMinutes(user) + seconds / 60.0));
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
    }

    public TimeWalletDTO getStatus(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDto(user);
    }

    /**
     * Accepts an active-time heartbeat from a paid feature surface and returns the
     * (possibly updated) wallet status. Charging rules, all server-side:
     *   - no-op for non-time-based users;
     *   - a beat is clamped to MAX_BEAT_SECONDS;
     *   - a beat can never charge more than the wall-clock elapsed since the LAST
     *     accepted beat — so two open tabs beating in parallel bill once, and a
     *     replayed/forged beat cannot inflate consumption.
     */
    public TimeWalletDTO consumeActiveTime(String userId, Integer claimedSeconds, String module) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!isTimeWalletActive(user)) {
            return toDto(user);
        }
        int claimed = claimedSeconds != null ? claimedSeconds : 0;
        if (claimed <= 0) {
            return toDto(user);
        }
        LocalDateTime now = IndiaTime.now();
        int charge = Math.min(claimed, MAX_BEAT_SECONDS);
        if (user.getLastTimeConsumeAt() != null) {
            long elapsed = Duration.between(user.getLastTimeConsumeAt(), now).getSeconds();
            charge = (int) Math.max(0, Math.min(charge, elapsed));
        }
        user.setLastTimeConsumeAt(now);
        if (charge > 0) {
            user.setTimeConsumedMinutes(round(consumedMinutes(user) + charge / 60.0));
            TimeConsumptionEvent event = new TimeConsumptionEvent();
            event.setUserId(userId);
            event.setModule(module != null && !module.isBlank() ? module.trim() : "unknown");
            event.setSeconds(charge);
            consumptionEventRepository.save(event);
        }
        user.setUpdatedAt(now);
        userRepository.save(user);
        return toDto(user);
    }

    /** Admin allocation change; requires a reason and writes an audit event. */
    public TimeWalletDTO adjust(String userId, TimeWalletAdjustRequestDTO request, String adminId) {
        if (request == null || request.getDeltaMinutes() == null || request.getDeltaMinutes() == 0) {
            throw new IllegalArgumentException("deltaMinutes is required and must be non-zero");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        double before = user.getTimeAllocatedMinutes() != null ? user.getTimeAllocatedMinutes() : 0.0;
        double after = Math.max(0.0, round(before + request.getDeltaMinutes()));
        user.setTimeAllocatedMinutes(after);
        if (user.getTimeConsumedMinutes() == null) {
            user.setTimeConsumedMinutes(0.0);
        }
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);

        TimeWalletAdjustmentEvent event = new TimeWalletAdjustmentEvent();
        event.setUserId(userId);
        event.setAdminId(adminId);
        event.setDeltaMinutes(request.getDeltaMinutes());
        event.setAllocatedBeforeMinutes(before);
        event.setAllocatedAfterMinutes(after);
        event.setReason(request.getReason().trim());
        adjustmentEventRepository.save(event);

        return toDto(user);
    }

    private TimeWalletDTO toDto(User user) {
        boolean active = isTimeWalletActive(user);
        double allocated = active ? user.getTimeAllocatedMinutes() : 0.0;
        double consumed = consumedMinutes(user);
        return TimeWalletDTO.builder()
                .active(active)
                .allocatedMinutes(round(allocated))
                .consumedMinutes(round(consumed))
                .remainingMinutes(round(Math.max(0.0, allocated - consumed)))
                .build();
    }

    private double consumedMinutes(User user) {
        return user != null && user.getTimeConsumedMinutes() != null ? user.getTimeConsumedMinutes() : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
