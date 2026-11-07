package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;

import java.util.List;

/**
 * Budget and feature decisions for event students.
 * {@link #NOOP} keeps existing wallet and rollout behavior for tests and non-event users.
 */
public interface EventAccessSupport {

    enum Gate {
        /** No active event rule. Existing wallet logic applies. */
        NONE,
        /** This call is covered by the event (time window or remaining credits). */
        COVERED,
        /**
         * Freemium event credits are already used up. The membership is reverted
         * and this call is rejected. The following call uses the freemium wallet.
         */
        CREDIT_BLOCKED
    }

    EventAccessSupport NOOP = new EventAccessSupport() {};

    default Gate gate(User user) {
        return Gate.NONE;
    }

    default void recordCoveredSpend(String userId, double billedUsd) {}

    /** Freemium event student, and this catalog feature is not on the event allow-list. */
    default boolean deniesLiveFeature(String userId, String featureCode) {
        return false;
    }

    /** Event explicitly includes the feature, or a paid plan includes the mapped module. */
    default boolean grantsHiddenFeature(String userId, String featureCode) {
        return false;
    }

    /** Replacement module list while a freemium event membership is active. Null means unchanged. */
    default List<String> eventModules(User user) {
        return null;
    }
}
