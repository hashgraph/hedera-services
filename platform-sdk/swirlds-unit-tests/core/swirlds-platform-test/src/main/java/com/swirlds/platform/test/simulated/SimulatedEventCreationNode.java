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

package com.swirlds.platform.test.simulated;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.event.EventCreatorThread;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.ChatterEventCreator;
import com.swirlds.platform.event.creation.LoggingEventCreationRules;
import com.swirlds.platform.event.creation.OtherParentTracker;
import com.swirlds.platform.event.creation.ParentBasedCreationRule;
import com.swirlds.platform.event.creation.StaticCreationRules;
import com.swirlds.platform.event.intake.ChatterEventMapper;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.test.simulated.config.NodeConfig;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
public class SimulatedEventCreationNode implements GossipMessageHandler {
    private static final ParentBasedCreationRule NULL_OTHER_PARENT = StaticCreationRules::nullOtherParent;
    private final Time time;
    private final NodeId nodeId;
    private final Function<Hash, EventImpl> eventByHash;
    private final CriticalQuorum criticalQuorum;
    private final NodeConfig config;
    private final ChatterEventCreator chatterEventCreator;
    private final EventCreatorThread creatorThread;
    private final ChatterEventMapper chatterEventMapper;
    private boolean genesisCreated = false;
    private Instant nextEventCreation;

    /**
     * @param softwareVersion software version of the node
     * @param random          source of randomness
     * @param time            current time
     * @param addressBook     address book of the network
     * @param consumers       consumers of created events
     * @param nodeId          this node's ID
     * @param eventByHash     retrive an {@link EventImpl} by its hash
     * @param config          the configuration for this node
     */
    public SimulatedEventCreationNode(
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final Random random,
            @NonNull final Time time,
            @NonNull final AddressBook addressBook,
            @NonNull final List<Consumer<GossipEvent>> consumers,
            @NonNull final NodeId nodeId,
            @NonNull final Function<Hash, EventImpl> eventByHash,
            @NonNull final NodeConfig config) {
        Objects.requireNonNull(softwareVersion, "the software version is null");
        Objects.requireNonNull(random, "the random is null");
        Objects.requireNonNull(addressBook, "the address book is null");
        Objects.requireNonNull(consumers, "the consumers is null");

        this.time = Objects.requireNonNull(time, "the time is null");
        this.nodeId = Objects.requireNonNull(nodeId, "the node ID is null");
        this.eventByHash = Objects.requireNonNull(eventByHash, "the event by hash function is null");
        this.config = Objects.requireNonNull(config, "the node config is null");

        final Configuration configuration = new TestConfigBuilder()
                .withValue("event.creation.useTipsetAlgorithm", "false")
                .getOrCreateConfig();

        final ChatterConfig chatterConfig = configuration.getConfigData(ChatterConfig.class);
        final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);

        criticalQuorum = new CriticalQuorumImpl(
                new NoOpMetrics(),
                addressBook.getNodeId(0),
                addressBook,
                false,
                chatterConfig.criticalQuorumSoftening());

        final OtherParentTracker otherParentTracker = new OtherParentTracker();
        final LoggingEventCreationRules eventCreationRules = LoggingEventCreationRules.create(
                List.of(), List.of(NULL_OTHER_PARENT, otherParentTracker, criticalQuorum));
        chatterEventMapper = new ChatterEventMapper();

        final Cryptography cryptography = Mockito.mock(Cryptography.class);
        Mockito.when(cryptography.digestSync(ArgumentMatchers.any(SerializableHashable.class)))
                .thenAnswer(invocation -> {
                    final Hash hash = RandomUtils.randomHash(random);
                    invocation.getArgument(0, SerializableHashable.class).setHash(hash);
                    return hash;
                });

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        chatterEventCreator = new ChatterEventCreator(
                platformContext,
                softwareVersion,
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
                threadConfig,
                nodeId,
                1, // not used since the thread does not run
                addressBook,
                chatterEventCreator::createEvent,
                random);
        nextEventCreation = time.now();
    }

    @NonNull
    @Override
    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     * Maybe create an event (depends on the creation rules and creation rate) and send it to the provided consumers
     */
    public void maybeCreateEvent() {
        if (config.createEventEvery().isZero() || time.now().isBefore(nextEventCreation)) {
            return;
        }
        nextEventCreation = nextEventCreation.plus(config.createEventEvery());
        if (!genesisCreated) {
            chatterEventCreator.createGenesisEvent();
            genesisCreated = true;
        }
        creatorThread.createEvent();
    }

    /**
     * Add an event created by another nodes
     *
     * @param msg the message to add
     */
    @Override
    public void handleMessageFromWire(@NonNull final SelfSerializable msg, @NonNull final NodeId fromPeer) {
        if (msg instanceof final GossipEvent event) {
            notifyCriticalQuorum(event);
            chatterEventMapper.mapEvent(event);
        } else {
            throw new RuntimeException("unrecognized message received via simulated gossip");
        }
    }

    private void notifyCriticalQuorum(@NonNull final GossipEvent event) {
        criticalQuorum.eventAdded(eventByHash.apply(event.getHashedData().getHash()));
    }
}
