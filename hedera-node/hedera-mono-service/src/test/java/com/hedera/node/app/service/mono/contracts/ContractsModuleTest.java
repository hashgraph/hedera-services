/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.mono.contracts.ContractsModule.provideCallLocalEvmTxProcessorFactory;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;

import com.hedera.node.app.service.evm.contracts.operations.HederaBalanceOperation;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.LivePricesSource;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.txns.util.PrngLogic;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    com.hedera.node.app.service.mono.state.expiry.ExpiringCreations ExpiringCreations;

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

    @Mock
    MessageFrame messageFrame;

    @Mock
    WorldUpdater worldUpdater;

    @Mock
    CodeCache codeCache;

    @Mock
    GasCalculator gasCalculator;

    @Mock
    AliasManager aliasManager;

    @Mock
    MessageCallProcessor messageCallProcessor;

    @Mock
    ContractCreationProcessor contractCreationProcessor;

    @Mock
    AutoCreationLogic autoCreationLogic;

    @Mock
    private BiPredicate<Address, MessageFrame> addressValidator;

    ContractsTestComponent subject;

    private final String ethAddress = "0xc257274276a4e539741ca11b590b9447b26a8051";
    private final Address ethAddressInstance = Address.fromHexString(ethAddress);

    @BeforeEach
    void createComponent() {
        subject = DaggerContractsTestComponent.builder()
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
                .autoCreationLogic(autoCreationLogic)
                .build();
    }

    @Test
    void canManufactureCallLocalProcessors() {
        final var pretendVersion = "0.0.1";
        given(globalDynamicProperties.evmVersion()).willReturn(pretendVersion);
        final var supplier = provideCallLocalEvmTxProcessorFactory(
                codeCache,
                livePricesSource,
                globalDynamicProperties,
                gasCalculator,
                Map.of(pretendVersion, () -> messageCallProcessor),
                Map.of(pretendVersion, () -> contractCreationProcessor),
                aliasManager);
        assertInstanceOf(CallLocalEvmTxProcessor.class, supplier.get());
    }

    @Test
    void precompileDetectorWorksAsExpected() {
        final var addressPredicate = ContractsModule.providePrecompileDetector();

        assertFalse(addressPredicate.test(
                Address.fromHexString("0x000000000000000000000000000000000010000"))); // 18th byte is not 0
        assertTrue(addressPredicate.test(Address.fromHexString("0x0000000000000000000000000000000000000000"))); // 0
        assertFalse(addressPredicate.test(Address.fromHexString("0x00000000000000000000000000000000000002EF"))); // 751
        assertTrue(addressPredicate.test(Address.fromHexString("0x00000000000000000000000000000000000002EE"))); // 750
        assertTrue(addressPredicate.test(Address.fromHexString("0x0000000000000000000000000000000000000001"))); // 1
        assertTrue(addressPredicate.test(Address.fromHexString("0x0000000000000000000000000000000000000020"))); // 32
        assertFalse(
                addressPredicate.test(Address.fromHexString("0x0000000000000000000000000000000050000011"))); // < 0 int
    }

    @Test
    void logOperationsAreProvided() {
        for (var evm : List.of(subject.evmV_0_30(), subject.evmV_0_34())) {
            Bytes testCode = Bytes.fromHexString("0xA0A1A2A3A4");
            Code legacyCode = CodeFactory.createCode(testCode, Hash.hash(testCode), 0, false);
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
        var evm = subject.evmV_0_34();
        var prngOperation = evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x44), Hash.ZERO, 0, false), 0);

        byte[] testBytes = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32
        };
        given(prngLogic.getNMinus3RunningHashBytes()).willReturn(testBytes);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
        given(messageFrame.getRemainingGas()).willReturn(300L);

        var result = prngOperation.execute(messageFrame, evm);
        assertEquals("PRNGSEED", prngOperation.getName());
        assertEquals(2L, result.getGasCost());
        assertEquals(1, result.getPcIncrement());
        assertNull(result.getHaltReason());
        assertArrayEquals(testBytes, bytesCaptor.getValue().toArray());
    }

    @Test
    void largePrngSeedTrimsAsExpected() {
        var evm = subject.evmV_0_34();
        var prngOperation = evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x44), Hash.ZERO, 0, false), 0);

        byte[] testBytes = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32
        };
        byte[] seedBytes = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
        };
        given(prngLogic.getNMinus3RunningHashBytes()).willReturn(seedBytes);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
        given(messageFrame.getRemainingGas()).willReturn(300L);

        var result = prngOperation.execute(messageFrame, evm);
        assertEquals("PRNGSEED", prngOperation.getName());
        assertEquals(2L, result.getGasCost());
        assertEquals(1, result.getPcIncrement());
        assertNull(result.getHaltReason());
        assertArrayEquals(testBytes, bytesCaptor.getValue().toArray());
    }

    @Test
    void prngSeedOutOfGas() {
        var evm = subject.evmV_0_34();
        var prngOperation = evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x44), Hash.ZERO, 0, false), 0);

        given(messageFrame.getRemainingGas()).willReturn(0L);

        var result = prngOperation.execute(messageFrame, evm);
        assertEquals(ExceptionalHaltReason.INSUFFICIENT_GAS, result.getHaltReason());
    }

    @Test
    void difficultyInV_0_30() {
        var evm = subject.evmV_0_30();
        var difficultyOperation = evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x44), Hash.ZERO, 0, false), 0);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
        given(messageFrame.getBlockValues()).willReturn(new BlockValues() {
            @Override
            public Bytes getDifficultyBytes() {
                return Bytes32.ZERO;
            }
        });
        given(messageFrame.getRemainingGas()).willReturn(300L);

        var result = difficultyOperation.execute(messageFrame, evm);
        assertEquals("DIFFICULTY", difficultyOperation.getName());
        assertEquals(2L, result.getGasCost());
        assertEquals(1, result.getPcIncrement());
        assertNull(result.getHaltReason());
        assertArrayEquals(new byte[32], bytesCaptor.getValue().toArray());
    }

    @Test
    void chainId() {
        Bytes32 chainIdBytes = Bytes32.fromHexStringLenient("0x12345678");
        for (var evm : List.of(subject.evmV_0_30(), subject.evmV_0_34())) {
            var chainIdOperation =
                    evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x46), Hash.ZERO, 0, false), 0);

            final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);

            doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());
            given(globalDynamicProperties.chainIdBytes32()).willReturn(chainIdBytes);
            given(messageFrame.getRemainingGas()).willReturn(300L);

            var result = chainIdOperation.execute(messageFrame, evm);
            assertEquals("CHAINID", chainIdOperation.getName());
            assertEquals(2L, result.getGasCost());
            assertEquals(1, result.getPcIncrement());
            assertNull(result.getHaltReason());
            assertArrayEquals(chainIdBytes.toArray(), bytesCaptor.getValue().toArray());
        }
    }

    @Test
    void chainIdOutOfGas() {
        for (var evm : List.of(subject.evmV_0_30(), subject.evmV_0_34())) {
            var chainIdOperation =
                    evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x46), Hash.ZERO, 0, false), 0);
            given(messageFrame.getRemainingGas()).willReturn(0L);
            var result = chainIdOperation.execute(messageFrame, evm);
            assertEquals(ExceptionalHaltReason.INSUFFICIENT_GAS, result.getHaltReason());
        }
    }

    @Test
    void balanceBadAddress() {
        var evm = subject.evmV_0_30();
        var balanceOperation = evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x31), Hash.ZERO, 0, false), 0);
        given(messageFrame.getStackItem(0)).willReturn(Bytes.fromHexString("0xdeadc0dedeadc0dedeadc0dedeadc0de"));
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(any())).willReturn(null);
        var result = balanceOperation.execute(messageFrame, evm);
        assertEquals("BALANCE", balanceOperation.getName());
        assertEquals(INVALID_SOLIDITY_ADDRESS, result.getHaltReason());
    }

    @Test
    void balanceGoodAddress() {

        var evm = subject.evmV_0_34();
        var balanceOperation = evm.operationAtOffset(CodeFactory.createCode(Bytes.of(0x31), Hash.ZERO, 0, false), 0);
        ((HederaBalanceOperation) balanceOperation).setAddressValidator(addressValidator);

        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.popStackItem()).willReturn(ethAddressInstance);
        given(messageFrame.getStackItem(0)).willReturn(ethAddressInstance);
        given(messageFrame.getRemainingGas()).willReturn(100000L);

        given(addressValidator.test(any(), any())).willReturn(true);
        given(worldUpdater.get(any())).willReturn(null);

        final var bytesCaptor = ArgumentCaptor.forClass(Bytes.class);
        doNothing().when(messageFrame).pushStackItem(bytesCaptor.capture());

        var result = balanceOperation.execute(messageFrame, evm);
        assertEquals("BALANCE", balanceOperation.getName());
        assertNull(result.getHaltReason());
        assertEquals(2600, result.getGasCost());
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
