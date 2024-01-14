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

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.gossip.shadowgraph.LatestEventTipsetTracker;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.sync.turbo.TurboSyncRunner;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Configures and builds a simulated node for testing turbo sync.
 */
public class TurboSyncTestNodeBuilder {

    private final FakeTime time;

    private PlatformContext platformContext;
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final NodeId peerId;

    private List<EventImpl> knownEvents = List.of();

    private BooleanSupplier gossipHalted;

    private final FallenBehindManager fallenBehindManager;
    private final BooleanSupplier intakeIsTooFull;
    private final IntakeEventCounter intakeEventCounter;
    private final SyncMetrics syncMetrics;
    private final ShadowGraph shadowgraph;
    private final Supplier<GraphGenerations> generationsSupplier;
    private final LatestEventTipsetTracker latestEventTipsetTracker;
    private InterruptableConsumer<GossipEvent> eventConsumer;
    private final ParallelExecutor executor;
    private final Connection connection;

    private final Instant startingTime;
    private Duration simulationInterval = Duration.ofMillis(100);
    private Duration simulationDuration = Duration.ofSeconds(5);

    /**
     * Constructor.
     *
     * @param startingTime the time that the test starts at
     * @param addressBook  the address book
     * @param selfId       the id of this node
     * @param peerId       the id of the peer we are syncing with
     */
    public TurboSyncTestNodeBuilder(
            @NonNull final Instant startingTime,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final NodeId peerId,
            @NonNull final Connection connection) {

        this.startingTime = Objects.requireNonNull(startingTime);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.peerId = Objects.requireNonNull(peerId);
        this.connection = Objects.requireNonNull(connection);

        fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.hasFallenBehind()).thenReturn(false);
        intakeIsTooFull = () -> false;
        intakeEventCounter = new NoOpIntakeEventCounter();

        time = new FakeTime(startingTime, Duration.ZERO);

        syncMetrics = mock(SyncMetrics.class);
        shadowgraph = new ShadowGraph(time, syncMetrics, addressBook, selfId);
        final GraphGenerations generations = new Generations(0, 0, 0);
        generationsSupplier = () -> generations;
        latestEventTipsetTracker =
                new LatestEventTipsetTracker(time, addressBook, selfId, AncientMode.GENERATION_THRESHOLD);
        executor = new TurboSyncTestExecutor();
    }

    /**
     * Setup default values for any parameters that were not provided and load events into data structures.
     */
    private void setupSubsystems() {
        if (platformContext == null) {
            platformContext = TestPlatformContextBuilder.create().withTime(time).build();
        }

        if (eventConsumer == null) {
            eventConsumer = event -> {};
        }

        for (final EventImpl event : knownEvents) {
            shadowgraph.addEvent(event);
        }
        for (final EventImpl event : knownEvents) {
            latestEventTipsetTracker.addEvent(event);
        }

        // During this test we hijack the gossipHalted method. This method is called once per sync iteration, and has
        // the ability to cause syncing to stop. We step forward in time by this interval during each sync iteration.
        final Instant endingTime = startingTime.plus(simulationDuration);
        gossipHalted = () -> {
            final Instant now = time.now();
            time.tick(simulationInterval);
            return now.isAfter(endingTime);
        };
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
     * Specify events that are known by the node at the start of the test.
     *
     * @param knownEvents the events that are known by the node at the start of the test
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withKnownEvents(@NonNull final List<EventImpl> knownEvents) {
        this.knownEvents = Objects.requireNonNull(knownEvents);
        return this;
    }

    /**
     * New events are passed to this method.
     *
     * @param eventConsumer the method that consumes new events
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withEventConsumer(@NonNull final InterruptableConsumer<GossipEvent> eventConsumer) {
        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        return this;
    }

    /**
     * Set the simulated time that passes during each sync iteration.
     *
     * @param simulationInterval the time that passes during each sync iteration
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withSimulationInterval(@NonNull final Duration simulationInterval) {
        this.simulationInterval = Objects.requireNonNull(simulationInterval);
        return this;
    }

    /**
     * Set the total simulated duration of the simulation.
     *
     * @param simulationDuration the total duration of the simulation
     * @return this builder
     */
    public TurboSyncTestNodeBuilder withSimulationDuration(@NonNull final Duration simulationDuration) {
        this.simulationDuration = Objects.requireNonNull(simulationDuration);
        return this;
    }

    /**
     * Build a turbo sync runner.
     *
     * @return a turbo sync runner
     */
    @NonNull
    public TurboSyncRunner build() {
        setupSubsystems();

        return new TurboSyncRunner(
                platformContext,
                addressBook,
                selfId,
                peerId,
                fallenBehindManager,
                gossipHalted,
                intakeIsTooFull,
                intakeEventCounter,
                connection,
                executor,
                shadowgraph,
                generationsSupplier,
                latestEventTipsetTracker,
                eventConsumer,
                syncMetrics);
    }
}
