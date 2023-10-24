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

package com.swirlds.platform;

import static com.swirlds.platform.consensus.ConsensusConstants.MIN_TRANS_TIMESTAMP_INCR_NANOS;
import static com.swirlds.platform.consensus.ConsensusUtils.calcMinTimestampForNextEvent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConsensusImplTest {

    @Test
    void calcMinTimestampForNextEventTest() {
        final Instant lastTransTimestamp = Instant.now();
        final Instant calculatedTimestamp = calcMinTimestampForNextEvent(lastTransTimestamp);
        final Instant lastTransTimestampPlusIncr = lastTransTimestamp.plusNanos(MIN_TRANS_TIMESTAMP_INCR_NANOS);
        assertFalse(
                calculatedTimestamp.isBefore(lastTransTimestampPlusIncr),
                "the timestamp should not be less than lastTransTimestamp + Settings.minTransTimestampIncrNanos");

        final int calculatedTimestampNanos = calculatedTimestamp.getNano();
        assertTrue(
                calculatedTimestampNanos % MIN_TRANS_TIMESTAMP_INCR_NANOS == 0
                        && (calculatedTimestampNanos - lastTransTimestampPlusIncr.getNano())
                                        / MIN_TRANS_TIMESTAMP_INCR_NANOS
                                < 1,
                "the timestamp should be the nearest multiple of minTransTimestampIncr");
    }
}
