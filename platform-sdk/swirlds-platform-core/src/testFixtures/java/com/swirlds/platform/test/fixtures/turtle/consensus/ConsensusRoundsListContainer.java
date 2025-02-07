/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.turtle.consensus;

import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * A container for collecting list of consensus rounds produced by the ConsensusEngine using List.
 */
public class ConsensusRoundsListContainer implements ConsensusRoundsHolder {

    final List<ConsensusRound> collectedRounds = new ArrayList<>();

    @Override
    public void interceptRounds(final List<ConsensusRound> rounds) {
        if (!rounds.isEmpty()) {
            collectedRounds.addAll(rounds);
        }

        // TODO add validation logic for the events
    }

    @Override
    public void clear(@NonNull final Object ignored) {
        collectedRounds.clear();
    }
}
