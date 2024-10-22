/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.metrics;

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.IntegerGauge;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metrics for ISS events.
 */
public class IssMetrics {

    private final AddressBook addressBook;

    /**
     * The current number of nodes experiencing an ISS.
     */
    private int issCount;

    /**
     * The highest round number observed so far.
     */
    private long highestRound = Long.MIN_VALUE;

    /**
     * The metric for the current number of nodes in an ISS state.
     */
    private final IntegerGauge issCountGauge;

    /**
     * The current total weight tied up by ISS events.
     */
    private long issWeight;

    /**
     * The metric for the current weight tied up by ISS events.
     */
    private final LongGauge issWeightGage;

    /**
     * The ISS status of a node.
     */
    private static class IssStatus {
        private boolean hasIss = false;
        private long round = Long.MIN_VALUE;

        /**
         * Check if this node is currently experiencing an ISS.
         */
        public boolean hasIss() {
            return hasIss;
        }

        /**
         * Set if this node is currently experiencing an ISS.
         */
        public void setHasIss(final boolean hasIss) {
            this.hasIss = hasIss;
        }

        /**
         * Get the round number that last reported the ISS status.
         */
        public long getRound() {
            return round;
        }

        /**
         * Set the round number that last reported the ISS status.
         */
        public void setRound(final long round) {
            this.round = round;
        }
    }

    /**
     * The current ISS status of nodes.
     */
    private final Map<NodeId, IssStatus> issDataByNode = new HashMap<>();

    /**
     * Constructor of {@code IssMetrics}
     *
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public IssMetrics(@NonNull final Metrics metrics, @NonNull final AddressBook addressBook) {
        Objects.requireNonNull(metrics, "metrics must not be null");
        this.addressBook = Objects.requireNonNull(addressBook, "addressBook must not be null");

        issCountGauge = metrics.getOrCreate(new IntegerGauge.Config(Metrics.INTERNAL_CATEGORY, "issCount")
                .withDescription("the number of nodes that currently disagree with the consensus hash"));

        issWeightGage = metrics.getOrCreate(new LongGauge.Config(Metrics.INTERNAL_CATEGORY, "issWeight")
                .withDescription("the amount of weight tied up by ISS events"));

        for (final Address address : addressBook) {
            issDataByNode.put(address.getNodeId(), new IssStatus());
        }
    }

    /**
     * Get the current number of nodes currently thought to be in an ISS state.
     */
    public int getIssCount() {
        return issCount;
    }

    /**
     * Get the current weight currently tied up in an ISS.
     */
    public long getIssWeight() {
        return issWeight;
    }

    /**
     * Report the status of a node.
     *
     * @param round
     * 		the round number
     * @param nodeId
     * 		the ID of the node that submitted the hash
     * @param nodeHash
     * 		the hash computed by the node
     * @param consensusHash
     * 		the consensus hash computed by the network
     */
    public void stateHashValidityObserver(
            @NonNull final Long round,
            @NonNull final NodeId nodeId,
            @NonNull final Hash nodeHash,
            @NonNull final Hash consensusHash) {
        Objects.requireNonNull(round, "round must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(nodeHash, "nodeHash must not be null");
        Objects.requireNonNull(consensusHash, "consensusHash must not be null");

        final boolean hasIss = !nodeHash.equals(consensusHash);

        final IssStatus issStatus = issDataByNode.get(nodeId);
        if (issStatus == null) {
            throw new IllegalArgumentException("Node " + nodeId + " is not being tracked");
        }

        highestRound = Math.max(highestRound, round);

        if (issStatus.getRound() >= round) {
            // Don't allow old data to overwrite new data
            return;
        }
        issStatus.setRound(round);

        if (issStatus.hasIss() != hasIss) {
            final long weight = addressBook.getAddress(nodeId).getWeight();
            if (hasIss) {
                issCount++;
                issWeight += weight;
            } else {
                issCount--;
                issWeight -= weight;
            }
            issCountGauge.set(issCount);
            issWeightGage.set(issWeight);
            issStatus.setHasIss(hasIss);
        }
    }

    /**
     * Report the existence of a catastrophic ISS.
     *
     * @param round
     * 		the round of the ISS
     */
    public void catastrophicIssObserver(final long round) {
        if (round <= highestRound) {
            // Don't report old data
            return;
        }

        highestRound = round;
        for (final IssStatus status : issDataByNode.values()) {
            status.setHasIss(true);
            status.setRound(round);
        }

        issCount = addressBook.getSize();
        issWeight = addressBook.getTotalWeight();

        issCountGauge.set(issCount);
        issWeightGage.set(issWeight);
    }
}
