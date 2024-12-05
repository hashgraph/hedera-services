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

package com.hedera.node.app.service.contract.impl.exec.metrics;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Metrics collection management for Smart Contracts service
 *
 * Includes:
 * * Rejected transactions counters: Transactions which failed `pureCheck` for one reason or another
 */
public class ContractMetrics {

    private static final Logger log = LogManager.getLogger(ContractMetrics.class);

    private final Supplier<Metrics> metricsSupplier;
    private final Supplier<ContractsConfig> contractsConfigSupplier;
    private boolean p1MetricsEnabled;
    private boolean p2MetricsEnabled;

    private final HashMap<HederaFunctionality, Counter> rejectedTxsCounters = new HashMap<>();
    private final HashMap<HederaFunctionality, Counter> rejectedTxsLackingIntrinsicGas = new HashMap<>();
    private Counter rejectedEthType3Counter;

    private static final Map<HederaFunctionality, String> POSSIBLE_FAILING_TX_TYPES = Map.of(
            CONTRACT_CALL, "contractCallTx", CONTRACT_CREATE, "contractCreateTx", ETHEREUM_TRANSACTION, "ethereumTx");

    private static final String METRIC_CATEGORY = "app";
    private static final String METRIC_SERVICE = "SmartContractService";
    private static final String METRIC_TXN_UNIT = "txs";

    // Templates:  %1$s - HederaFunctionality name
    //             %2$s - METRIC_SERVICE
    //             %3$s - short specific metric description

    private static final String REJECTED_NAME_TEMPLATE = "%2$s:Rejected-%1$s_total";
    private static final String REJECTED_DESCR_TEMPLATE = "submitted %1$s %3$s rejected by pureChecks";

    private static final String REJECTED_TXN_SHORT_DESCR = "txns";
    private static final String REJECTED_TYPE3_SHORT_DESCR = "Ethereum Type 3 txns";
    private static final String REJECTED_FOR_GAS_SHORT_DESCR = "txns with not even intrinsic gas";

    private static final String REJECTED_TYPE3_FUNCTIONALITY = "ethType3BlobTransaction";

    @Inject
    public ContractMetrics(
            @NonNull final Supplier<Metrics> metricsSupplier,
            @NonNull final Supplier<ContractsConfig> contractsConfigSupplier) {
        this.metricsSupplier = requireNonNull(
                metricsSupplier, "metrics supplier (from platform via ServicesMain/Hedera must not be null");
        this.contractsConfigSupplier =
                requireNonNull(contractsConfigSupplier, "contracts configuration supplier must not be null");
    }

    public void createContractMetrics() {

        final var contractsConfig = requireNonNull(contractsConfigSupplier.get());
        this.p1MetricsEnabled = contractsConfig.metricsSmartContractPrimaryEnabled();
        this.p2MetricsEnabled = contractsConfig.metricsSmartContractSecondaryEnabled();

        final var metrics = requireNonNull(metricsSupplier.get());

        if (p1MetricsEnabled) {
            // Rejected transactions counters
            for (final var txKind : POSSIBLE_FAILING_TX_TYPES.keySet()) {
                final var name = toRejectedName(txKind, REJECTED_TXN_SHORT_DESCR);
                final var descr = toRejectedDescr(txKind, REJECTED_TXN_SHORT_DESCR);
                final var config = new Counter.Config(METRIC_CATEGORY, name)
                        .withDescription(descr)
                        .withUnit(METRIC_TXN_UNIT);
                final var metric = newCounter(metrics, config);
                rejectedTxsCounters.put(txKind, metric);
            }

            // Rejected transactions because they don't even have intrinsic gas
            for (final var txKind : POSSIBLE_FAILING_TX_TYPES.keySet()) {
                final var functionalityName = POSSIBLE_FAILING_TX_TYPES.get(txKind) + "DueToIntrinsicGas";
                final var name = toRejectedName(functionalityName, REJECTED_FOR_GAS_SHORT_DESCR);
                final var descr = toRejectedDescr(functionalityName, REJECTED_FOR_GAS_SHORT_DESCR);
                final var config = new Counter.Config(METRIC_CATEGORY, name)
                        .withDescription(descr)
                        .withUnit(METRIC_TXN_UNIT);
                final var metric = newCounter(metrics, config);
                rejectedTxsLackingIntrinsicGas.put(txKind, metric);
            }

            // Rejected transactions for ethereum calls that are in type 3 blob transaction format
            {
                final var name = toRejectedName(REJECTED_TYPE3_FUNCTIONALITY, REJECTED_TYPE3_SHORT_DESCR);
                final var descr = toRejectedDescr(REJECTED_TYPE3_FUNCTIONALITY, REJECTED_TYPE3_SHORT_DESCR);
                final var config = new Counter.Config(METRIC_CATEGORY, name)
                        .withDescription(descr)
                        .withUnit(METRIC_TXN_UNIT);
                final var metric = newCounter(metrics, config);
                rejectedEthType3Counter = metric;
            }
        }

        if (p2MetricsEnabled) {
            // PLACEHOLDER
        }
    }

