/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip;

import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.FallenBehindAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Objects;

/**
 * A thread-safe implementation of {@link FallenBehindManager}
 */
public class FallenBehindManagerImpl implements FallenBehindManager {

    /**
     * the number of neighbors we have
     */
    private final int numNeighbors;
    /**
     * set of neighbors who report that this node has fallen behind
     */
    private final HashSet<NodeId> neighborsReportingWeAreBehind;

    /**
     * Enables submitting platform status actions
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * Called when the status becomes fallen behind
     */
    private final Runnable fallenBehindCallback;

    private final ReconnectConfig config;

    public FallenBehindManagerImpl(
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final RandomGraph connectionGraph,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Runnable fallenBehindCallback,
            @NonNull final ReconnectConfig config) {
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(connectionGraph);

        neighborsReportingWeAreBehind = new HashSet<>();
        /* an array with all the neighbor ids */
        final int[] neighbors = connectionGraph.getNeighbors(addressBook.getIndexOfNodeId(selfId));
        numNeighbors = neighbors.length;
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.fallenBehindCallback = Objects.requireNonNull(fallenBehindCallback);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void reportFallenBehind(@NonNull final NodeId peerId) {
        final boolean previouslyFallenBehind = hasFallenBehind();

        if (neighborsReportingWeAreBehind.add(peerId)) {
            if (!previouslyFallenBehind && hasFallenBehind()) {
                statusActionSubmitter.submitStatusAction(new FallenBehindAction());
                fallenBehindCallback.run();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void reportNotFallenBehind(@NonNull final NodeId peerId) {
        neighborsReportingWeAreBehind.remove(peerId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean hasFallenBehind() {
        return numNeighbors * config.fallenBehindThreshold() < neighborsReportingWeAreBehind.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean shouldReconnectFrom(@NonNull final NodeId peerId) {
        if (!hasFallenBehind()) {
            return false;
        }
        // if this neighbor has told me I have fallen behind, I will reconnect with him
        return neighborsReportingWeAreBehind.contains(peerId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void resetFallenBehind() {
        neighborsReportingWeAreBehind.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int numReportedFallenBehind() {
        return neighborsReportingWeAreBehind.size();
    }
}
