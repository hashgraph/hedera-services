/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.state.tasks;

import static com.hedera.services.state.merkle.MerkleNetworkContext.*;
import static com.hedera.services.state.tasks.SystemTaskResult.*;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SystemTaskManager {
    private static final Logger log = LogManager.getLogger(SystemTaskManager.class);
    private final SystemTask[] tasks;

    @Inject
    public SystemTaskManager(final Map<String, SystemTask> namedTasks) {
        // Only runs once per restart, so performance doesn't matter
        tasks =
                namedTasks.keySet().stream()
                        .sorted()
                        .map(namedTasks::get)
                        .toArray(SystemTask[]::new);
        log.info("Discovered {} tasks ({})", tasks.length, namedTasks.keySet());
    }

    /**
     * Performs as many pending system tasks as possible for the given entity number and consensus
     * time. These tasks will begin from {@link MerkleNetworkContext#nextTaskTodo()} and continue
     * until either the manager receives {@link SystemTaskResult#NO_CAPACITY_LEFT} or {@link
     * SystemTaskResult#NEEDS_DIFFERENT_CONTEXT}; or until the end of the {@code tasks} list is
     * reached.
     *
     * <p>More precisely, this method will return:
     *
     * <ul>
     *   <li>{@link SystemTaskResult#NOTHING_TO_DO} iff all tasks returned {@code NOTHING_TO_DO}.
     *   <li>{@link SystemTaskResult#DONE} iff at least one task returned {@code DONE} and all other
     *       tasks returned {@code NOTHING_TO_DO}.
     *   <li>{@link SystemTaskResult#NO_CAPACITY_LEFT} immediately when any task reports it.
     *   <li>{@link SystemTaskResult#NEEDS_DIFFERENT_CONTEXT} immediately when any task reports it.
     * </ul>
     *
     * @param literalNum the id to process
     * @param now the consensus time now
     * @param curNetworkCtx the current network context
     * @return the cumulative result of attempting all pending tasks
     */
    public SystemTaskResult process(
            final long literalNum, final Instant now, final MerkleNetworkContext curNetworkCtx) {
        boolean didSomething = false;
        for (var i = curNetworkCtx.nextTaskTodo(); i < tasks.length; i++) {
            if (!tasks[i].isActive(literalNum, curNetworkCtx)) {
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
        // Only update the pre-existing scan status when we complete all tasks for an entity id
        updatePreExistingScanStatus(literalNum, curNetworkCtx);
        return didSomething ? DONE : NOTHING_TO_DO;
    }

    @VisibleForTesting
    void updatePreExistingScanStatus(final long n, final MerkleNetworkContext curNetworkCtx) {
        final var status = curNetworkCtx.getPreExistingEntityScanStatus();
        if (status == LAST_PRE_EXISTING_ENTITY_NOT_SCANNED) {
            if (n == curNetworkCtx.seqNoPostUpgrade() - 1) {
                log.info(
                        "Setting pre-existing entity scan status to"
                                + " LAST_PRE_EXISTING_ENTITY_SCANNED");
                curNetworkCtx.setPreExistingEntityScanStatus(LAST_PRE_EXISTING_ENTITY_SCANNED);
            }
        } else if (status == LAST_PRE_EXISTING_ENTITY_SCANNED
                && n == curNetworkCtx.lastScannedPostUpgrade()) {
            log.info(
                    "Setting pre-existing entity scan status to ALL_PRE_EXISTING_ENTITIES_SCANNED");
            curNetworkCtx.setPreExistingEntityScanStatus(ALL_PRE_EXISTING_ENTITIES_SCANNED);
        }
    }

    @VisibleForTesting
    SystemTask[] getTasks() {
        return tasks;
    }
}
