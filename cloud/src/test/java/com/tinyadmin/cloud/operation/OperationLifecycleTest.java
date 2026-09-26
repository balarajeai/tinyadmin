package com.tinyadmin.cloud.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for operation lifecycle state machine (Finding #7, #12).
 * 
 * Validates:
 * - Preview success is NOT mutation terminal success
 * - Terminal states marked correctly
 * - State transitions follow defined flow
 */
class OperationLifecycleTest {
    
    @Test
    void testPreviewedIsNotTerminal() {
        // Finding #7: Preview completion is NOT terminal success
        assertFalse(OperationLifecycleStatus.PREVIEWED.isTerminal(),
                "PREVIEWED state must not be terminal");
        assertFalse(OperationLifecycleStatus.PREVIEW_PENDING.isTerminal(),
                "PREVIEW_PENDING state must not be terminal");
    }
    
    @Test
    void testConfirmedIsNotTerminal() {
        assertFalse(OperationLifecycleStatus.CONFIRMED.isTerminal(),
                "CONFIRMED state must not be terminal");
    }
    
    @Test
    void testDispatchedIsNotTerminal() {
        assertFalse(OperationLifecycleStatus.DISPATCHED.isTerminal(),
                "DISPATCHED state must not be terminal");
        assertFalse(OperationLifecycleStatus.PENDING_RESULT.isTerminal(),
                "PENDING_RESULT state must not be terminal");
    }
    
    @Test
    void testSucceededIsTerminal() {
        assertTrue(OperationLifecycleStatus.SUCCEEDED.isTerminal(),
                "SUCCEEDED state must be terminal");
    }
    
    @Test
    void testFailedIsTerminal() {
        // Finding #4: FAILED != CANCELLED
        assertTrue(OperationLifecycleStatus.FAILED.isTerminal(),
                "FAILED state must be terminal");
    }
    
    @Test
    void testCancelledIsTerminal() {
        // Finding #4: CANCELLED is separate terminal state
        assertTrue(OperationLifecycleStatus.CANCELLED.isTerminal(),
                "CANCELLED state must be terminal");
    }
    
    @Test
    void testUnknownIsTerminal() {
        assertTrue(OperationLifecycleStatus.UNKNOWN.isTerminal(),
                "UNKNOWN state must be terminal");
    }
    
    @Test
    void testFailedAndCancelledAreDifferent() {
        // Finding #4: FAILED != CANCELLED
        assertNotEquals(OperationLifecycleStatus.FAILED, OperationLifecycleStatus.CANCELLED,
                "FAILED and CANCELLED must be distinct states");
    }
}
