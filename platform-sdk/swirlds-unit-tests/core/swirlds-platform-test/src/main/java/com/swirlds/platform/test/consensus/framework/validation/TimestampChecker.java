/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Assertions;

public class TimestampChecker {
    public static void validateConsensusTimestamps(
            @NonNull final ConsensusOutput output1, @NonNull final ConsensusOutput ignored) {
        PlatformEvent previousConsensusEvent = null;

        for (final ConsensusRound round : output1.getConsensusRounds()) {
            for (final PlatformEvent e : round.getConsensusEvents()) {
                if (previousConsensusEvent == null) {
                    previousConsensusEvent = e;
                    continue;
                }
                Assertions.assertNotNull(e.getConsensusTimestamp());
                Assertions.assertNotNull(previousConsensusEvent.getConsensusTimestamp());
                Assertions.assertTrue(
                        e.getConsensusTimestamp().isAfter(previousConsensusEvent.getConsensusTimestamp()),
                        String.format(
                                "Consensus time does not increase!%n"
                                        + "Event %s consOrder:%s consTime:%s%n"
                                        + "Event %s consOrder:%s consTime:%s%n",
                                previousConsensusEvent.getDescriptor(),
                                previousConsensusEvent.getConsensusOrder(),
                                previousConsensusEvent.getConsensusTimestamp(),
                                e.getDescriptor(),
                                e.getConsensusOrder(),
                                e.getConsensusTimestamp()));
                previousConsensusEvent = e;
            }
        }
    }
}
