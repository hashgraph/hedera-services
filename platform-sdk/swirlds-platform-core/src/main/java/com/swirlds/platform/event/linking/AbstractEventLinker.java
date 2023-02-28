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

package com.swirlds.platform.event.linking;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.consensus.RoundCalculationUtils;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.sync.Generations;

/**
 * Common functionality for an {@link EventLinker}
 */
public abstract class AbstractEventLinker implements EventLinker {
    private final ConsensusConfig config;

    /**
     * @param config consensus configuration
     */
    protected AbstractEventLinker(final ConsensusConfig config) {
        this.config = config;
    }

    private long minGenerationNonAncient = Generations.GENESIS_GENERATIONS.getMinGenerationNonAncient();

    /**
     * Same as {@link GraphGenerations#getMinGenerationNonAncient()}
     */
    public long getMinGenerationNonAncient() {
        return minGenerationNonAncient;
    }

    @Override
    public void loadFromSignedState(final SignedState signedState) {
        minGenerationNonAncient = RoundCalculationUtils.getMinGenNonAncient(config.roundsNonAncient(), signedState);
    }

    @Override
    public void updateGenerations(final GraphGenerations generations) {
        minGenerationNonAncient = generations.getMinGenerationNonAncient();
    }

    @Override
    public void clear() {
        minGenerationNonAncient = Generations.GENESIS_GENERATIONS.getMinGenerationNonAncient();
    }
}
