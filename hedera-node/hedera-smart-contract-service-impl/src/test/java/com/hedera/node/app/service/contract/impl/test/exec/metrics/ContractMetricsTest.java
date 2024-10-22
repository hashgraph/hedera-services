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

package com.hedera.node.app.service.contract.impl.test.exec.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.metrics.platform.PlatformMetricsFactoryImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ContractMetricsTest {

    private final Metrics metrics = fakeMetrics();

    public @NonNull ContractMetrics getSut() {
        final var contractMetrics = new ContractMetrics();
        contractMetrics.init(metrics);
        return contractMetrics;
    }

    @Test
    public void rejectedTxCountersGetBumped() {
        final var sut = getSut();

        sut.init(metrics);

        sut.incrementRejectedTx(HederaFunctionality.CONTRACT_CALL);
        sut.bumpRejectedTx(HederaFunctionality.CONTRACT_CREATE, 2);
        sut.bumpRejectedTx(HederaFunctionality.ETHEREUM_TRANSACTION, 4);

        sut.bumpRejectedForGasTx(HederaFunctionality.CONTRACT_CALL, 10);
        sut.bumpRejectedForGasTx(HederaFunctionality.CONTRACT_CREATE, 12);
        sut.bumpRejectedForGasTx(HederaFunctionality.ETHEREUM_TRANSACTION, 14);

        sut.bumpRejectedType3EthTx(20);

        assertThat(sut.getAllCounterNames())
                .containsExactlyInAnyOrder(
                        "SmartContractService:Rejected-callEthereumDueToIntrinsicGas_total",
                        "SmartContractService:Rejected-callEthereum_total",
                        "SmartContractService:Rejected-contractCallDueToIntrinsicGas_total",
                        "SmartContractService:Rejected-contractCall_total",
                        "SmartContractService:Rejected-createContractDueToIntrinsicGas_total",
                        "SmartContractService:Rejected-createContract_total",
                        "SmartContractService:Rejected-ethType3BlobTransaction_total");
        // assertThat(sut.getAllCounterDescriptions()).containsExactlyInAnyOrder();
        assertThat(sut.getAllCounters())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "SmartContractService:Rejected-callEthereumDueToIntrinsicGas_total",
                        14L,
                        "SmartContractService:Rejected-callEthereum_total",
                        4L,
                        "SmartContractService:Rejected-contractCallDueToIntrinsicGas_total",
                        10L,
                        "SmartContractService:Rejected-contractCall_total",
                        1L,
                        "SmartContractService:Rejected-createContractDueToIntrinsicGas_total",
                        12L,
                        "SmartContractService:Rejected-createContract_total",
                        2L,
                        "SmartContractService:Rejected-ethType3BlobTransaction_total",
                        20L));

        // And there is no counter for this functionality
        assertThrows(NullPointerException.class, () -> {
            sut.bumpRejectedTx(HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE, 22);
        });
    }

    private static final long DEFAULT_NODE_ID = 3;

    public static Metrics fakeMetrics() {
        final MetricsConfig metricsConfig =
                HederaTestConfigBuilder.createConfig().getConfigData(MetricsConfig.class);

        return new DefaultPlatformMetrics(
                NodeId.of(DEFAULT_NODE_ID),
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new PlatformMetricsFactoryImpl(metricsConfig),
                metricsConfig);
    }
}
