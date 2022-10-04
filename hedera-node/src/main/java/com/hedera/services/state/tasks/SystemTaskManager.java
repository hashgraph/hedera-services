package com.hedera.services.state.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.merkle.MerkleNetworkContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;

import static com.hedera.services.state.merkle.MerkleNetworkContext.*;
import static com.hedera.services.state.tasks.SystemTaskResult.*;

@Singleton
public class SystemTaskManager {
    private final SystemTask[] tasks;

    @Inject
    public SystemTaskManager(final Map<String, SystemTask> namedTasks) {
        // Only runs once per restart, so performance doesn't matter
        tasks = namedTasks.keySet().stream().sorted().map(namedTasks::get).toArray(SystemTask[]::new);
    }

    /**
     * Performs as many pending system tasks as possible for the given entity number and
     * consensus time. These tasks will begin from {@link MerkleNetworkContext#nextTaskTodo()}
     * and continue until either the manager receives {@link SystemTaskResult#NO_CAPACITY_LEFT}
     * or {@link SystemTaskResult#NEEDS_DIFFERENT_CONTEXT}; or until the end of the {@code tasks}
     * list is reached.
     * <p>
     * More precisely, this method will return:
     * <ul>
     *     <li>{@link SystemTaskResult#NOTHING_TO_DO} iff all tasks returned {@code NOTHING_TO_DO}.</li>
     *     <li>{@link SystemTaskResult#DONE} iff at least one task returned {@code DONE} and all
     *     other tasks returned {@code NOTHING_TO_DO}.</li>
     *     <li>{@link SystemTaskResult#NO_CAPACITY_LEFT} immediately when any task reports it.</li>
     *     <li>{@link SystemTaskResult#NEEDS_DIFFERENT_CONTEXT} immediately when any task reports it.</li>
     * </ul>
     *
     * @param literalNum    the id to process
     * @param now           the consensus time now
     * @param curNetworkCtx the current network context
     * @return the cumulative result of attempting all pending tasks
     */
    public SystemTaskResult process(
            final long literalNum,
            final Instant now,
            final MerkleNetworkContext curNetworkCtx) {
        boolean didSomething = false;
        for (var i = curNetworkCtx.nextTaskTodo(); i < tasks.length; i++) {
            if (!tasks[i].isActive()) {
                continue;
            }
            final var result = tasks[i].process(literalNum, now);
            if (result == NOTHING_TO_DO || result == DONE) {
                didSomething |= (result == DONE);
            } else if (result == NEEDS_DIFFERENT_CONTEXT || result == NO_CAPACITY_LEFT) {
                curNetworkCtx.setNextTaskTodo(i);
                return result;
            }
        }
        curNetworkCtx.setNextTaskTodo(0);
        updatePreExistingScanStatus(literalNum, curNetworkCtx);
        return didSomething ? DONE : NOTHING_TO_DO;
    }

    @VisibleForTesting
    void updatePreExistingScanStatus(final long n, final MerkleNetworkContext curNetworkCtx) {
        final var status = curNetworkCtx.getPreExistingEntityScanStatus();
        if (status == LAST_PRE_EXISTING_ENTITY_NOT_SCANNED) {
            if (n == curNetworkCtx.seqNoPostUpgrade() - 1) {
                curNetworkCtx.setPreExistingEntityScanStatus(LAST_PRE_EXISTING_ENTITY_SCANNED);
            }
        } else if (status == LAST_PRE_EXISTING_ENTITY_SCANNED &&
                n == curNetworkCtx.lastScannedPostUpgrade()) {
            curNetworkCtx.setPreExistingEntityScanStatus(ALL_PRE_EXISTING_ENTITIES_SCANNED);
        }
    }

    @VisibleForTesting
    SystemTask[] getTasks() {
        return tasks;
    }
}
