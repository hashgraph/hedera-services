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

import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;

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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsModuleTest {
    @Mock GlobalDynamicProperties globalDynamicProperties;
    @Mock UsagePricesProvider usagePricesProvider;
    @Mock HbarCentExchange hbarCentExchange;
    @Mock EvmSigsVerifier evmSigsVerifier;
    @Mock RecordsHistorian recordsHistorian;
    @Mock ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock FeeCalculator feeCalculatorProvider;
    @Mock StateView stateView;
    @Mock TxnAwareEvmSigsVerifier txnAwareEvmSigsVerifier;
    @Mock com.hedera.services.state.expiry.ExpiringCreations ExpiringCreations;
    @Mock InfrastructureFactory InfrastructureFactory;
    @Mock Supplier<Instant> now;
    @Mock PrngLogic prngLogic;
    @Mock LivePricesSource livePricesSource;
    @Mock TransactionContext transactionContext;
    @Mock EntityCreator entityCreator;
    @Mock MessageFrame messageFrame;
    @Mock WorldUpdater worldUpdater;

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
        for (var evm : List.of(subject.evmV_0_30(), subject.evmV_0_31())) {
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

    @Test
    void prngSeedOverwritesDifficulty() {
        var evm = subject.evmV_0_31();
        var prngOperation =
                evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x44), Hash.ZERO), 0);

        byte[] testBytes = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            25, 26, 27, 28, 29, 30, 31, 32
        };
        given(prngLogic.getNMinus3RunningHashBytes()).willReturn(testBytes);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
        given(messageFrame.getRemainingGas()).willReturn(300L);

        var result = prngOperation.execute(messageFrame, evm);
        assertEquals("PRNGSEED", prngOperation.getName());
        assertEquals(OptionalLong.of(2L), result.getGasCost());
        assertEquals(1, result.getPcIncrement());
        assertEquals(Optional.empty(), result.getHaltReason());
        assertArrayEquals(testBytes, bytesCaptor.getValue().toArray());
    }

    @Test
    void largePrngSeedTrimsAsExpected() {
        var evm = subject.evmV_0_31();
        var prngOperation =
                evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x44), Hash.ZERO), 0);

        byte[] testBytes = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            25, 26, 27, 28, 29, 30, 31, 32
        };
        byte[] seedBytes = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46,
            47, 48
        };
        given(prngLogic.getNMinus3RunningHashBytes()).willReturn(seedBytes);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
        given(messageFrame.getRemainingGas()).willReturn(300L);

        var result = prngOperation.execute(messageFrame, evm);
        assertEquals("PRNGSEED", prngOperation.getName());
        assertEquals(OptionalLong.of(2L), result.getGasCost());
        assertEquals(1, result.getPcIncrement());
        assertEquals(Optional.empty(), result.getHaltReason());
        assertArrayEquals(testBytes, bytesCaptor.getValue().toArray());
    }

    @Test
    void prngSeedOutOfGas() {
        var evm = subject.evmV_0_31();
        var prngOperation =
                evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x44), Hash.ZERO), 0);

        given(messageFrame.getRemainingGas()).willReturn(0L);

        var result = prngOperation.execute(messageFrame, evm);
        assertEquals(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS), result.getHaltReason());
    }

    @Test
    void difficultyInV_0_30() {
        var evm = subject.evmV_0_30();
        var difficultyOperation =
                evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x44), Hash.ZERO), 0);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
        given(messageFrame.getBlockValues())
                .willReturn(
                        new BlockValues() {
                            @Override
                            public Bytes getDifficultyBytes() {
                                return Bytes32.ZERO;
                            }
                        });
        given(messageFrame.getRemainingGas()).willReturn(300L);

        var result = difficultyOperation.execute(messageFrame, evm);
        assertEquals("DIFFICULTY", difficultyOperation.getName());
        assertEquals(OptionalLong.of(2L), result.getGasCost());
        assertEquals(1, result.getPcIncrement());
        assertEquals(Optional.empty(), result.getHaltReason());
        assertArrayEquals(new byte[32], bytesCaptor.getValue().toArray());
    }

    @Test
    void chainId() {
        Bytes32 chainIdBytes = Bytes32.fromHexStringLenient("0x12345678");
        for (var evm : List.of(subject.evmV_0_30(), subject.evmV_0_31())) {
            var chainIdOperation =
                    evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x46), Hash.ZERO), 0);

            final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

            doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
            given(globalDynamicProperties.chainIdBytes32()).willReturn(chainIdBytes);
            given(messageFrame.getRemainingGas()).willReturn(300L);

            var result = chainIdOperation.execute(messageFrame, evm);
            assertEquals("CHAINID", chainIdOperation.getName());
            assertEquals(OptionalLong.of(2L), result.getGasCost());
            assertEquals(1, result.getPcIncrement());
            assertEquals(Optional.empty(), result.getHaltReason());
            assertArrayEquals(chainIdBytes.toArray(), bytesCaptor.getValue().toArray());
        }
    }

    @Test
    void chainIdOutOfGas() {
        for (var evm : List.of(subject.evmV_0_30(), subject.evmV_0_31())) {
            var chainIdOperation =
                    evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x46), Hash.ZERO), 0);
            given(messageFrame.getRemainingGas()).willReturn(0L);
            var result = chainIdOperation.execute(messageFrame, evm);
            assertEquals(
                    Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS), result.getHaltReason());
        }
    }

    @Test
    void balanceBadAddress() {
        var evm = subject.evmV_0_30();
        var balanceOperation =
                evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x31), Hash.ZERO), 0);
        given(messageFrame.getStackItem(0))
                .willReturn(Bytes.fromHexString("0xdeadc0dedeadc0dedeadc0dedeadc0de"));
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(null);
        var result = balanceOperation.execute(messageFrame, evm);
        assertEquals("BALANCE", balanceOperation.getName());
        assertEquals(Optional.of(INVALID_SOLIDITY_ADDRESS), result.getHaltReason());
    }

    @Test
    void balanceGoodAddress() {
        var evm = subject.evmV_0_31();
        var balanceOperation =
                evm.operationAtOffset(Code.createLegacyCode(Bytes.of(0x31), Hash.ZERO), 0);
        given(messageFrame.getRemainingGas()).willReturn(3000L);
        given(messageFrame.popStackItem())
                .willReturn(Bytes.fromHexString("0xdeadc0dedeadc0dedeadc0dedeadc0de"));
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(null);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);
        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());

        var result = balanceOperation.execute(messageFrame, evm);
        assertEquals("BALANCE", balanceOperation.getName());
        assertEquals(Optional.empty(), result.getHaltReason());
        assertEquals(OptionalLong.of(2600), result.getGasCost());
        assertEquals(UInt256.ZERO, bytesCaptor.getValue());
    }

    @Test
    void allProcessorsLoad() {
        var versions = subject.contractCreateProcessors().keySet();
        assertEquals(versions, subject.messageCallProcessors().keySet());

        for (var version : versions) {
            subject.messageCallProcessors().get(version).get();
            subject.contractCreateProcessors().get(version).get();
        }
    }
}
