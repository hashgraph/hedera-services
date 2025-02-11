/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.consensus;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.consensus.SyntheticSnapshot.GENESIS_SNAPSHOT;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.component.framework.model.WiringModelBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.builder.ApplicationCallbacks;
import com.swirlds.platform.builder.PlatformBuildingBlocks;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.components.DefaultEventWindowManager;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.preconsensus.InlinePcesWriter;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.RosterUpdate;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.pool.TransactionPoolNexus;
import com.swirlds.platform.roster.RosterHistory;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.fixtures.state.FakeStateLifecycles;
import com.swirlds.platform.util.RandomBuilder;
import com.swirlds.platform.wiring.PlatformSchedulersConfig_;
import com.swirlds.platform.wiring.PlatformWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * Event intake with consensus and shadowgraph, used for testing
 */
public class ProdTestIntake implements Intake {
    private final ConsensusOutput output;
    private final PlatformWiring platformWiring;
    /**
     * @param platformContext the platform context used to configure this intake.
     * @param addressBook     the address book used by this intake
     */
    public ProdTestIntake(@NonNull final PlatformContext platformContext, @NonNull final AddressBook addressBook) {
        final NodeId selfId = NodeId.of(0);
        final var roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();

        final Time time = Time.getCurrent();
        output = new ConsensusOutput(time);
        final TestConfigBuilder builder = new TestConfigBuilder();
        platformContext
                .getConfiguration()
                .getPropertyNames()
                .forEach(name -> builder.withValue(
                        name, platformContext.getConfiguration().getValue(name)));
        var configuration = builder.withValue(PlatformSchedulersConfig_.EVENT_HASHER, "DIRECT")
                .withValue(PlatformSchedulersConfig_.EVENT_DEDUPLICATOR, "DIRECT")
                .withValue(PlatformSchedulersConfig_.EVENT_SIGNATURE_VALIDATOR, "DIRECT")
                .withValue(PlatformSchedulersConfig_.INTERNAL_EVENT_VALIDATOR, "DIRECT")
                .withValue(PlatformSchedulersConfig_.CONSENSUS_ENGINE, "DIRECT")
                .withValue(PlatformSchedulersConfig_.ORPHAN_BUFFER, "DIRECT")
                .withValue(PlatformSchedulersConfig_.PCES_INLINE_WRITER, "DIRECT")
                .withValue(PlatformSchedulersConfig_.CONSENSUS_EVENT_STREAM, "NO_OP")
                .withValue(PlatformSchedulersConfig_.PLATFORM_PUBLISHER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.SELF_EVENT_SIGNER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.APPLICATION_TRANSACTION_PREHANDLER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.BRANCH_DETECTOR, "NO_OP")
                .withValue(PlatformSchedulersConfig_.TRANSACTION_POOL, "NO_OP")
                .withValue(PlatformSchedulersConfig_.STATE_HASHER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.STATE_GARBAGE_COLLECTOR, "NO_OP")
                .withValue(PlatformSchedulersConfig_.ISS_HANDLER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.TRANSACTION_RESUBMITTER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.STATE_SNAPSHOT_MANAGER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.HASH_LOGGER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.SIGNED_STATE_SENTINEL, "NO_OP")
                .withValue(PlatformSchedulersConfig_.BRANCH_REPORTER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.ISS_DETECTOR, "NO_OP")
                .withValue(PlatformSchedulersConfig_.STATE_SIGNER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.STATE_SIGNATURE_COLLECTOR, "NO_OP")
                .withValue(PlatformSchedulersConfig_.TRANSACTION_HANDLER, "NO_OP")
                .withValue(PlatformSchedulersConfig_.STATUS_STATE_MACHINE, "NO_OP")
                .withValue(PlatformSchedulersConfig_.LATEST_COMPLETE_STATE_NOTIFIER, "NO_OP")
                .getOrCreateConfig();

        var model = WiringModelBuilder.create(platformContext)
                .withDeterministicModeEnabled(true)
                .build();

        final var context = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .withTime(platformContext.getTime())
                .withMetrics(platformContext.getMetrics())
                .withCryptography(platformContext.getCryptography())
                .withMerkleCryptography(platformContext.getMerkleCryptography())
                .withRecycleBin(platformContext.getRecycleBin())
                .withFileSystemManager(platformContext.getFileSystemManager())
                .build();

        final Randotron randotron = Randotron.create();
        // Mock the ReadableRosterStore

        var roster = RosterRetriever.buildRoster(addressBook);
        var hash = RosterUtils.hash(roster).getBytes();
        final PlatformBuildingBlocks buildingBlocks = new PlatformBuildingBlocks(
                context,
                model,
                mock(KeysAndCerts.class),
                selfId,
                "appName",
                "swirldName",
                new BasicSoftwareVersion(1),
                mock(),
                new RosterHistory(
                        IntStream.range(0, roundsNonAncient)
                                .boxed()
                                .map(i -> RoundRosterPair.newBuilder()
                                        .roundNumber(i)
                                        .activeRosterHash(hash)
                                        .build())
                                .toList(),
                        Map.of(hash, roster)),
                new ApplicationCallbacks(x -> {}, x -> {}, x -> {}, x -> Bytes.EMPTY),
                x -> {},
                x -> {},
                new NoOpIntakeEventCounter(),
                new RandomBuilder(randotron.nextLong()),
                new TransactionPoolNexus(platformContext),
                new AtomicReference<>(),
                new AtomicReference<>(),
                mock(),
                "name",
                mock(),
                NotificationEngine.buildEngine(getStaticThreadManager()),
                new AtomicReference<>(),
                mock(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                false,
                FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES);

        platformWiring =
                new PlatformWiring(context, model, buildingBlocks.applicationCallbacks(), output::consensusRound);
        platformWiring.bind(
                new PlatformComponentBuilder(buildingBlocks)
                        .withGossip(mock())
                        .withEventSignatureValidator(new EventSignatureValidator() {
                            @Nullable
                            @Override
                            public PlatformEvent validateSignature(@NonNull final PlatformEvent event) {
                                return event;
                            }

                            @Override
                            public void setEventWindow(@NonNull final EventWindow eventWindow) {}

                            @Override
                            public void updateRosters(@NonNull final RosterUpdate rosterUpdate) {}
                        })
                        .withInlinePcesWriter(new InlinePcesWriter() {

                            @Override
                            public void beginStreamingNewEvents() {}

                            @NonNull
                            @Override
                            public PlatformEvent writeEvent(@NonNull final PlatformEvent event) {
                                return event;
                            }

                            @Override
                            public void registerDiscontinuity(@NonNull final Long newOriginRound) {}

                            @Override
                            public void updateNonAncientEventBoundary(@NonNull final EventWindow nonAncientBoundary) {}

                            @Override
                            public void setMinimumAncientIdentifierToStore(
                                    @NonNull final Long minimumAncientIdentifierToStore) {}
                        }),
                mock(),
                mock(),
                new DefaultEventWindowManager(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock());

        model.start();
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final PlatformEvent event) {
        platformWiring.getPlatformEventInput().inject(event);
        output.eventAdded(event);
    }

    /**
     * Same as {@link #addEvent(PlatformEvent)} but for a list of events
     */
    public void addEvents(@NonNull final List<EventImpl> events) {
        for (final EventImpl event : events) {
            addEvent(event.getBaseEvent());
        }
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public @NonNull LinkedList<ConsensusRound> getConsensusRounds() {
        return output.getConsensusRounds();
    }

    public @Nullable ConsensusRound getLatestRound() {
        return output.getConsensusRounds().getLast();
    }

    public void loadSnapshot(@NonNull final ConsensusSnapshot snapshot) {
        platformWiring.consensusSnapshotOverride(snapshot);
    }

    public @NonNull ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        loadSnapshot(GENESIS_SNAPSHOT);
        output.clear();
    }

    static Roster buildValidRoster() {
        return Roster.newBuilder()
                .rosterEntries(
                        RosterEntry.newBuilder()
                                .nodeId(0)
                                .weight(1)
                                .gossipCaCertificate(Bytes.wrap("test"))
                                .gossipEndpoint(ServiceEndpoint.newBuilder()
                                        .domainName("domain.com")
                                        .port(666)
                                        .build())
                                .build(),
                        RosterEntry.newBuilder()
                                .nodeId(1)
                                .weight(2)
                                .gossipCaCertificate(Bytes.wrap("test"))
                                .gossipEndpoint(ServiceEndpoint.newBuilder()
                                        .domainName("domain.com")
                                        .port(666)
                                        .build())
                                .build())
                .build();
    }
}
