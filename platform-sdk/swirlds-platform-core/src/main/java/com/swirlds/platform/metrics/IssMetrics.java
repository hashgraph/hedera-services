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

package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.error.CatastrophicIssTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashValidityTrigger;
import java.util.HashMap;
import java.util.Map;

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
     * The current total stake tied up by ISS events.
     */
    private long issStake;

    /**
     * The metric for the current stake tied up by ISS events.
     */
    private final LongGauge issStakeGage;

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
    private final Map<Long /* node ID */, IssStatus> issDataByNode = new HashMap<>();

    /**
     * Constructor of {@code IssMetrics}
     *
     * @param metrics
     * 		a reference to the metrics-system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public IssMetrics(final Metrics metrics, final AddressBook addressBook) {
        CommonUtils.throwArgNull(metrics, "metrics");
        this.addressBook = CommonUtils.throwArgNull(addressBook, "addressBook");

        issCountGauge = metrics.getOrCreate(new IntegerGauge.Config(INTERNAL_CATEGORY, "issCount")
                .withDescription("the number of nodes that currently disagree with the consensus hash"));

        issStakeGage = metrics.getOrCreate(new LongGauge.Config(INTERNAL_CATEGORY, "issStake")
                .withDescription("the amount of stake tied up by ISS events"));

        for (final Address address : addressBook) {
            issDataByNode.put(address.getId(), new IssStatus());
        }
    }

    /**
     * Get the current number of nodes currently thought to be in an ISS state.
     */
    public int getIssCount() {
        return issCount;
    }

    /**
     * Get the current stake currently tied up in an ISS.
     */
    public long getIssStake() {
        return issStake;
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
    @Observer(StateHashValidityTrigger.class)
    public void stateHashValidityObserver(
            final Long round, final Long nodeId, final Hash nodeHash, final Hash consensusHash) {

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
            final long stake = addressBook.getAddress(nodeId).getStake();
            if (hasIss) {
                issCount++;
                issStake += stake;
            } else {
                issCount--;
                issStake -= stake;
            }
            issCountGauge.set(issCount);
            issStakeGage.set(issStake);
            issStatus.setHasIss(hasIss);
        }
    }

    /**
     * Report the existence of a catastrophic ISS.
     *
     * @param round
     * 		the round of the ISS
     * @param selfStateHash
     * 		the hash computed by this node
     */
    @Observer(CatastrophicIssTrigger.class)
    public void catastrophicIssObserver(final Long round, final Hash selfStateHash) {

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
        issStake = addressBook.getTotalStake();

        issCountGauge.set(issCount);
        issStakeGage.set(issStake);
    }
}
