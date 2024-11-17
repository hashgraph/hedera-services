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

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class to track all the metrics related to TSS functionalities.
 */
@Singleton
public class TssMetrics {
    private static final Logger log = LogManager.getLogger(TssMetrics.class);

    private final Metrics metrics;

    private static final String TSS_MESSAGE_COUNTER_METRIC = "tss_message_total";
    private static final String TSS_MESSAGE_COUNTER_METRIC_DESC =
            "total numbers of tss message transactions for roster ";
    private final Map<Bytes, Counter> messagesPerCandidateRoster = new HashMap<>();

    private static final String TSS_VOTE_COUNTER_METRIC = "tss_vote_total";
    private static final String TSS_VOTE_COUNTER_METRIC_DESC = "total numbers of tss vote transactions for roster ";
    private final Map<Bytes, Counter> votesPerCandidateRoster = new HashMap<>();

    private static final String TSS_SHARES_AGGREGATION_TIME = "tss_shares_aggregation_time";
    private static final String TSS_SHARES_AGGREGATION_TIME_DESC =
            "the time it takes to compute shares from the key material";
    private static final LongGauge.Config TSS_SHARES_AGGREGATION_CONFIG =
            new LongGauge.Config("app", TSS_SHARES_AGGREGATION_TIME).withDescription(TSS_SHARES_AGGREGATION_TIME_DESC);
    private final LongGauge tssSharesAggregationTime;

    private static final String TSS_CANDIDATE_ROSTER_LIFECYCLE = "tss_candidate_roster_lifecycle";
    private static final String TSS_CANDIDATE_ROSTER_LIFECYCLE_DESC = "the lifecycle of the current candidate roster";
    private static final LongGauge.Config TSS_ROSTER_LIFECYCLE_CONFIG = new LongGauge.Config(
                    "app", TSS_CANDIDATE_ROSTER_LIFECYCLE)
            .withDescription(TSS_CANDIDATE_ROSTER_LIFECYCLE_DESC);

    private static final String TSS_LEDGER_SIGNATURE_TIME = "tss_ledger_signature_time";
    private static final String TSS_LEDGER_SIGNATURE_TIME_DESC =
            "the time it takes to to get ledger signature from the time it is requested";
    private static final LongGauge.Config TSS_LEDGER_SIGNATURE_TIME_CONFIG =
            new LongGauge.Config("app", TSS_LEDGER_SIGNATURE_TIME).withDescription(TSS_LEDGER_SIGNATURE_TIME_DESC);
    private final LongGauge tssLedgerSignatureTime;
    private final LongGauge tssCandidateRosterLifecycle;

    // local variable to track the start of candidate roster's lifecycle
    private Instant candidateRosterLifecycleStart = null;

    /**
     * Constructor for the TssMetrics.
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     */
    @Inject
    public TssMetrics(@NonNull final Metrics metrics) {
        this.metrics = requireNonNull(metrics, "metrics must not be null");
        tssCandidateRosterLifecycle = metrics.getOrCreate(TSS_ROSTER_LIFECYCLE_CONFIG);
        tssSharesAggregationTime = metrics.getOrCreate(TSS_SHARES_AGGREGATION_CONFIG);
        tssLedgerSignatureTime = metrics.getOrCreate(TSS_LEDGER_SIGNATURE_TIME_CONFIG);
    }

    /**
     * Track the count of messages per candidate roster.
     *
     * @param targetRosterHash the {@link Bytes} of the candidate roster
     */
    public void updateMessagesPerCandidateRoster(@NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash, "targetRosterHash must not be null");

