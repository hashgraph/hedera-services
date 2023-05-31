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

import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;

public class InputEventsValidation {
    public static void validateInputsAreTheSame(final ConsensusOutput output1, final ConsensusOutput output2) {
        // Verify that ALL base events fed into consensus are exactly identical
        // this will check only pre-consensus data, for non-consensus events, the consensus data
        // does not have to match
        assertBaseEventLists("Verifying input events", output1.sortedAddedEvents(), output2.sortedAddedEvents());
    }

    /**
     * Assert that base events are equal. This does not check any consensus data, only
     * pre-consensus. If they are not equal then cause the test to fail and print a meaningful error
     * message.
     *
     * @param description a string that is printed if the events are unequal
     * @param l1 the first list of events
     * @param l2 the second list of events
     */
    public static void assertBaseEventLists(
            final String description, final List<EventImpl> l1, final List<EventImpl> l2) {

        if (l1.size() != l2.size()) {
            Assertions.fail(String.format("Length of event lists are unequal: %d vs %d", l1.size(), l2.size()));
        }

        for (int index = 0; index < l1.size(); index++) {
            final EventImpl e1 = l1.get(index);
            final EventImpl e2 = l2.get(index);
            if (!Objects.equals(e1.getBaseEvent(), e2.getBaseEvent())) {
                final String sb = description
                        + "\n"
                        + "Events are not equal:\n"
                        + "Event 1: "
                        + e1
                        + "\n"
                        + "Event 2: "
                        + e2
                        + "\n"
                        + "at index: "
                        + index;
                Assertions.fail(sb);
            }
        }
    }

    /**
     * Assert that base events are equal. This does not check any consensus data, only
     * pre-consensus. If they are not equal then cause the test to fail and print a meaningful error
     * message.
     *
     * @param description a string that is printed if the events are unequal
     * @param e1 the first event
     * @param e2 the second event
     */
    public static void assertBaseEvents(final String description, final EventImpl e1, final EventImpl e2) {
        if (!Objects.equals(e1.getBaseEvent(), e2.getBaseEvent())) {
            final String sb =
                    description + "\n" + "Events are not equal:\n" + "Event 1: " + e1 + "\n" + "Event 2: " + e2 + "\n";
            Assertions.fail(sb);
        }
    }
}
