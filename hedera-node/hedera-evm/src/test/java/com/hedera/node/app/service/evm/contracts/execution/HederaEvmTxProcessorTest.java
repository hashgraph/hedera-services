/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.contracts.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTxProcessorTest {
    private static final int MAX_STACK_SIZE = 1024;
    private final String EVM_VERSION_0_30 = "v0.30";
    private final String EVM_VERSION_0_34 = "v0.34";
    private final String EVM_VERSION_0_50 = "v0.50";

    @Mock
    private PricesAndFeesProvider livePricesSource;

    @Mock
    private HederaEvmMutableWorldState worldState;

    @Mock
    private EvmProperties globalDynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private HederaEvmWorldUpdater updater;

    @Mock
    private HederaEvmWorldUpdater stackedUpdater;

    @Mock
    private HederaBlockValues hederaBlockValues;

    @Mock
    private BlockValues blockValues;

    @Mock
    private BlockMetaSource blockMetaSource;

    @Mock
    private EvmConfiguration evmConfiguration;

    private final HederaEvmAccount sender =
            new HederaEvmAccount(Address.fromHexString("0x000000000000000000000000000000000000071e"));
    private final Address senderAddress = Address.fromHexString("0x000000000000000000000000000000000000070e");
    private final Address receiver = Address.fromHexString("0x627306090abaB3A6e1400e9345bC60c78a8BEf57");
    private final Address mirrorReceiver = Address.fromHexString("0x000000000000000000000000000000000000072e");
    private final Address fundingAccount = Address.fromHexString("0x000000000000000000000000000000000000074e");
    private final int MAX_REFUND_PERCENT = 20;
    private final long GAS_LIMIT = 300_000L;
    private final String INSUFFICIENT_GAS = "INSUFFICIENT_GAS";

    private SpyHederaEvmTxProcessor evmTxProcessor;
    private MessageCallProcessor msgCallProcessor050;
    private String mcpVersion;
    private String ccpVersion;

    @BeforeEach
    void setup() {
        final var testChainId = BigInteger.ZERO;
        final var operationRegistry = new OperationRegistry();
        final var operationRegistry50 = new OperationRegistry();
        MainnetEVMs.registerLondonOperations(operationRegistry, gasCalculator, testChainId);
        MainnetEVMs.registerCancunOperations(operationRegistry, gasCalculator, testChainId);
        when(globalDynamicProperties.evmVersion()).thenReturn(EVM_VERSION_0_30);
        when(evmConfiguration.getJumpDestCacheWeightBytes())
                .thenReturn(EvmConfiguration.DEFAULT.getJumpDestCacheWeightBytes());
        final var evm30 = new EVM(operationRegistry, gasCalculator, evmConfiguration, EvmSpecVersion.LONDON);
        final var evm50 = new EVM(operationRegistry, gasCalculator, evmConfiguration, EvmSpecVersion.CANCUN);
        final Map<String, Provider<MessageCallProcessor>> mcps = Map.of(
                EVM_VERSION_0_30,
                () -> {
                    mcpVersion = EVM_VERSION_0_30;
                    return new MessageCallProcessor(evm30, new PrecompileContractRegistry());
                },
                EVM_VERSION_0_34,
                () -> {
                    mcpVersion = EVM_VERSION_0_34;
                    return new MessageCallProcessor(evm30, new PrecompileContractRegistry());
                },
                EVM_VERSION_0_50,
                () -> {
                    mcpVersion = EVM_VERSION_0_50;
                    return msgCallProcessor050 = new MessageCallProcessor(evm50, new PrecompileContractRegistry());
                });
        final Map<String, Provider<ContractCreationProcessor>> ccps = Map.of(
                EVM_VERSION_0_30,
                () -> {
                    ccpVersion = EVM_VERSION_0_30;

                    return new ContractCreationProcessor(gasCalculator, evm30, true, List.of(), 1);
                },
                EVM_VERSION_0_34,
                () -> {
                    ccpVersion = EVM_VERSION_0_34;
                    return new ContractCreationProcessor(gasCalculator, evm30, true, List.of(), 1);
                },
                EVM_VERSION_0_50,
                () -> {
                    ccpVersion = EVM_VERSION_0_50;
                    return new ContractCreationProcessor(gasCalculator, evm50, true, List.of(), 1);
                });

        evmTxProcessor = new SpyHederaEvmTxProcessor(
                worldState, livePricesSource, globalDynamicProperties, gasCalculator, mcps, ccps, blockMetaSource);

        final var hederaEvmOperationTracer = new DefaultHederaTracer();
        evmTxProcessor.setOperationTracer(hederaEvmOperationTracer);
    }

    @Test
    void assertSuccessExecution() {
        givenValidMock(0L);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver);
        assertTrue(result.isSuccessful());
    }

    @Test
    void assertSuccessExecution050() {
        // Force EVM 0.50
        when(globalDynamicProperties.dynamicEvmVersion()).thenReturn(true);
        when(globalDynamicProperties.evmVersion()).thenReturn(EVM_VERSION_0_50);
        givenValidMock(0L);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        final var spyOpTracer = new DefaultHederaTracer() {
            public MessageFrame frame;

            @Override
            public void init(final MessageFrame initialFrame) {
                frame = initialFrame;
            }
        };
        evmTxProcessor.setOperationTracer(spyOpTracer);
        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver);
        assertTrue(result.isSuccessful());
        assertEquals(msgCallProcessor050, evmTxProcessor.getMessageCallProcessor(), "Confirming using EVM 0.50");

        // Given EVM 0.50, check Cancun semantics
        assertTrue(() -> spyOpTracer.frame.getVersionedHashes().orElseThrow().isEmpty());
        assertEquals(Wei.ONE, spyOpTracer.frame.getBlobGasPrice());
    }

    @Test
    void missingCodeBecomesEmptyInInitialFrame() {
        final MessageFrame.Builder protoFrame = MessageFrame.builder()
                .worldUpdater(updater)
                .initialGas(1L)
                .originator(sender.canonicalAddress())
                .gasPrice(Wei.ZERO)
                .sender(sender.canonicalAddress())
                .value(Wei.ONE)
                .apparentValue(Wei.ONE)
                .blockValues(blockValues)
                .completer(frame -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup(hash -> null);

        final var messageFrame = evmTxProcessor.buildInitialFrame(protoFrame, receiver, Bytes.EMPTY, 33L);

        assertEquals(CodeV0.EMPTY_CODE, messageFrame.getCode());
    }

    @Test
    void assertSuccessExecutionChargesCorrectMinimumGas() {
        givenValidMock(0L);

        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);
        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 0L, GAS_LIMIT, 1234L, Bytes.EMPTY, false, mirrorReceiver);
        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), GAS_LIMIT - GAS_LIMIT * MAX_REFUND_PERCENT / 100);
    }

    @Test
    void assertSuccessExecutionChargesCorrectGasWhenGasUsedIsLargerThanMinimum() {
        givenValidMock(0L);
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);

        final long intrinsicGasCost = 290_000L;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(intrinsicGasCost);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 0L, GAS_LIMIT, 1234L, Bytes.EMPTY, false, mirrorReceiver);
        assertTrue(result.isSuccessful());
        assertEquals(intrinsicGasCost, result.getGasUsed());
    }

    @Test
    void throwsWhenSenderCannotCoverUpfrontCost() {
        givenInvalidMock();

        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);
        final var wrappedSenderAccount = mock(MutableAccount.class);
        given(stackedUpdater.getSenderAccount(any())).willReturn(wrappedSenderAccount);
        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(100_000L);

        final var wrappedRecipientAccount = mock(MutableAccount.class);

        given(stackedUpdater.getOrCreate(any())).willReturn(wrappedRecipientAccount);
        given(updater.updater()).willReturn(stackedUpdater);

        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 333_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver);
        assertEquals(INSUFFICIENT_GAS, result.getHaltReason().get().name());
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimit() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);
        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);

        final var mutableAccount = mock(MutableAccount.class);
        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        final var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));

        given(stackedUpdater.getSenderAccount(any())).willReturn(mutableAccount);
        given(stackedUpdater.getSenderAccount(any())).willReturn(senderMutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(mutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(senderMutableAccount);

        givenInvalidMock();

        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 33_333L, 0L, 1234L, Bytes.EMPTY, true, mirrorReceiver);
        assertEquals(INSUFFICIENT_GAS, result.getHaltReason().get().name());
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimitAndGasLimitIsEqualToMaxGasLimit() {
        givenValidMock(100_000L);

        final int maxGasLimit = 10_000_000;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(maxGasLimit + 1L);

        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, 0L, maxGasLimit, 1234L, Bytes.EMPTY, false, mirrorReceiver);
        assertEquals(INSUFFICIENT_GAS, result.getHaltReason().get().name());
    }

    @Test
    void assertIsContractCallFunctionality() {
        assertEquals(HederaFunctionality.CONTRACT_CALL, evmTxProcessor.getFunctionType());
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        final Wei oneWei = Wei.of(1L);
        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(mock(WorldUpdater.class))
                .initialGas(GAS_LIMIT)
                .originator(senderAddress)
                .gasPrice(Wei.ZERO)
                .sender(senderAddress)
                .value(oneWei)
                .apparentValue(oneWei)
                .blockValues(mock(BlockValues.class))
                .completer(__ -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup(h -> null);
        // when:
        final MessageFrame buildMessageFrame =
                evmTxProcessor.buildInitialFrame(commonInitialFrame, receiver, Bytes.EMPTY, 0L);

        // expect:
        assertEquals(senderAddress, buildMessageFrame.getSenderAddress());
        assertEquals(oneWei, buildMessageFrame.getApparentValue());
    }

    @Test
    void assertSuccessExecutionWithRefund() {
        givenValidMock(0L);
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(100);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        evmTxProcessor.setupFields(Bytes.EMPTY, false);
        final var result =
                evmTxProcessor.execute(sender, receiver, GAS_LIMIT, 0L, 1234L, Bytes.EMPTY, true, mirrorReceiver);

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

        final var mutableAccount = mock(MutableAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(intrinsicGasCost);

        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        given(stackedUpdater.getSenderAccount(any())).willReturn(mutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(mutableAccount);

        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
    }

    @Test
    void testEvmVersionLoading() {
        given(globalDynamicProperties.evmVersion()).willReturn(EVM_VERSION_0_34, "vDoesn'tExist");
        given(globalDynamicProperties.dynamicEvmVersion()).willReturn(false, false, true, true);

        givenValidMock(0L);
        given(globalDynamicProperties.fundingAccountAddress()).willReturn(fundingAccount);

        // uses default setup
        evmTxProcessor.setupFields(Bytes.EMPTY, false);

        evmTxProcessor.execute(sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver);
        assertEquals(EVM_VERSION_0_30, mcpVersion);
        assertEquals(EVM_VERSION_0_30, ccpVersion);

        // version changes, but dynamic not set
        evmTxProcessor.execute(sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver);
        assertEquals(EVM_VERSION_0_30, mcpVersion);
        assertEquals(EVM_VERSION_0_30, ccpVersion);

        // version changes, dynamic set
        evmTxProcessor.execute(sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver);
        assertEquals(EVM_VERSION_0_34, mcpVersion);
        assertEquals(EVM_VERSION_0_34, ccpVersion);

        // bad version
        assertThrows(
                NullPointerException.class,
                () -> evmTxProcessor.execute(sender, receiver, 33_333L, 1234L, 1L, Bytes.EMPTY, true, mirrorReceiver));
    }

    @Test
    void forwardsPayloadToGasCalculator() {
        final var mockedGasCalculator = mock(GasCalculator.class);
        evmTxProcessor.setGasCalculator(mockedGasCalculator);

        Bytes empty = Bytes.EMPTY;
        Bytes somePayload = Bytes.fromBase64String("9499rew9rwefdsfkad9cd09f0dscds0cds");

        // with empty bytes
        evmTxProcessor.setupFields(empty, true);
        verify(mockedGasCalculator).transactionIntrinsicGasCost(argThat(x -> x.equals(empty)), eq(true));

        // with actual payload
        evmTxProcessor.setupFields(somePayload, true);
        verify(mockedGasCalculator).transactionIntrinsicGasCost(argThat(x -> x.equals(somePayload)), eq(true));
    }
}
