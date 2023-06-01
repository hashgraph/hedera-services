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

package com.swirlds.platform.test.consensus.framework.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.swirlds.platform.test.consensus.framework.validation.Validations.ValidationType.CONSENSUS_EVENTS;
import static com.swirlds.platform.test.consensus.framework.validation.Validations.ValidationType.CONSENSUS_TIMESTAMPS;
import static com.swirlds.platform.test.consensus.framework.validation.Validations.ValidationType.DIFFERENT_ORDER;
import static com.swirlds.platform.test.consensus.framework.validation.Validations.ValidationType.INPUTS_ARE_SAME;
import static com.swirlds.platform.test.consensus.framework.validation.Validations.ValidationType.RATIOS;

public class Validations {
    private final Map<ValidationType, ConsensusOutputValidation> map = new HashMap<>(Map.of(
            INPUTS_ARE_SAME,  InputEventsValidation::validateInputsAreTheSame,
            DIFFERENT_ORDER, InputEventsValidation::validateEventsAreInDifferentOrder,
            CONSENSUS_EVENTS, ConsensusRoundValidation::validateConsensusRounds,
            CONSENSUS_TIMESTAMPS, TimestampChecker::validateConsensusTimestamps
    ));

    public static Validations standard() {
        return new Validations();
    }

    public Validations remove(final ValidationType type){
        map.remove(type);
        return this;
    }

    public Validations ratios(final EventRatioValidation ratioValidation) {
        map.put(RATIOS, ratioValidation);
        return this;
    }

    public List<ConsensusOutputValidation> getList() {
        return map.values().stream().toList();
    }

    public enum ValidationType {
        INPUTS_ARE_SAME,
        DIFFERENT_ORDER,
        CONSENSUS_EVENTS,
        CONSENSUS_TIMESTAMPS,
        RATIOS
    }
}
