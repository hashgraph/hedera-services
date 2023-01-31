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
package com.hedera.services.evm.contracts.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.services.evm.store.contracts.HederaEvmWorldUpdater;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.CodeCache;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTxProcessorTest {
    private static final int MAX_STACK_SIZE = 1024;
    private final String EVM_VERSION_0_30 = "v0.30";
    private final String EVM_VERSION_0_32 = "v0.32";
    @Mock private PricesAndFeesProvider livePricesSource;
    @Mock private HederaEvmMutableWorldState worldState;
    @Mock private CodeCache codeCache;
    @Mock private EvmProperties globalDynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private Set<Operation> operations;
    @Mock private Transaction transaction;
    @Mock private HederaEvmWorldUpdater updater;
    @Mock private HederaEvmWorldUpdater stackedUpdater;
    @Mock private HederaBlockValues hederaBlockValues;
    @Mock private BlockValues blockValues;
    @Mock private BlockMetaSource blockMetaSource;

    private final MockHederaEvmAccount sender =
            new MockHederaEvmAccount(
                    Address.fromHexString("0x000000000000000000000000000000000000071e"));
    private final Address senderAddress =
            Address.fromHexString("0x000000000000000000000000000000000000070e");
    private final Address receiver =
            Address.fromHexString("0x627306090abaB3A6e1400e9345bC60c78a8BEf57");
    private final Address mirrorReceiver =
            Address.fromHexString("0x000000000000000000000000000000000000072e");
    private final Address fundingAccount =
            Address.fromHexString("0x000000000000000000000000000000000000074e");
    private final int MAX_REFUND_PERCENT = 20;
    private final long GAS_LIMIT = 300_000L;
    private final String INSUFFICIENT_GAS = "INSUFFICIENT_GAS";

    private MockHederaEvmTxProcessor evmTxProcessor;
    private String mcpVersion;
    private String ccpVersion;

    @BeforeEach
    void setup() {
        var operationRegistry = new OperationRegistry();
        MainnetEVMs.registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        operations.forEach(operationRegistry::put);
        when(globalDynamicProperties.evmVersion()).thenReturn(EVM_VERSION_0_30);
        var evm30 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
        Map<String, Provider<MessageCallProcessor>> mcps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> {
                            mcpVersion = EVM_VERSION_0_30;
                            return new MessageCallProcessor(
                                    evm30, new PrecompileContractRegistry());
                        },
                        EVM_VERSION_0_32,
                        () -> {
                            mcpVersion = EVM_VERSION_0_32;
                            return new MessageCallProcessor(
                                    evm30, new PrecompileContractRegistry());
                        });
        Map<String, Provider<ContractCreationProcessor>> ccps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> {
                            ccpVersion = EVM_VERSION_0_30;

                            return new ContractCreationProcessor(
                                    gasCalculator, evm30, true, List.of(), 1);
                        },
                        EVM_VERSION_0_32,
                        () -> {
                            ccpVersion = EVM_VERSION_0_32;
                            return new ContractCreationProcessor(
                                    gasCalculator, evm30, true, List.of(), 1);
                        });

        evmTxProcessor =
                new MockHederaEvmTxProcessor(
                        worldState,
                        livePricesSource,
                        globalDynamicProperties,
                        gasCalculator,
                        mcps,
                        ccps,
                        blockMetaSource);

        final var hederaEvmOperationTracer = new MockHederaEvmOperationTracer();
        evmTxProcessor.setOperationTracer(hederaEvmOperationTracer);
    }

    @Test
    void assertSuccessExecution() {
        givenValidMock(0L);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        33_333L,
                        1234L,
                        1L,
                        Bytes.EMPTY,
                        false,
                        true,
                        mirrorReceiver);
        assertTrue(result.isSuccessful());
    }

    @Test
    void missingCodeBecomesEmptyInInitialFrame() {
        MessageFrame.Builder protoFrame =
                MessageFrame.builder()
                        .messageFrameStack(new ArrayDeque<>())
                        .worldUpdater(updater)
                        .initialGas(1L)
                        .originator(sender.canonicalAddress())
                        .gasPrice(Wei.ZERO)
                        .sender(sender.canonicalAddress())
                        .value(Wei.ONE)
                        .apparentValue(Wei.ONE)
                        .blockValues(blockValues)
                        .depth(1)
                        .completer(frame -> {})
                        .miningBeneficiary(Address.ZERO)
                        .blockHashLookup(hash -> null);

        var messageFrame = evmTxProcessor.buildInitialFrame(protoFrame, receiver, Bytes.EMPTY, 33L);

        assertEquals(Code.EMPTY, messageFrame.getCode());
    }

    @Test
    void assertSuccessExecutionChargesCorrectMinimumGas() {
        givenValidMock(0L);

        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);
        var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        0L,
                        GAS_LIMIT,
                        1234L,
                        Bytes.EMPTY,
                        false,
                        false,
                        mirrorReceiver);
        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), GAS_LIMIT - GAS_LIMIT * MAX_REFUND_PERCENT / 100);
    }

    @Test
    void assertSuccessExecutionChargesCorrectGasWhenGasUsedIsLargerThanMinimum() {
        givenValidMock(0L);
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);

        long intrinsicGasCost = 290_000L;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false))
                .willReturn(intrinsicGasCost);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        0L,
                        GAS_LIMIT,
                        1234L,
                        Bytes.EMPTY,
                        false,
                        false,
                        mirrorReceiver);
        assertTrue(result.isSuccessful());
        assertEquals(intrinsicGasCost, result.getGasUsed());
    }

    @Test
    void throwsWhenSenderCannotCoverUpfrontCost() {
        givenInvalidMock();

        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(stackedUpdater.getSenderAccount(any())).willReturn(wrappedSenderAccount);
        given(stackedUpdater.getSenderAccount(any()).getMutable()).willReturn(mutableSenderAccount);
        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(100_000L);

        final var wrappedRecipientAccount = mock(EvmAccount.class);
        final var mutableRecipientAccount = mock(MutableAccount.class);

        given(stackedUpdater.getOrCreate(any())).willReturn(wrappedRecipientAccount);
        given(stackedUpdater.getOrCreate(any()).getMutable()).willReturn(mutableRecipientAccount);
        given(updater.updater()).willReturn(stackedUpdater);

        final var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        333_333L,
                        1234L,
                        1L,
                        Bytes.EMPTY,
                        false,
                        true,
                        mirrorReceiver);
        assertEquals(INSUFFICIENT_GAS, result.getHaltReason().get().name());
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimit() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);
        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);

        var evmAccount = mock(EvmAccount.class);
        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
        given(evmAccount.getMutable()).willReturn(senderMutableAccount);

        given(stackedUpdater.getSenderAccount(any())).willReturn(evmAccount);
        given(stackedUpdater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(evmAccount);
        given(stackedUpdater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);

        givenInvalidMock();

        final var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        33_333L,
                        0L,
                        1234L,
                        Bytes.EMPTY,
                        false,
                        true,
                        mirrorReceiver);
        assertEquals(INSUFFICIENT_GAS, result.getHaltReason().get().name());
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimitAndGasLimitIsEqualToMaxGasLimit() {
        givenValidMock(100_000L);

        int maxGasLimit = 10_000_000;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false))
                .willReturn(maxGasLimit + 1L);

        final var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        0L,
                        maxGasLimit,
                        1234L,
                        Bytes.EMPTY,
                        false,
                        false,
                        mirrorReceiver);
        assertEquals(INSUFFICIENT_GAS, result.getHaltReason().get().name());
    }

    @Test
    void assertIsContractCallFunctionality() {
        assertEquals(HederaFunctionality.ContractCall, evmTxProcessor.getFunctionType());
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        doReturn(Optional.of(receiver)).when(transaction).getTo();
        given(transaction.getSender()).willReturn(senderAddress);
        given(transaction.getValue()).willReturn(Wei.of(1L));
        final MessageFrame.Builder commonInitialFrame =
                MessageFrame.builder()
                        .messageFrameStack(mock(Deque.class))
                        .maxStackSize(MAX_STACK_SIZE)
                        .worldUpdater(mock(WorldUpdater.class))
                        .initialGas(GAS_LIMIT)
                        .originator(senderAddress)
                        .gasPrice(Wei.ZERO)
                        .sender(senderAddress)
                        .value(Wei.of(transaction.getValue().getAsBigInteger()))
                        .apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
                        .blockValues(mock(BlockValues.class))
                        .depth(0)
                        .completer(__ -> {})
                        .miningBeneficiary(Address.ZERO)
                        .blockHashLookup(h -> null);
        // when:
        MessageFrame buildMessageFrame =
                evmTxProcessor.buildInitialFrame(
                        commonInitialFrame, (Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

        // expect:
        assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
        assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
    }

    @Test
    void assertSuccessExecutionWithRefund() {
        givenValidMock(0L);
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(100);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        var result =
                evmTxProcessor.execute(
                        sender,
                        receiver,
                        GAS_LIMIT,
                        0L,
                        1234L,
                        Bytes.EMPTY,
                        false,
                        true,
                        mirrorReceiver);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getGasUsed());
    }

    private void givenInvalidMock() {
        given(worldState.updater()).willReturn(updater);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(100_000L);
    }

    private void givenValidMock(final long intrinsicGasCost) {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false))
                .willReturn(intrinsicGasCost);

        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
        given(evmAccount.getMutable()).willReturn(senderMutableAccount);

        given(stackedUpdater.getSenderAccount(any())).willReturn(evmAccount);
        given(stackedUpdater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(evmAccount);
        given(stackedUpdater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);

        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
    }

    @Test
    void testEvmVersionLoading() {
        given(globalDynamicProperties.evmVersion()).willReturn(EVM_VERSION_0_32, "vDoesn'tExist");
        given(globalDynamicProperties.dynamicEvmVersion()).willReturn(false, false, true, true);

        givenValidMock(0L);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        // uses default setup
        evmTxProcessor.execute(
                sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, false, true, mirrorReceiver);
        assertEquals(EVM_VERSION_0_30, mcpVersion);
        assertEquals(EVM_VERSION_0_30, ccpVersion);

        // version changes, but dynamic not set
        evmTxProcessor.execute(
                sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, false, true, mirrorReceiver);
        assertEquals(EVM_VERSION_0_30, mcpVersion);
        assertEquals(EVM_VERSION_0_30, ccpVersion);

        // version changes, dynamic set
        evmTxProcessor.execute(
                sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, false, true, mirrorReceiver);
        assertEquals(EVM_VERSION_0_32, mcpVersion);
        assertEquals(EVM_VERSION_0_32, ccpVersion);

        // bad version
        assertThrows(
                NullPointerException.class,
                () ->
                        evmTxProcessor.execute(
                                sender,
                                receiver,
                                33_333L,
                                1234L,
                                1L,
                                Bytes.EMPTY,
                                false,
                                true,
                                mirrorReceiver));
    }
}
