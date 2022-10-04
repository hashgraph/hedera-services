package com.hedera.services.state.tasks;

import com.hedera.services.state.merkle.MerkleNetworkContext;

import java.time.Instant;

/**
 * Minimal interface for a system task that needs to perform deterministic work
 * on (possibly) every entity in state.
 */
public interface SystemTask {
    /**
     * Whether this task is still active.
     *
     * @return if the task manager still needs to include this task in processing
     */
    boolean isActive();

    /**
     * Attempts to do this task's work on the entity with the given id, if applicable
     * and capacity and context permit.
     *
     * @param literalNum the id of the entity to process
     * @param now the current consensus time
     * @param curNetworkCtx the current network context, if useful
     * @return the result of the task's work
     */
    default SystemTaskResult process(long literalNum, Instant now, MerkleNetworkContext curNetworkCtx) {
        return process(literalNum, now);
    }

    /**
     * Attempts to do this task's work on the entity with the given id, if applicable
     * and capacity and context permit.
     *
     * @param literalNum the id of the entity to process
     * @param now the current consensus time
     * @return the result of the task's work
     */
    SystemTaskResult process(long literalNum, Instant now);
}
