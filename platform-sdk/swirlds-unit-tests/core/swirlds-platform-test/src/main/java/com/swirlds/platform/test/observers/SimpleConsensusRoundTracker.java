/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SimpleConsensusRoundTracker {
    private final Map<ObservationType, Set<Long>> consensusRoundsObserved;

    public SimpleConsensusRoundTracker() {
        this.consensusRoundsObserved = new HashMap<>();
        for (final ObservationType type : ObservationType.values()) {
            this.consensusRoundsObserved.put(type, new HashSet<>());
        }
    }

    public void observe(final ObservationType observation, final ConsensusRound consensusRound) {
        if (isObserved(observation, consensusRound)) {
            throw new RuntimeException("Consensus round should not be observed twice");
        }
    }

    public boolean isObserved(final ObservationType observation, final ConsensusRound consensusRound) {
        return consensusRoundsObserved.get(observation).contains(consensusRound.getRoundNum());
    }

    public void clear() {
        for (final Set<Long> value : consensusRoundsObserved.values()) {
            value.clear();
        }
    }
}
