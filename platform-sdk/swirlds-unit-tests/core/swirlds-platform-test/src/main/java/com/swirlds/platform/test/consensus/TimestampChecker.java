/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus;

import com.swirlds.platform.test.event.IndexedEvent;
import java.util.List;

public class TimestampChecker implements SingleConsensusChecker {
    private IndexedEvent previousConsensusEvent = null;

    public boolean check(List<IndexedEvent> consensusEvents) {
        if (consensusEvents == null) {
            return true;
        }
        for (IndexedEvent e : consensusEvents) {
            if (previousConsensusEvent == null) {
                previousConsensusEvent = e;
                continue;
            }

            if (!e.getConsensusTimestamp().isAfter(previousConsensusEvent.getConsensusTimestamp())) {
                System.out.printf(
                        "Consensus time does not increase!\n" + "Event %s consOrder:%s consTime:%s\n"
                                + "Event %s consOrder:%s consTime:%s\n",
                        previousConsensusEvent.toShortString(),
                        previousConsensusEvent.getConsensusOrder(),
                        previousConsensusEvent.getConsensusTimestamp(),
                        e.toShortString(),
                        e.getConsensusOrder(),
                        e.getConsensusTimestamp());
                return false;
            }
            previousConsensusEvent = e;
        }
        return true;
    }
}