    public void incrementRejectedTx(@NonNull final HederaFunctionality txKind) {
        bumpRejectedTx(txKind, 1);
    }

    public void bumpRejectedTx(@NonNull final HederaFunctionality txKind, final long bumpBy) {
        if (p1MetricsEnabled) requireNonNull(rejectedTxsCounters.get(txKind)).add(bumpBy);
    }

    public void incrementRejectedForGasTx(@NonNull final HederaFunctionality txKind) {
        bumpRejectedForGasTx(txKind, 1);
    }

    public void bumpRejectedForGasTx(@NonNull final HederaFunctionality txKind, final long bumpBy) {
        if (p1MetricsEnabled)
            requireNonNull(rejectedTxsLackingIntrinsicGas.get(txKind)).add(bumpBy);
    }

    public void incrementRejectedType3EthTx() {
        bumpRejectedType3EthTx(1);
    }

    public void bumpRejectedType3EthTx(final long bumpBy) {
        if (p1MetricsEnabled) rejectedEthType3Counter.add(bumpBy);
    }

    @VisibleForTesting
    public @NonNull Map<String, Long> getAllCounters() {
        return Stream.concat(
                        Stream.concat(
                                rejectedTxsCounters.values().stream(),
                                rejectedTxsLackingIntrinsicGas.values().stream()),
                        Stream.ofNullable(rejectedEthType3Counter))
                .collect(toMap(Counter::getName, Counter::get));
    }

    @VisibleForTesting
    public @NonNull List<String> getAllCounterNames() {
        return Stream.concat(
                        Stream.concat(
                                rejectedTxsCounters.values().stream(),
                                rejectedTxsLackingIntrinsicGas.values().stream()),
                        Stream.ofNullable(rejectedEthType3Counter))
                .map(Metric::getName)
                .sorted()
                .toList();
    }

    @VisibleForTesting
    public @NonNull List<String> getAllCounterDescriptions() {
        return Stream.concat(
                        Stream.concat(
                                rejectedTxsCounters.values().stream(),
                                rejectedTxsLackingIntrinsicGas.values().stream()),
                        Stream.ofNullable(rejectedEthType3Counter))
                .map(Metric::getDescription)
                .sorted()
                .toList();
    }

    @VisibleForTesting
    public @NonNull String allCountersToString() {
        return getAllCounters().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private @NonNull Counter newCounter(@NonNull final Metrics metrics, @NonNull final Counter.Config config) {
        return metrics.getOrCreate(config);
    }

    private static @NonNull String toRejectedName(
            @NonNull final HederaFunctionality functionality, @NonNull final String shortDescription) {
        return toRejectedName(POSSIBLE_FAILING_TX_TYPES.get(functionality), shortDescription);
    }

    private static @NonNull String toRejectedName(
            @NonNull final String functionality, @NonNull final String shortDescription) {
        return toString(REJECTED_NAME_TEMPLATE, functionality, shortDescription);
    }

    private static @NonNull String toRejectedDescr(
            @NonNull final HederaFunctionality functionality, @NonNull final String shortDescription) {
        return toString(REJECTED_DESCR_TEMPLATE, POSSIBLE_FAILING_TX_TYPES.get(functionality), shortDescription);
    }

    private static @NonNull String toRejectedDescr(
            @NonNull final String functionality, @NonNull final String shortDescription) {
        return toString(REJECTED_DESCR_TEMPLATE, functionality, shortDescription);
    }

    private static @NonNull String toString(
            @NonNull final String template,
            @NonNull final String functionality,
            @NonNull final String shortDescription) {
        return template.formatted(functionality, METRIC_SERVICE, shortDescription);
    }
}
