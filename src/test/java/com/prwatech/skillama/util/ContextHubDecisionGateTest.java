package com.prwatech.skillama.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextHubDecisionGateTest {

    @Test
    void acceptsValidDecisionIds() {
        assertTrue(ContextHubDecisionGate.isValidDecisionId("dec_axybn6ity2"));
        assertTrue(ContextHubDecisionGate.isValidDecisionId("DEC_ABC123"));
        assertFalse(ContextHubDecisionGate.isValidDecisionId("dec_x"));
        assertFalse(ContextHubDecisionGate.isValidDecisionId(""));
    }

    @Test
    void blocksBackendClaimingDecisionOwnership() {
        var blocked = ContextHubDecisionGate.assertNotDecisionOwner(
                "Implement Decision brain freeze Decision pack in API");
        assertFalse(blocked.allowed());
        assertTrue(blocked.reason().contains("Context Hub"));

        var ok = ContextHubDecisionGate.assertNotDecisionOwner("Add enrollment status endpoint");
        assertTrue(ok.allowed());
    }

    @Test
    void gatesAgentTasksWithoutDecisionId() {
        var blocked = ContextHubDecisionGate.gateAgentTask(null, null, "Ship filter API", null);
        assertEquals("needs_clarification", blocked.status());
        assertFalse(blocked.allowed());

        var ok = ContextHubDecisionGate.gateAgentTask(
                "dec_axybn6ity2", "prop_uwat7hn5fp", "Ship filter API", "LMS support");
        assertEquals("ok", ok.status());
        assertTrue(ok.allowed());
        assertEquals("dec_axybn6ity2", ok.decisionId());
    }

    @Test
    void blocksOrchestrationWordingEvenWithDecisionId() {
        var blocked = ContextHubDecisionGate.gateAgentTask(
                "dec_axybn6ity2",
                "prop_uwat7hn5fp",
                "Own Context Hub source of truth in backend",
                null);
        assertFalse(blocked.allowed());
        assertEquals("needs_clarification", blocked.status());
    }
}
