/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.runninghash;

import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Computes the running event hash of events that have reached consensus.
 */
public interface RunningEventHasher {

    /**
     * Compute the running event hash for the given round. When computed, write the hash to the round.
     *
     * @param round the round
     */
    @InputWireLabel("rounds")
    void computeRunningEventHash(@NonNull final ConsensusRound round);

    /**
     * Override the running event hash of the previous round. This must be called at restart and reconnect boundaries.
     *
     * @param runningEventHashOverride the running event hash override
     */
    @InputWireLabel("hash override")
    void overrideRunningEventHash(@NonNull final RunningEventHashOverride runningEventHashOverride);
}
