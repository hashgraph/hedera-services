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

package com.swirlds.platform.test.simulated;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.event.EventCreatorThread;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.ChatterEventCreator;
import com.swirlds.platform.event.creation.LoggingEventCreationRules;
import com.swirlds.platform.event.creation.OtherParentTracker;
import com.swirlds.platform.event.creation.ParentBasedCreationRule;
import com.swirlds.platform.event.creation.StaticCreationRules;
import com.swirlds.platform.event.intake.ChatterEventMapper;
import com.swirlds.platform.internal.EventImpl;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Used for simulating a node's event creation, this node will create and send events as well as receive events from
 * other nodes
 */
public class SimulatedEventCreationNode {
    private static final ParentBasedCreationRule NULL_OTHER_PARENT = StaticCreationRules::nullOtherParent;
    private final NodeId nodeId;
    private final Function<Hash, EventImpl> eventByHash;
    private final CriticalQuorum criticalQuorum;
    private final ChatterEventCreator chatterEventCreator;
    private final EventCreatorThread creatorThread;
    private final ChatterEventMapper chatterEventMapper;
    private boolean genesisCreated = false;

    /**
     * @param random
     * 		source of randomness
     * @param time
     * 		current time
     * @param addressBook
     * 		address book of the network
     * @param consumers
     * 		consumers of created events
     * @param nodeId
     * 		this node's ID
     * @param eventByHash
     * 		retrive an {@link EventImpl} by its hash
     * @param shouldCreateEvents
     * 		should this node create events
     */
    public SimulatedEventCreationNode(
            final Random random,
            final Time time,
            final AddressBook addressBook,
            final List<Consumer<GossipEvent>> consumers,
            final NodeId nodeId,
            final Function<Hash, EventImpl> eventByHash,
            final boolean shouldCreateEvents) {
        this.nodeId = nodeId;
        this.eventByHash = eventByHash;
        criticalQuorum =
                new CriticalQuorumImpl(addressBook, false, new ChatterSubSetting().getCriticalQuorumSoftening());
        final OtherParentTracker otherParentTracker = new OtherParentTracker();
        final LoggingEventCreationRules eventCreationRules = LoggingEventCreationRules.create(
                List.of(() ->
                        shouldCreateEvents ? EventCreationRuleResponse.PASS : EventCreationRuleResponse.DONT_CREATE),
                List.of(NULL_OTHER_PARENT, otherParentTracker, criticalQuorum));
        chatterEventMapper = new ChatterEventMapper();

        final Cryptography cryptography = Mockito.mock(Cryptography.class);
        Mockito.when(cryptography.digestSync(ArgumentMatchers.any(SerializableHashable.class)))
                .thenAnswer(invocation -> {
                    final Hash hash = RandomUtils.randomHash(random);
                    invocation.getArgument(0, SerializableHashable.class).setHash(hash);
                    return hash;
                });
        chatterEventCreator = new ChatterEventCreator(
                nodeId,
                new RandomSigner(random),
                () -> new ConsensusTransactionImpl[0],
                CommonUtils.combineConsumers(Stream.concat(
                                consumers.stream(),
                                Stream.of(
                                        otherParentTracker::track,
                                        chatterEventMapper::mapEvent,
                                        this::notifyCriticalQuorum))
                        .toList()),
                chatterEventMapper::getMostRecentEvent,
                eventCreationRules,
                cryptography,
                time);

        creatorThread = new EventCreatorThread(
                getStaticThreadManager(),
                nodeId,
                1, // not used since the thread does not run
                addressBook,
                chatterEventCreator::createEvent,
                random);
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     * Maybe create an event (depends on the creation rules) and send it to the provided consumers
     */
    public void createEvent() {
        if (!genesisCreated) {
            chatterEventCreator.createGenesisEvent();
            genesisCreated = true;
        }
        creatorThread.createEvent();
    }

    /**
     * Add an event created by another nodes
     *
     * @param event
     * 		the event to add
     */
    public void addEvent(final GossipEvent event) {
        notifyCriticalQuorum(event);
        chatterEventMapper.mapEvent(event);
    }

    private void notifyCriticalQuorum(final GossipEvent event) {
        criticalQuorum.eventAdded(eventByHash.apply(event.getHashedData().getHash()));
    }
}
