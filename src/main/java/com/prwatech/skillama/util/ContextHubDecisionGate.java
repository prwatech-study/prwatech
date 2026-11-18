package com.prwatech.skillama.util;

import java.util.regex.Pattern;

/**
 * Context Hub Decision gate for backend (dec_axybn6ity2 / prop_uwat7hn5fp).
 *
 * <p>SoT for product decisions = Context Hub Decision + context pack — not this API,
 * not LMS. Agent-originated work must cite an approved Decision ID or stay blocked at
 * {@code needs_clarification} (no silent scope invent / auto-approve).
 */
public final class ContextHubDecisionGate {

    private static final Pattern DECISION_ID = Pattern.compile("^dec_[a-z0-9]{6,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROPOSAL_ID = Pattern.compile("^prop_[a-z0-9]{6,}$", Pattern.CASE_INSENSITIVE);

    private static final Pattern[] FORBIDDEN_BACKEND_OWNERSHIP = {
            Pattern.compile("decision\\s*brain", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context\\s*hub", Pattern.CASE_INSENSITIVE),
            Pattern.compile("orchestrat", Pattern.CASE_INSENSITIVE),
            Pattern.compile("freeze\\s*(a\\s*)?decision", Pattern.CASE_INSENSITIVE),
            Pattern.compile("source\\s+of\\s+truth", Pattern.CASE_INSENSITIVE),
            Pattern.compile("context\\s*pack", Pattern.CASE_INSENSITIVE),
            Pattern.compile("approv(e|al)\\s+gate", Pattern.CASE_INSENSITIVE),
    };

    private ContextHubDecisionGate() {}

    public static boolean isValidDecisionId(String value) {
        return value != null && DECISION_ID.matcher(value.trim()).matches();
    }

    public static boolean isValidProposalId(String value) {
        return value != null && PROPOSAL_ID.matcher(value.trim()).matches();
    }

    /**
     * Reject assigning Decision / Hub orchestration ownership to backend APIs.
     */
    public static GateResult assertNotDecisionOwner(String text) {
        String blob = text == null ? "" : text;
        for (Pattern re : FORBIDDEN_BACKEND_OWNERSHIP) {
            if (re.matcher(blob).find()) {
                return GateResult.blocked(
                        "needs_clarification",
                        "Decision orchestration / SoT belongs to Context Hub (not backend). "
                                + "Do not implement Decision freezing, approval gates, or context-pack ownership here.");
            }
        }
        return GateResult.ok(null, null);
    }

    /**
     * Gate agent-generated backend tasks: require Decision ID citation.
     */
    public static GateResult gateAgentTask(String decisionId, String proposalId, String title, String summary) {
        GateResult ownership = assertNotDecisionOwner(
                (title == null ? "" : title) + " " + (summary == null ? "" : summary));
        if (!ownership.allowed()) {
            return ownership.withIds(trimToNull(decisionId), trimToNull(proposalId));
        }

        String dec = decisionId == null ? "" : decisionId.trim();
        String prop = proposalId == null ? "" : proposalId.trim();
        if (!isValidDecisionId(dec)) {
            return GateResult.blocked(
                            "needs_clarification",
                            "Agent tasks require an approved Context Hub Decision ID (dec_…) in metadata. "
                                    + "Work stays blocked until Hub freezes a Decision — backend must not invent scope.")
                    .withIds(trimToNull(dec), trimToNull(prop));
        }
        String propOut = isValidProposalId(prop) ? prop : trimToNull(prop);
        return GateResult.ok(dec, propOut);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public record GateResult(
            String status,
            boolean allowed,
            String decisionId,
            String proposalId,
            String reason
    ) {
        static GateResult ok(String decisionId, String proposalId) {
            return new GateResult("ok", true, decisionId, proposalId, null);
        }

        static GateResult blocked(String status, String reason) {
            return new GateResult(status, false, null, null, reason);
        }

        GateResult withIds(String decisionId, String proposalId) {
            return new GateResult(status, allowed, decisionId, proposalId, reason);
        }
    }
}
