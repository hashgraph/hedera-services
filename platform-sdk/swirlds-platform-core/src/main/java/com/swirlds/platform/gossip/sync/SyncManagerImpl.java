/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.logging.legacy.LogMarker.FREEZE;
import static com.swirlds.metrics.api.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.FallenBehindManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that manages information about who we need to sync with, and whether we need to reconnect
 */
public class SyncManagerImpl implements FallenBehindManager {

    private static final Logger logger = LogManager.getLogger(SyncManagerImpl.class);

    private final EventConfig eventConfig;

    /**
     * Supplies the event intake queue size.
     */
    private final LongSupplier intakeQueueSizeSupplier;

    /** This object holds data on how nodes are connected to each other. */
    private final FallenBehindManager fallenBehindManager;

    /**
     * True if gossip has been artificially halted.
     */
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);

    /**
     * Creates a new SyncManager
     *
     * @param platformContext         the platform context
     * @param intakeQueueSizeSupplier a supplier for the size of the event intake queue
     * @param fallenBehindManager     the fallen behind manager
     * @param eventConfig             the event config
     */
    public SyncManagerImpl(
            @NonNull final PlatformContext platformContext,
            @NonNull final LongSupplier intakeQueueSizeSupplier,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final EventConfig eventConfig) {

        this.intakeQueueSizeSupplier = Objects.requireNonNull(intakeQueueSizeSupplier);

        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.eventConfig = Objects.requireNonNull(eventConfig);

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                INTERNAL_CATEGORY, "hasFallenBehind", Object.class, this::hasFallenBehind)
                        .withDescription("has this node fallen behind?"));
        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                INTERNAL_CATEGORY,
                                "numReportFallenBehind",
                                Integer.class,
                                this::numReportedFallenBehind)
                        .withDescription("the number of nodes that have fallen behind")
                        .withUnit("count"));
    }

    /**
     * A method called by the sync listener to determine whether a sync should be accepted or not
     *
     * @return true if the sync should be accepted, false otherwise
     */
    public boolean shouldAcceptSync() {
        // don't gossip if halted
        if (gossipHalted.get()) {
            return false;
        }

        // we shouldn't sync if the event intake queue is too big
        final long intakeQueueSize = intakeQueueSizeSupplier.getAsLong();
        if (intakeQueueSize > eventConfig.eventIntakeQueueThrottleSize()) {
            return false;
        }
        return true;
    }

    /**
     * A method called by the sync caller to determine whether a sync should be initiated or not
     *
     * @return true if the sync should be initiated, false otherwise
     */
    public boolean shouldInitiateSync() {
        // don't gossip if halted
        if (gossipHalted.get()) {
            return false;
        }

        // we shouldn't sync if the event intake queue is too big
        return intakeQueueSizeSupplier.getAsLong() <= eventConfig.eventIntakeQueueThrottleSize();
    }

    /**
     * Observers halt requested dispatches. Causes gossip to permanently stop (until node reboot).
     *
     * @param reason the reason why gossip is being stopped
     */
    public void haltRequestedObserver(final String reason) {
        gossipHalted.set(true);
        logger.info(FREEZE.getMarker(), "Gossip frozen, reason: {}", reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportFallenBehind(final NodeId id) {
        fallenBehindManager.reportFallenBehind(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetFallenBehind() {
        fallenBehindManager.resetFallenBehind();
    }

    @Override
    public List<NodeId> getNeededForFallenBehind() {
        return fallenBehindManager.getNeededForFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFallenBehind() {
        return fallenBehindManager.hasFallenBehind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<NodeId> getNeighborsForReconnect() {
        return fallenBehindManager.getNeighborsForReconnect();
    }

    @Override
    public boolean shouldReconnectFrom(final NodeId peerId) {
        return fallenBehindManager.shouldReconnectFrom(peerId);
    }

    @Override
    public int numReportedFallenBehind() {
        return fallenBehindManager.numReportedFallenBehind();
    }
}
