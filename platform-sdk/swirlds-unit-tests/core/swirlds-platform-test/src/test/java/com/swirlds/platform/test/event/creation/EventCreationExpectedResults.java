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

package com.swirlds.platform.test.event.creation;

import com.swirlds.common.utility.DurationUtils;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;

/**
 * Used to store and expected results and validate {@link EventCreationExpectedResults}
 */
public class EventCreationExpectedResults {
    private int numEventsCreatedMax = Integer.MAX_VALUE;
    private int numEventsCreatedMin = 0;
    private int numConsEventsMin = 0;
    private boolean consensusExpected = true;
    private Duration maxC2CMax = Duration.ofSeconds(Long.MAX_VALUE);
    private Duration avgC2CMax = Duration.ofSeconds(Long.MAX_VALUE);
    private int maxRoundSizeMax = Integer.MAX_VALUE;

    public static EventCreationExpectedResults get() {
        return new EventCreationExpectedResults();
    }

    public EventCreationExpectedResults setNumEventsCreatedMax(final int numEventsCreatedMax) {
        this.numEventsCreatedMax = numEventsCreatedMax;
        return this;
    }

    public EventCreationExpectedResults setNumEventsCreatedMin(final int numEventsCreatedMin) {
        this.numEventsCreatedMin = numEventsCreatedMin;
        return this;
    }

    public EventCreationExpectedResults setNumConsEventsMin(final int numConsEventsMin) {
        this.numConsEventsMin = numConsEventsMin;
        return this;
    }

    public EventCreationExpectedResults setConsensusExpected(final boolean consensusExpected) {
        this.consensusExpected = consensusExpected;
        return this;
    }

    public EventCreationExpectedResults setMaxC2CMax(final Duration maxC2CMax) {
        this.maxC2CMax = maxC2CMax;
        return this;
    }

    public EventCreationExpectedResults setAvgC2CMax(final Duration avgC2CMax) {
        this.avgC2CMax = avgC2CMax;
        return this;
    }

    public EventCreationExpectedResults setMaxRoundSizeMax(final int maxRoundSizeMax) {
        this.maxRoundSizeMax = maxRoundSizeMax;
        return this;
    }

    public void validate(final EventCreationSimulationResults r) {
        Assertions.assertTrue(numEventsCreatedMax >= r.numEventsCreated());
        Assertions.assertTrue(numEventsCreatedMin <= r.numEventsCreated());
        if (!consensusExpected) {
            Assertions.assertEquals(0, r.numConsEvents());
            return;
        }
        Assertions.assertTrue(numConsEventsMin <= r.numConsEvents());
        Assertions.assertNotNull(r.avgC2C());
        Assertions.assertNotNull(r.maxC2C());
        Assertions.assertFalse(DurationUtils.isLonger(r.maxC2C(), maxC2CMax));
        Assertions.assertFalse(DurationUtils.isLonger(r.avgC2C(), avgC2CMax));
        Assertions.assertNotNull(r.maxRoundSize());
        Assertions.assertTrue(maxRoundSizeMax >= r.maxRoundSize());
    }
}
