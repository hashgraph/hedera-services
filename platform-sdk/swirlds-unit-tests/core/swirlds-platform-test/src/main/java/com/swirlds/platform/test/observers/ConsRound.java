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

package com.swirlds.platform.test.observers;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.observers.ConsensusRoundObserver;

public class ConsRound extends SimpleConsensusRoundTracker implements ConsensusRoundObserver {
    @Override
    public void consensusRound(final ConsensusRound round) {
        observe(ObservationType.CONSENSUS, round);
    }
}
