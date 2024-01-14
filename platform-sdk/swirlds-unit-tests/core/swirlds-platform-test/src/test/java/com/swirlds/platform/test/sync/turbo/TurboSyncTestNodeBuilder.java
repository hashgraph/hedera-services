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

package com.swirlds.platform.test.sync.turbo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.turbo.TurboSyncRunner;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Configures and builds a simulated node for testing turbo sync.
 */
public class TurboSyncTestNodeBuilder {

    private final Time time;

    private PlatformContext platformContext;
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final NodeId peerId;

    private List<EventImpl> knownEvents = List.of();

    private FallenBehindManager fallenBehindManager;

    private BooleanSupplier gossipHalted;
    private BooleanSupplier intakeIsTooFull;
    private IntakeEventCounter intakeEventCounter;
    private SyncMetrics syncMetrics;
    private ShadowGraph shadowgraph;
    private Supplier<GraphGenerations> generationsSupplier;
    private LatestEventTipsetTracker latestEventTipsetTracker;

    /**
     * Constructor.
     *
     * @param time        provides wall clock time (or fake wall clock time)
     * @param addressBook the address book
     * @param selfId      the id of this node
     * @param peerId      the id of the peer we are syncing with
     */
    public TurboSyncTestNodeBuilder(
            @NonNull final Time time,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final NodeId peerId) {

        this.time = Objects.requireNonNull(time);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.peerId = Objects.requireNonNull(peerId);
    }

    /**
     * Setup default values for any parameters that were not provided. Build all subsystems that are needed by the turbo
     * sync runner.
     */
    private void buildSubsystems() {
        if (platformContext == null) {
            platformContext = TestPlatformContextBuilder.create().withTime(time).build();
        }

        fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.hasFallenBehind()).thenReturn(false);

        if (gossipHalted == null) {
            gossipHalted = () -> false;
        }

        intakeIsTooFull = () -> false;

        intakeEventCounter = new NoOpIntakeEventCounter();

        syncMetrics = mock(SyncMetrics.class);
        shadowgraph = new ShadowGraph(time, syncMetrics, addressBook, selfId);
        // TODO load events into shadowgraph

        final GraphGenerations generations = new Generations(0, 0, 0);
        generationsSupplier = () -> generations;

        latestEventTipsetTracker =
                new LatestEventTipsetTracker(time, addressBook, selfId, AncientMode.GENERATION_THRESHOLD);
        for (final EventImpl event : knownEvents) {
            latestEventTipsetTracker.addEvent(event);
        }
    }

    /**
     * Sets the {@link FallenBehindManager} that will be used by the turbo sync runner.
     *
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withPlatformContext(@NonNull final PlatformContext platformContext) {
        this.platformContext = Objects.requireNonNull(platformContext);
        return this;
    }

    /**
     * Sets the method that signals if gossip has been halted.
     *
     * @param gossipHalted the method that signals if gossip has been halted
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withGossipHalted(@NonNull final BooleanSupplier gossipHalted) {
        this.gossipHalted = Objects.requireNonNull(gossipHalted);
        return this;
    }

    /**
     * Specify events that are known by the node at the start of the test.
     *
     * @param knownEvents the events that are known by the node at the start of the test
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withKnownEvents(@NonNull final List<EventImpl> knownEvents) {
        this.knownEvents = Objects.requireNonNull(knownEvents);
        return this;
    }

    @NonNull
    public TurboSyncRunner build() {
        buildSubsystems();

        return new TurboSyncRunner(
                platformContext,
                addressBook,
                selfId,
                peerId,
                fallenBehindManager,
                gossipHalted,
                intakeIsTooFull,
                intakeEventCounter,
                null, // TODO
                null, // TODO
                shadowgraph,
                generationsSupplier,
                null,
                null,
                syncMetrics);
    }
}
