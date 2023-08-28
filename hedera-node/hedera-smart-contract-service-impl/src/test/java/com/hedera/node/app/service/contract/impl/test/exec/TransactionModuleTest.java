/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.exec.TransactionModule.provideActionSidecarContentTracer;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_CALL_DATA;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.TransactionModule;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionModuleTest {
    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private HederaOperations hederaOperations;

    @Mock
    private SystemContractOperations systemContractOperations;

    @Mock
    private EvmFrameStateFactory factory;

    @Mock
    private EthereumCallDataHydration hydration;

    @Mock
    private ReadableFileStore fileStore;

    @Mock
    private HandleContext context;

    @Test
    void createsEvmActionTracer() {
        assertInstanceOf(EvmActionTracer.class, provideActionSidecarContentTracer());
    }

    @Test
    void feesOnlyUpdaterIsProxyUpdater() {
        assertInstanceOf(
                ProxyWorldUpdater.class,
                TransactionModule.provideFeesOnlyUpdater(hederaOperations, systemContractOperations, factory)
                        .get());
    }

    @Test
    void providesEthTxDataWhenApplicable() {
        final var ethTxn = EthereumTransactionBody.newBuilder()
                .ethereumData(TestHelpers.ETH_WITH_TO_ADDRESS)
                .build();
        final var body =
                TransactionBody.newBuilder().ethereumTransaction(ethTxn).build();
        given(context.body()).willReturn(body);
        final var expectedHydration = HydratedEthTxData.successFrom(ETH_DATA_WITH_CALL_DATA);
        given(hydration.tryToHydrate(ethTxn, fileStore)).willReturn(expectedHydration);
        assertSame(expectedHydration, TransactionModule.maybeProvideHydratedEthTxData(context, hydration, fileStore));
    }

    @Test
    void providesNullEthTxDataIfNotEthereumTransaction() {
        final var callTxn = ContractCallTransactionBody.newBuilder()
                .contractID(TestHelpers.CALLED_CONTRACT_ID)
                .build();
        final var body = TransactionBody.newBuilder().contractCall(callTxn).build();
        given(context.body()).willReturn(body);
        assertNull(TransactionModule.maybeProvideHydratedEthTxData(context, hydration, fileStore));
    }

    @Test
    void providesValidators() {
        given(context.attributeValidator()).willReturn(attributeValidator);
        given(context.expiryValidator()).willReturn(expiryValidator);
        assertSame(attributeValidator, TransactionModule.provideAttributeValidator(context));
        assertSame(expiryValidator, TransactionModule.provideExpiryValidator(context));
    }

    @Test
    void providesNetworkInfo() {
        given(context.networkInfo()).willReturn(networkInfo);
        assertSame(networkInfo, TransactionModule.provideNetworkInfo(context));
    }

    @Test
    void providesExpectedConsTime() {
        given(context.consensusNow()).willReturn(Instant.MAX);
        assertSame(Instant.MAX, TransactionModule.provideConsensusTime(context));
    }
}
