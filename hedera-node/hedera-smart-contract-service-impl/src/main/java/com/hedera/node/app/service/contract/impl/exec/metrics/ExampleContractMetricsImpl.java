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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.ExampleContractMetrics;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Metrics collection management for Smart Contracts service
 */
public class ExampleContractMetricsImpl implements ExampleContractMetrics {
    private static final Logger log = LogManager.getLogger(ExampleContractMetricsImpl.class);

    private static final String METRIC_CATEGORY = "app";
    private static final String METRIC_TXN_UNIT = "txs";
    private static final String METRIC_NAME_TEMPLATE =
            METRIC_CATEGORY + "_" + ContractService.NAME + "_%1$s_pureCheckInvocations_total";
    private static final String METRIC_DESCR_TEMPLATE = "Count of %1$s transaction types submitted to `pureCheck`";

    // Mapping of transaction type -> counter for that transaction type
    private final HashMap<HederaFunctionality, Counter> txnCounters = new HashMap<>();

    /**
     * Constructor
     *
     * @param metrics the instance to create this service's metrics with
     */
    public ExampleContractMetricsImpl(@NonNull final Metrics metrics) {
        // Instantiate metric for counting invocations of `CONTRACT_CALL` types
        final var contractCallMetricName = METRIC_NAME_TEMPLATE.formatted(CONTRACT_CALL.protoName());
        final var contractCallDescr = METRIC_DESCR_TEMPLATE.formatted(CONTRACT_CALL);
        final var contractCallConfig = new Counter.Config(METRIC_CATEGORY, contractCallMetricName)
                .withDescription(contractCallDescr)
                .withUnit(METRIC_TXN_UNIT);
        txnCounters.put(CONTRACT_CALL, metrics.getOrCreate(contractCallConfig));

        // Instantiate metric for counting invocations of `CONTRACT_CREATE`
        final var createContractMetricName = METRIC_NAME_TEMPLATE.formatted(CONTRACT_CREATE.protoName());
        final var createContractMetricDescr = METRIC_DESCR_TEMPLATE.formatted(CONTRACT_CREATE);
        final var createContractConfig = new Counter.Config(METRIC_CATEGORY, createContractMetricName)
                .withDescription(createContractMetricDescr)
                .withUnit(METRIC_TXN_UNIT);
        txnCounters.put(CONTRACT_CREATE, metrics.getOrCreate(createContractConfig));
    }

    @Override
    public void incrementExampleCounter(@NonNull final HederaFunctionality txnType) {
        // Demonstrate the example counter is invoked
        txnCounters.get(txnType).add(1);
        log.info(
                "Example counter invoked. Counter at {}",
                txnCounters.get(txnType).get());
    }
}
