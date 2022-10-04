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

import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;

/**
 * Minimal interface for a system task that needs to perform deterministic work on (possibly) every
 * entity in state.
 */
public interface SystemTask {
    /**
     * Whether this task is still active.
     *
     * @param literalNum the id of the entity to consider processing
     * @param curNetworkCtx the current network context
     * @return if the task manager still needs to include this task in processing
     */
    default boolean isActive(final long literalNum, final MerkleNetworkContext curNetworkCtx) {
        return true;
    }

    /**
     * Attempts to do this task's work on the entity with the given id, if applicable and capacity
     * and context permit.
     *
     * @param literalNum the id of the entity to process
     * @param now the current consensus time
     * @return the result of the task's work
     */
    SystemTaskResult process(long literalNum, Instant now);
}
