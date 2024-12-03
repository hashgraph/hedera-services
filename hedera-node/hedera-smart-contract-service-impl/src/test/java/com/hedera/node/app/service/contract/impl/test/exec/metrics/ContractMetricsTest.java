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
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.config.data.ContractsConfig;
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
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ContractMetricsTest {

    private final Supplier<Metrics> metricsSupplier = () -> fakeMetrics();

    @Mock
    private ContractsConfig contractsConfig;

    public @NonNull ContractMetrics getSubject() {
        final var contractMetrics = new ContractMetrics(metricsSupplier, () -> contractsConfig);
        contractMetrics.createContractMetrics();
        return contractMetrics;
    }

    @Test
    public void rejectedTxCountersGetBumpedWhenEnabled() {
        given(contractsConfig.metricsSmartContractPrimaryEnabled()).willReturn(true);

        final var subject = getSubject();

        subject.incrementRejectedTx(HederaFunctionality.CONTRACT_CALL);
        subject.bumpRejectedTx(HederaFunctionality.CONTRACT_CREATE, 2);
        subject.bumpRejectedTx(HederaFunctionality.ETHEREUM_TRANSACTION, 4);

        subject.bumpRejectedForGasTx(HederaFunctionality.CONTRACT_CALL, 10);
        subject.bumpRejectedForGasTx(HederaFunctionality.CONTRACT_CREATE, 12);
        subject.bumpRejectedForGasTx(HederaFunctionality.ETHEREUM_TRANSACTION, 14);

        subject.bumpRejectedType3EthTx(20);

        assertThat(subject.getAllCounterNames())
                .containsExactlyInAnyOrder(
                        "SmartContractService:Rejected-ethereumTxDueToIntrinsicGas_total",
                        "SmartContractService:Rejected-ethereumTx_total",
                        "SmartContractService:Rejected-contractCallTxDueToIntrinsicGas_total",
                        "SmartContractService:Rejected-contractCallTx_total",
                        "SmartContractService:Rejected-contractCreateTxDueToIntrinsicGas_total",
                        "SmartContractService:Rejected-contractCreateTx_total",
                        "SmartContractService:Rejected-ethType3BlobTransaction_total");
        assertThat(subject.getAllCounters())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "SmartContractService:Rejected-ethereumTxDueToIntrinsicGas_total",
                        14L,
                        "SmartContractService:Rejected-ethereumTx_total",
                        4L,
                        "SmartContractService:Rejected-contractCallTxDueToIntrinsicGas_total",
                        10L,
                        "SmartContractService:Rejected-contractCallTx_total",
                        1L,
                        "SmartContractService:Rejected-contractCreateTxDueToIntrinsicGas_total",
                        12L,
                        "SmartContractService:Rejected-contractCreateTx_total",
                        2L,
                        "SmartContractService:Rejected-ethType3BlobTransaction_total",
                        20L));

        // And there is no counter for this functionality
        assertThrows(NullPointerException.class, () -> {
            subject.bumpRejectedTx(HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE, 22);
        });
    }

    @Test
    public void rejectedTxCountersGetIgnoredWhenDisabled() {
        given(contractsConfig.metricsSmartContractPrimaryEnabled()).willReturn(false);

        final var subject = getSubject();

        subject.incrementRejectedTx(HederaFunctionality.CONTRACT_CALL);
        subject.bumpRejectedTx(HederaFunctionality.CONTRACT_CREATE, 2);
        subject.bumpRejectedTx(HederaFunctionality.ETHEREUM_TRANSACTION, 4);

        subject.bumpRejectedForGasTx(HederaFunctionality.CONTRACT_CALL, 10);
        subject.bumpRejectedForGasTx(HederaFunctionality.CONTRACT_CREATE, 12);
        subject.bumpRejectedForGasTx(HederaFunctionality.ETHEREUM_TRANSACTION, 14);

        subject.bumpRejectedType3EthTx(20);

        assertThat(subject.getAllCounterNames()).isEmpty();
        assertThat(subject.getAllCounters()).isEmpty();
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
