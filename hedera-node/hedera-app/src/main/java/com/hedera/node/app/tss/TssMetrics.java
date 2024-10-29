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

package com.hedera.node.app.tss;

import static com.hedera.hapi.node.base.HederaFunctionality.TSS_MESSAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.TSS_VOTE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A class to track all the metrics related to TSS functionalities.
 */
@Singleton
public class TssMetrics {
    private static final String TSS_CANDIDATE_ROSTER_LIFECYCLE = "tss_candidate_roster_lifecycle";
    private static final String TSS_CANDIDATE_ROSTER_LIFECYCLE_DESC = "the lifecycle of the current candidate roster";
    private static final LongGauge.Config TSS_ROSTER_LIFECYCLE_CONFIG = new LongGauge.Config(
                    "app", TSS_CANDIDATE_ROSTER_LIFECYCLE)
            .withDescription(TSS_CANDIDATE_ROSTER_LIFECYCLE_DESC);
    private final LongGauge tssCandidateRosterLifecycle;

    private static final String TSS_MESSAGE_COUNTER_METRIC = "tss_message_total";
    private static final String TSS_MESSAGE_COUNTER_METRIC_DESC = "total numbers of tss message transactions";
    private static final Counter.Config TSS_MESSAGE_TX_COUNTER =
            new Counter.Config("app", TSS_MESSAGE_COUNTER_METRIC).withDescription(TSS_MESSAGE_COUNTER_METRIC_DESC);
    private final Counter tssMessageTxCounter;

    private static final String TSS_VOTE_COUNTER_METRIC = "tss_vote_total";
    private static final String TSS_VOTE_COUNTER_METRIC_DESC = "total numbers of tss vote transactions";
    private static final Counter.Config TSS_VOTE_TX_COUNTER =
            new Counter.Config("app", TSS_VOTE_COUNTER_METRIC).withDescription(TSS_VOTE_COUNTER_METRIC_DESC);
    private final Counter tssVoteTxCounter;

    private static final String TSS_SHARES_AGGREGATION_TIME = "tss_shares_aggregation_time";
    private static final String TSS_SHARES_AGGREGATION_TIME_DESC =
            "the time it takes to compute shares from the key material";
    private static final LongGauge.Config TSS_SHARES_AGGREGATION_CONFIG =
            new LongGauge.Config("app", TSS_SHARES_AGGREGATION_TIME).withDescription(TSS_SHARES_AGGREGATION_TIME_DESC);
    private final LongGauge tssSharesAggregationTime;

    private final long tssSharesAggregationStart = 0L;

    /**
     * Constructor for the TssMetrics.
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     */
    @Inject
    public TssMetrics(@NonNull final Metrics metrics) {
        requireNonNull(metrics, "metrics must not be null");

        tssCandidateRosterLifecycle = metrics.getOrCreate(TSS_ROSTER_LIFECYCLE_CONFIG);
        tssSharesAggregationTime = metrics.getOrCreate(TSS_SHARES_AGGREGATION_CONFIG);
        tssMessageTxCounter = metrics.getOrCreate(TSS_MESSAGE_TX_COUNTER);
        tssVoteTxCounter = metrics.getOrCreate(TSS_VOTE_TX_COUNTER);
    }

    /**
     * Increment counter metrics for TssVote or TssMessage transactions.
     *
     * @param functionality the TSS {@link HederaFunctionality} for which the metrics will be updated
     */
    public void updateTssTransactionMetrics(@NonNull final HederaFunctionality functionality) {
        requireNonNull(functionality, "functionality must not be null");
        if (functionality == HederaFunctionality.NONE) {
            return;
        }

        if (functionality == TSS_MESSAGE) {
            tssMessageTxCounter.increment();
        } else if (functionality == TSS_VOTE) {
            tssVoteTxCounter.increment();
        }
    }

    /**
     * Track when the candidate roster is set.
     *
     * @param lifecycle the time at which the candidate roster is set
     */
    public void updateCandidateRosterLifecycle(final long lifecycle) {
        if (lifecycle <= 0) throw new IllegalArgumentException("Candidate roster lifecycle must be positive");
        tssCandidateRosterLifecycle.set(lifecycle);
    }

    /**
     * The time it takes to aggregate private shares from the key material.
     *
     * @param aggregationTime the time which it takes to compute shares from the key material
     */
    public void updateAggregationTime(final long aggregationTime) {
        if (aggregationTime <= 0)
            throw new IllegalArgumentException("Private shares aggregation time must be positive");
        tssSharesAggregationTime.set(aggregationTime);
    }

    public void trackSharesAggregationStartTime(final long aggregationStartTime) {
        this.tssSharesAggregationTime.set(aggregationStartTime);
    }
}
