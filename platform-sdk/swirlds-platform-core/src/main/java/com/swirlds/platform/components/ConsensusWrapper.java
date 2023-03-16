/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.sync.Generations;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * A wrapper for the consensus algorithm that returns {@link ConsensusRound} objects instead of {@link EventImpl}
 * objects. This class can be removed if/when the consensus interface is modified to return rounds.
 */
public class ConsensusWrapper {

    private final Supplier<Consensus> consensusSupplier;

    public ConsensusWrapper(final Supplier<Consensus> consensusSupplier) {
        this.consensusSupplier = consensusSupplier;
    }

    public List<ConsensusRound> addEvent(final EventImpl event, final AddressBook addressBook) {
        final List<EventImpl> consensusEvents = consensusSupplier.get().addEvent(event, addressBook);
        if (consensusEvents == null || consensusEvents.isEmpty()) {
            return null;
        }

        final SortedMap<Long, List<EventImpl>> roundEvents = new TreeMap<>();
        for (final EventImpl cEvent : consensusEvents) {
            final long round = cEvent.getRoundReceived();
            roundEvents.putIfAbsent(round, new LinkedList<>());
            roundEvents.get(round).add(cEvent);
        }

        final List<ConsensusRound> rounds = new LinkedList<>();
        for (final Map.Entry<Long, List<EventImpl>> entry : roundEvents.entrySet()) {
            rounds.add(new ConsensusRound(
                    entry.getValue(),
                    event,
                    new Generations(consensusSupplier.get()),
                    consensusSupplier.get().getMinGenerationNonAncient())); // TODO don't use this!
        }

        return rounds;
    }
}
