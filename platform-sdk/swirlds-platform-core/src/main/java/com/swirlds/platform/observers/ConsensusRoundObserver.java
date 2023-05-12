/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.observers;

import com.swirlds.platform.internal.ConsensusRound;

/**
 * An observer that is notified when a round has achieved consensus.
 */
@FunctionalInterface
public interface ConsensusRoundObserver extends EventObserver {

    /**
     * Announce that the given round has achieved consensus
     *
     * @param consensusRound
     * 		the round
     */
    void consensusRound(final ConsensusRound consensusRound);
}
