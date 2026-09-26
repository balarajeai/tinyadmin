package com.tinyadmin.cloud.operation;

/**
 * Operation lifecycle status (Finding #7 - Operation Lifecycle).
 * 
 * CRITICAL: Preview success is NOT mutation terminal success.
 * 
 * State flow:
 * - PREVIEW_PENDING → PREVIEWED (preview completed, not mutation success)
 * - PREVIEWED → CONFIRMED (confirmation received)
 * - CONFIRMED → DISPATCHED (command sent to Agent)
 * - DISPATCHED → PENDING_RESULT (awaiting Agent result)
 * - PENDING_RESULT → SUCCEEDED/FAILED/UNKNOWN (Agent terminal result)
 * 
 * Terminal states: SUCCEEDED, FAILED, UNKNOWN
 * Idempotent: Duplicate terminal results safe; conflicting terminal rejected
 */
public enum OperationLifecycleStatus {
    /** Preview operation pending */
    PREVIEW_PENDING,
    
    /** Preview completed successfully (NOT mutation success) */
    PREVIEWED,
    
    /** Operation confirmed by operator */
    CONFIRMED,
    
    /** Command dispatched to Agent */
    DISPATCHED,
    
    /** Awaiting Agent result */
    PENDING_RESULT,
    
    /** Mutation succeeded (TERMINAL - from Agent) */
    SUCCEEDED,
    
    /** Mutation failed definitively (TERMINAL - from Agent) */
    FAILED,
    
    /** Mutation outcome indeterminate (TERMINAL - requires reconciliation) */
    UNKNOWN,
    
    /** Operation cancelled before execution */
    CANCELLED;
    
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == UNKNOWN || this == CANCELLED;
    }
}
