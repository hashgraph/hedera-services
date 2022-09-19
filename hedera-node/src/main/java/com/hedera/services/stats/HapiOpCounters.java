/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_ANSWERED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_ANSWERED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_DEPRECATED_TXNS_NAME;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_HANDLED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_HANDLED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_DEPRECATED_DESC;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_RECEIVED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_SUBMITTED_DESC_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.COUNTER_SUBMITTED_NAME_TPL;
import static com.hedera.services.stats.ServicesStatsConfig.IGNORED_FUNCTIONS;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;
import static com.hedera.services.utils.MiscUtils.QUERY_FUNCTIONS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.TransactionContext;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Counter.Config;
import com.swirlds.common.system.Platform;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class HapiOpCounters {
    static Supplier<HederaFunctionality[]> allFunctions =
            HederaFunctionality.class::getEnumConstants;
    private final MiscRunningAvgs runningAvgs;
    private final TransactionContext txnCtx;
    private final Function<HederaFunctionality, String> statNameFn;

    private final EnumMap<HederaFunctionality, Counter> receivedOps =
            new EnumMap<>(HederaFunctionality.class);
    private final EnumMap<HederaFunctionality, Counter> handledTxns =
            new EnumMap<>(HederaFunctionality.class);
    private final EnumMap<HederaFunctionality, Counter> submittedTxns =
            new EnumMap<>(HederaFunctionality.class);
    private final EnumMap<HederaFunctionality, Counter> answeredQueries =
            new EnumMap<>(HederaFunctionality.class);
    private Counter deprecatedTxns;

    private EnumMap<HederaFunctionality, Counter.Config> receivedOpsConfig =
            new EnumMap<>(HederaFunctionality.class);
    private EnumMap<HederaFunctionality, Counter.Config> handledTxnsConfig =
            new EnumMap<>(HederaFunctionality.class);
    private EnumMap<HederaFunctionality, Counter.Config> submittedTxnsConfig =
            new EnumMap<>(HederaFunctionality.class);
    private EnumMap<HederaFunctionality, Counter.Config> answeredQueriesConfig =
            new EnumMap<>(HederaFunctionality.class);
    private Counter.Config deprecatedTxnsConfig;

    public HapiOpCounters(
            final MiscRunningAvgs runningAvgs,
            final TransactionContext txnCtx,
            final Function<HederaFunctionality, String> statNameFn) {
        this.txnCtx = txnCtx;
        this.statNameFn = statNameFn;
        this.runningAvgs = runningAvgs;

        Arrays.stream(allFunctions.get())
                .filter(function -> !IGNORED_FUNCTIONS.contains(function))
                .forEach(
                        function -> {
                            receivedOpsConfig.put(
                                    function,
                                    counterConfigFor(
                                            function,
                                            COUNTER_RECEIVED_NAME_TPL,
                                            COUNTER_RECEIVED_DESC_TPL));
                            if (QUERY_FUNCTIONS.contains(function)) {
                                answeredQueriesConfig.put(
                                        function,
                                        counterConfigFor(
                                                function,
                                                COUNTER_ANSWERED_NAME_TPL,
                                                COUNTER_ANSWERED_DESC_TPL));
                            } else {
                                submittedTxnsConfig.put(
                                        function,
                                        counterConfigFor(
                                                function,
                                                COUNTER_SUBMITTED_NAME_TPL,
                                                COUNTER_SUBMITTED_DESC_TPL));
                                handledTxnsConfig.put(
                                        function,
                                        counterConfigFor(
                                                function,
                                                COUNTER_HANDLED_NAME_TPL,
                                                COUNTER_HANDLED_DESC_TPL));
                            }
                        });
        deprecatedTxnsConfig =
                new Config(STAT_CATEGORY, COUNTER_DEPRECATED_TXNS_NAME)
                        .withDescription(COUNTER_RECEIVED_DEPRECATED_DESC);
    }

    private Counter.Config counterConfigFor(
            final HederaFunctionality function, final String nameTpl, final String descTpl) {
        final var baseName = statNameFn.apply(function);
        return new Counter.Config(STAT_CATEGORY, String.format(nameTpl, baseName))
                .withDescription(String.format(descTpl, baseName));
    }

    public void registerWith(final Platform platform) {
        registerCounters(platform, receivedOps, receivedOpsConfig);
        registerCounters(platform, submittedTxns, submittedTxnsConfig);
        registerCounters(platform, handledTxns, handledTxnsConfig);
        registerCounters(platform, answeredQueries, answeredQueriesConfig);
        deprecatedTxns = platform.getOrCreateMetric(deprecatedTxnsConfig);

        receivedOpsConfig = null;
        submittedTxnsConfig = null;
        handledTxnsConfig = null;
        answeredQueriesConfig = null;
        deprecatedTxnsConfig = null;
    }

    private void registerCounters(
            final Platform platform,
            final Map<HederaFunctionality, Counter> counters,
            final Map<HederaFunctionality, Counter.Config> configs) {
        configs.forEach(
                (function, config) -> counters.put(function, platform.getOrCreateMetric(config)));
    }

    public void countReceived(final HederaFunctionality op) {
        safeIncrement(receivedOps, op);
    }

    public long receivedSoFar(final HederaFunctionality op) {
        return IGNORED_FUNCTIONS.contains(op) ? 0 : receivedOps.get(op).get();
    }

    public void countSubmitted(final HederaFunctionality txn) {
        safeIncrement(submittedTxns, txn);
    }

    public long submittedSoFar(final HederaFunctionality txn) {
        return IGNORED_FUNCTIONS.contains(txn) ? 0 : submittedTxns.get(txn).get();
    }

    public void countHandled(final HederaFunctionality txn) {
        safeIncrement(handledTxns, txn);
        if (txn == ConsensusSubmitMessage) {
            int txnBytes = txnCtx.accessor().getTxn().getSerializedSize();
            runningAvgs.recordHandledSubmitMessageSize(txnBytes);
        }
    }

    public long handledSoFar(final HederaFunctionality txn) {
        return IGNORED_FUNCTIONS.contains(txn) ? 0 : handledTxns.get(txn).get();
    }

    public void countAnswered(final HederaFunctionality query) {
        safeIncrement(answeredQueries, query);
    }

    public long answeredSoFar(final HederaFunctionality query) {
        return IGNORED_FUNCTIONS.contains(query) ? 0 : answeredQueries.get(query).get();
    }

    private void safeIncrement(
            final Map<HederaFunctionality, Counter> counters, final HederaFunctionality function) {
        if (!IGNORED_FUNCTIONS.contains(function)) {
            counters.get(function).increment();
        }
    }

    public void countDeprecatedTxnReceived() {
        deprecatedTxns.increment();
    }

    public long receivedDeprecatedTxnSoFar() {
        return deprecatedTxns.get();
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, Counter> getReceivedOps() {
        return receivedOps;
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, Counter> getHandledTxns() {
        return handledTxns;
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, Counter> getSubmittedTxns() {
        return submittedTxns;
    }

    @VisibleForTesting
    EnumMap<HederaFunctionality, Counter> getAnsweredQueries() {
        return answeredQueries;
    }

    @VisibleForTesting
    Counter getDeprecatedTxns() {
        return deprecatedTxns;
    }

    @VisibleForTesting
    static void setAllFunctions(Supplier<HederaFunctionality[]> allFunctions) {
        HapiOpCounters.allFunctions = allFunctions;
    }
}
