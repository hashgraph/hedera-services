/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

public class FakeRound implements Round {
    private final long roundNum;
    private final AddressBook addressBook;
    private final List<ConsensusEvent> consensusEvents;

    public FakeRound(
            final long roundNum,
            @NonNull final AddressBook addressBook,
            @NonNull final List<ConsensusEvent> consensusEvents) {
        this.roundNum = roundNum;
        this.addressBook = requireNonNull(addressBook);
        this.consensusEvents = requireNonNull(consensusEvents);
    }

    @NonNull
    @Override
    public Iterator<ConsensusEvent> iterator() {
        return consensusEvents.iterator();
    }

    @Override
    public long getRoundNum() {
        return roundNum;
    }

    @Override
    public boolean isEmpty() {
        return consensusEvents.isEmpty();
    }

    @Override
    public int getEventCount() {
        return consensusEvents.size();
    }

    @NonNull
    @Override
    public AddressBook getConsensusRoster() {
        return addressBook;
    }

    @NonNull
    @Override
    public Instant getConsensusTimestamp() {
        return consensusEvents.getLast().getConsensusTimestamp();
    }
}
