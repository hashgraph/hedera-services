/*
 * Copyright (C) 2016-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.component.framework.component.InputWireLabel;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Applies transactions from rounds that have reached consensus to the state
 */
public interface TransactionHandler {

    /**
     * This method is called after a restart or a reconnect. It provides the previous round's legacy running event hash,
     * in case we need it.
     *
     * @param runningHashUpdate the update to the running hash
     */
    @InputWireLabel("hash override")
    void updateLegacyRunningEventHash(@NonNull RunningEventHashOverride runningHashUpdate);

    /**
     * Applies the transactions in the consensus round to the state
     *
     * @param consensusRound the consensus round to apply
     * @return a new signed state, along with the consensus round that caused it to be created. null if no new state was
     * created
     */
    @Nullable
    StateAndRound handleConsensusRound(@NonNull ConsensusRound consensusRound);
}
