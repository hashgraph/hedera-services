// SPDX-License-Identifier: Apache-2.0
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
