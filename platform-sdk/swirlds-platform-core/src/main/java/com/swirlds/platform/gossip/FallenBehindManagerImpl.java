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

package com.swirlds.platform.gossip;

import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.system.status.actions.FallenBehindAction;
import com.swirlds.platform.network.RandomGraph;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe implementation of {@link FallenBehindManager}
 */
public class FallenBehindManagerImpl implements FallenBehindManager {
    /**
     * a set of all neighbors of this node
     */
    private final HashSet<NodeId> allNeighbors;
    /**
     * the number of neighbors we have
     */
    private final int numNeighbors;
    /**
     * set of neighbors who report that this node has fallen behind
     */
    private final HashSet<NodeId> reportFallenBehind;
    /**
     * set of neighbors that have not yet reported that we have fallen behind, only exists if someone reports we have
     * fallen behind. This Set is made from a ConcurrentHashMap, so it needs no synchronization
     */
    private final Set<NodeId> notYetReportFallenBehind;

    /**
     * Enables submitting platform status actions
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * Called when the status becomes fallen behind
     */
    private final Runnable fallenBehindCallback;

    private final ReconnectConfig config;
    /**
     * number of neighbors who think this node has fallen behind
     */
    volatile int numReportFallenBehind;

    public FallenBehindManagerImpl(
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final RandomGraph connectionGraph,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final Runnable fallenBehindCallback,
            @NonNull final ReconnectConfig config) {
        Objects.requireNonNull(addressBook, "addressBook");
        Objects.requireNonNull(selfId, "selfId");
        Objects.requireNonNull(connectionGraph, "connectionGraph");

        notYetReportFallenBehind = ConcurrentHashMap.newKeySet();
        reportFallenBehind = new HashSet<>();
        allNeighbors = new HashSet<>();
        /* an array with all the neighbor ids */
        final int[] neighbors = connectionGraph.getNeighbors(addressBook.getIndexOfNodeId(selfId));
        numNeighbors = neighbors.length;
        for (final int neighbor : neighbors) {
            allNeighbors.add(addressBook.getNodeId(neighbor));
        }
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
        this.fallenBehindCallback =
                Objects.requireNonNull(fallenBehindCallback, "fallenBehindCallback must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public synchronized void reportFallenBehind(final NodeId id) {
        final boolean previouslyFallenBehind = hasFallenBehind();
        if (reportFallenBehind.add(id)) {
            if (numReportFallenBehind == 0) {
                // we have received the first indication that we have fallen behind, so we need to check with other
                // nodes to confirm
                notYetReportFallenBehind.addAll(allNeighbors);
            }
            // we don't need to check with this node
            notYetReportFallenBehind.remove(id);
            numReportFallenBehind++;
            if (!previouslyFallenBehind && hasFallenBehind()) {
                statusActionSubmitter.submitStatusAction(new FallenBehindAction());
                fallenBehindCallback.run();
            }
        }
    }

    @Override
    public List<NodeId> getNeededForFallenBehind() {
        if (notYetReportFallenBehind.isEmpty()) {
            return null;
        }
        final List<NodeId> ret = new ArrayList<>(notYetReportFallenBehind);
        Collections.shuffle(ret);
        return ret;
    }

    @Override
    public boolean hasFallenBehind() {
        return numNeighbors * config.fallenBehindThreshold() < numReportFallenBehind;
    }

    @Override
    public synchronized List<NodeId> getNeighborsForReconnect() {
        final List<NodeId> ret = new ArrayList<>(reportFallenBehind);
        Collections.shuffle(ret);
        return ret;
    }

    @Override
    public boolean shouldReconnectFrom(final NodeId peerId) {
        if (!hasFallenBehind()) {
            return false;
        }
        synchronized (this) {
            // if this neighbor has told me I have fallen behind, I will reconnect with him
            return reportFallenBehind.contains(peerId);
        }
    }

    @Override
    public synchronized void resetFallenBehind() {
        numReportFallenBehind = 0;
        reportFallenBehind.clear();
        notYetReportFallenBehind.clear();
    }

    @Override
    public int numReportedFallenBehind() {
        return numReportFallenBehind;
    }
}
