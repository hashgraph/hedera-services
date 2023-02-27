/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.transaction.system;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.State;

/**
 * Keeps track of system transaction handling methods.
 * <p>
 * Has the ability to handle pre-consensus events, and post-consensus rounds
 */
public interface SystemTransactionManager {
    /**
     * Handle a pre-consensus event by passing each included system transaction to the relevant registered handlers
     *
     * @param state an unmodifiable state. if an attempt is made to modify this state, an exception will be thrown
     * @param event the pre-consensus event
     */
    void handlePreConsensusEvent(State state, EventImpl event);

    /**
     * Handle a post-consensus round by passing each included system transaction to the relevant registered handlers
     *
     * @param state a state, which may or may not be modified by the handling methods
     * @param round the post-consensus round
     */
    void handlePostConsensusRound(State state, ConsensusRound round);
}
