/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.txns.util.PrngLogic;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsModuleTest {
    @Mock
    GlobalDynamicProperties globalDynamicProperties;
    @Mock
    UsagePricesProvider usagePricesProvider;
    @Mock
    HbarCentExchange hbarCentExchange;
    @Mock
    EvmSigsVerifier evmSigsVerifier;
    @Mock
    RecordsHistorian recordsHistorian;
    @Mock
    ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock
    FeeCalculator feeCalculatorProvider;
    @Mock
    StateView stateView;
    @Mock
    TxnAwareEvmSigsVerifier txnAwareEvmSigsVerifier;
    @Mock
    com.hedera.services.state.expiry.ExpiringCreations ExpiringCreations;
    @Mock
    InfrastructureFactory InfrastructureFactory;
    @Mock
    Supplier<Instant> now;
    @Mock
    PrngLogic prngLogic;
    @Mock
    LivePricesSource livePricesSource;
    @Mock
    TransactionContext transactionContext;
    @Mock
    EntityCreator entityCreator;

    ContractsTestComponent subject;

    @BeforeEach
    void createComponent() {
        subject =
                DaggerContractsTestComponent.builder()
                        .globalDynamicProperties(globalDynamicProperties)
                        .usagePricesProvider(usagePricesProvider)
                        .hbarCentExchange(hbarCentExchange)
                        .evmSigsVerifier(evmSigsVerifier)
                        .recordsHistorian(recordsHistorian)
                        .impliedTransferMarshal(impliedTransfersMarshal)
                        .feeCalculator(feeCalculatorProvider)
                        .stateView(stateView)
                        .txnAwareSigsVerifier(txnAwareEvmSigsVerifier)
                        .ExpiringCreations(ExpiringCreations)
                        .InfrastructureFactory(InfrastructureFactory)
                        .now(now)
                        .prngLogic(prngLogic)
                        .livePricesSource(livePricesSource)
                        .transactionContext(transactionContext)
                        .entityCreator(entityCreator)
                        .build();
    }

    @Test
    void logOperationsAreProvided() {
        for (var evm : List.of(
                subject.evmV_0_30(),
                subject.evmV_0_31()
        )) {
            Bytes testCode = Bytes.fromHexString("0xA0A1A2A3A4");
            Code legacyCode = Code.createLegacyCode(testCode, Hash.hash(testCode));
            final var log0 = evm.operationAtOffset(legacyCode, 0);
            final var log1 = evm.operationAtOffset(legacyCode, 1);
            final var log2 = evm.operationAtOffset(legacyCode, 2);
            final var log3 = evm.operationAtOffset(legacyCode, 3);
            final var log4 = evm.operationAtOffset(legacyCode, 4);

            assertEquals("LOG0", log0.getName());
            assertEquals("LOG1", log1.getName());
            assertEquals("LOG2", log2.getName());
            assertEquals("LOG3", log3.getName());
            assertEquals("LOG4", log4.getName());
        }
    }
}
