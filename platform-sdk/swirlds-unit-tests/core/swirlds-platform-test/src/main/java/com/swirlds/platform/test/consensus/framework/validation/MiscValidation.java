/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import java.util.List;

public class MiscValidation {
    /**
     * Verifies that the created round of new events does not advance when a quorum of nodes is
     * down.
     */
    private static void createdRoundDoesNotAdvance(
            final ConsensusOutput output1, final ConsensusOutput ignored) {
        final List<EventImpl> allEvents = output1.getAddedEvents();
        final long firstRoundInSequence = allEvents.get(0).getRoundCreated();
        final long secondRoundInSequence = firstRoundInSequence + 1;
        final long thirdRoundInSequence = secondRoundInSequence + 1;

        for (final EventImpl e : allEvents) {
            final long roundCreated = e.getRoundCreated();

            // Ignore the first three rounds of events in the sequence.
            // The first round may have events from the prior sequence which has a quorum
            // The second round's events could still be strongly seen by witnesses in the previous
            // round

            // It is possible for a third round to be created if the nodes that went down created
            // events
            // in secondRoundInSequence in the previous sequence. I.e. the nodes that were still
            // running
            // in this sequence created some events that had a created round of one less than the
            // last events
            // created by the crashed nodes.
            if (roundCreated == firstRoundInSequence
                    || roundCreated == secondRoundInSequence
                    || roundCreated == thirdRoundInSequence) {
                continue;
            }

            final long spRound =
                    e.getSelfParent() == null ? -1 : e.getSelfParent().getRoundCreated();
            final long opRound =
                    e.getOtherParent() == null ? -1 : e.getOtherParent().getRoundCreated();
            assertEquals(
                    Math.max(spRound, opRound),
                    roundCreated,
                    String.format(
                            "Created round of event %s should not advance when a quorum of nodes"
                                + " are down.\n"
                                + "created round: %s, sp created round: %s, op created round: %s",
                            e, roundCreated, spRound, opRound));
        }
    }
}
