/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.node.app.service.contract.impl.exec.QueryModule.provideActionSidecarContentTracer;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_HEDERA_CONFIG;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.QueryModule;
import com.hedera.node.app.service.contract.impl.exec.gas.CanonicalDispatchPrices;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.exec.tracers.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.spi.workflows.QueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryModuleTest {
    @Mock
    private CanonicalDispatchPrices canonicalDispatchPrices;

    @Mock
    private HederaOperations hederaOperations;

    @Mock
    private HederaNativeOperations hederaNativeOperations;

    @Mock
    SystemContractOperations systemContractOperations;

    @Mock
    private EvmFrameStateFactory factory;

    @Mock
    private HederaEvmBlocks hederaEvmBlocks;

    @Mock
    private TinybarValues tinybarValues;

    @Mock
    private QueryContext queryContext;

    @Mock
    private SystemContractGasCalculator systemContractGasCalculator;

    @Test
    void providesExpectedProxyWorldUpdater() {
        final var enhancement =
                new HederaWorldUpdater.Enhancement(hederaOperations, hederaNativeOperations, systemContractOperations);
        assertInstanceOf(ProxyWorldUpdater.class, QueryModule.provideProxyWorldUpdater(enhancement, factory));
    }

    @Test
    void getsHederaConfig() {
        given(queryContext.configuration()).willReturn(TestHelpers.DEFAULT_CONFIG);
        assertSame(DEFAULT_HEDERA_CONFIG, QueryModule.provideHederaConfig(queryContext));
    }

    @Test
    void createsEvmActionTracer() {
        assertInstanceOf(EvmActionTracer.class, provideActionSidecarContentTracer());
    }

    @Test
    void feesOnlyUpdaterIsProxyUpdater() {
        final var enhancement =
                new HederaWorldUpdater.Enhancement(hederaOperations, hederaNativeOperations, systemContractOperations);
        assertInstanceOf(
                ProxyWorldUpdater.class,
                QueryModule.provideFeesOnlyUpdater(enhancement, factory).get());
    }

    @Test
    void providesExpectedHederaEvmContext() {
        assertInstanceOf(
                HederaEvmContext.class,
                QueryModule.provideHederaEvmContext(
                        hederaOperations, hederaEvmBlocks, tinybarValues, systemContractGasCalculator));
    }

    @Test
    void systemContractCalculatorNotUsableForChildDispatchCalculations() {
        final var calculator = QueryModule.provideSystemContractGasCalculator(canonicalDispatchPrices, tinybarValues);
        assertThrows(
                IllegalStateException.class,
                () -> calculator.gasRequirement(TransactionBody.DEFAULT, DispatchType.APPROVE, AccountID.DEFAULT));
    }
}