        // if this is the first message for this candidate roster, initialize new metric to track occurrences
        if (!messagesPerCandidateRoster.containsKey(targetRosterHash)) {
            final Counter.Config TSS_MESSAGE_TX_COUNTER = new Counter.Config("app", TSS_MESSAGE_COUNTER_METRIC)
                    .withDescription(TSS_MESSAGE_COUNTER_METRIC_DESC + targetRosterHash);
            final Counter tssMessageTxCounter = metrics.getOrCreate(TSS_MESSAGE_TX_COUNTER);
            tssMessageTxCounter.increment();
            messagesPerCandidateRoster.put(targetRosterHash, tssMessageTxCounter);
        } else {
            // if the metric is already present, just increment
            getMessagesPerCandidateRoster(targetRosterHash).increment();
        }
    }

    /**
     * Track the count of votes per candidate roster.
     *
     * @param targetRosterHash the {@link Bytes} of the candidate roster
     */
    public void updateVotesPerCandidateRoster(@NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash, "targetRosterHash must not be null");

        // if this is the first vote for this candidate roster, initialize new metric to track occurrences
        if (!votesPerCandidateRoster.containsKey(targetRosterHash)) {
            final Counter.Config TSS_VOTE_TX_COUNTER = new Counter.Config("app", TSS_VOTE_COUNTER_METRIC)
                    .withDescription(TSS_VOTE_COUNTER_METRIC_DESC + targetRosterHash);
            final Counter tssVoteTxCounter = metrics.getOrCreate(TSS_VOTE_TX_COUNTER);
            tssVoteTxCounter.increment();
            votesPerCandidateRoster.put(targetRosterHash, tssVoteTxCounter);
        } else {
            // if the metric is already present, just increment
            getVotesPerCandidateRoster(targetRosterHash).increment();
        }
    }

    /**
     * Track when the vote for candidate roster is closed.
     *
     * @param rosterLifecycleEndTime the time at which the candidate roster is set
     */
    public void updateCandidateRosterLifecycle(@NonNull final Instant rosterLifecycleEndTime) {
        requireNonNull(rosterLifecycleEndTime, "rosterLifecycleEndTime must not be null");
        final long lifecycle = Duration.between(this.candidateRosterLifecycleStart, rosterLifecycleEndTime)
                .toMillis();
        tssCandidateRosterLifecycle.set(lifecycle);
    }

    /**
     * Track when the candidate roster is set.
     *
     * @param rosterLifecycleStartTime the time at which the candidate roster was set
     */
    public void trackCandidateRosterLifecycleStart(@NonNull final Instant rosterLifecycleStartTime) {
        requireNonNull(rosterLifecycleStartTime, "rosterLifecycleStartTime must not be null");
        this.candidateRosterLifecycleStart = rosterLifecycleStartTime;
    }

    /**
     * The time it takes to aggregate private shares from the key material.
     *
     * @param aggregationTime the time which it takes to compute shares from the key material
     */
    public void updateAggregationTime(final long aggregationTime) {
        if (aggregationTime < 0) {
            log.warn("Received negative aggregation time: {}", aggregationTime);
        } else {
            tssSharesAggregationTime.set(aggregationTime);
        }
    }

    /**
     * @param targetRosterHash the {@link Bytes} of the candidate roster
     * @return the metric which contains how many votes are registered for candidate roster
     */
    public @NonNull Counter getMessagesPerCandidateRoster(@NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash);
        return messagesPerCandidateRoster.get(targetRosterHash);
    }

    /**
     * @param targetRosterHash the {@link Bytes} of the candidate roster
     * @return the metric which contains how many votes are registered for candidate roster
     */
    public @NonNull Counter getVotesPerCandidateRoster(@NonNull final Bytes targetRosterHash) {
        requireNonNull(targetRosterHash);
        return votesPerCandidateRoster.get(targetRosterHash);
    }

    /**
     * The time it takes to get ledger signature from the time it is requested.
     *
     * @param time the time it takes to get ledger signature from the time it is requested
     */
    public void updateLedgerSignatureTime(final long time) {
        if (time < 0) {
            log.warn("Received negative signature time: {}", time);
        } else {
            tssLedgerSignatureTime.set(time);
        }
    }

    /**
     * @return the aggregation time from the metric
     */
    @VisibleForTesting
    public long getAggregationTime() {
        return tssSharesAggregationTime.get();
    }

    /**
     * @return the candidate roster lifecycle from the metric
     */
    @VisibleForTesting
    public long getCandidateRosterLifecycle() {
        return tssCandidateRosterLifecycle.get();
    }

    /**
     * @return the ledger signature time from the metric
     */
    @VisibleForTesting
    public long getTssLedgerSignatureTime() {
        return tssLedgerSignatureTime.get();
    }
}
