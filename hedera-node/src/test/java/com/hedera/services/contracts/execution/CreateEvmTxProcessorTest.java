/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

import static com.hedera.services.contracts.ContractsV_0_30Module.EVM_VERSION_0_30;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
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
class CreateEvmTxProcessorTest {
    private static final int MAX_STACK_SIZE = 1024;

    @Mock private LivePricesSource livePricesSource;
    @Mock private HederaWorldState worldState;
    @Mock private CodeCache codeCache;
    @Mock private GlobalDynamicProperties globalDynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private Set<Operation> operations;
    @Mock private Transaction transaction;
    @Mock private HederaWorldState.Updater updater;
    @Mock private InHandleBlockMetaSource blockMetaSource;
    @Mock private HederaBlockValues hederaBlockValues;

    private CreateEvmTxProcessor createEvmTxProcessor;
    private final Account sender = new Account(new Id(0, 0, 1002));
    private final Account receiver = new Account(new Id(0, 0, 1006));
    private final Account relayer = new Account(new Id(0, 0, 1007));
    private final Instant consensusTime = Instant.now();
    private final int MAX_GAS_LIMIT = 10_000_000;
    private final int MAX_REFUND_PERCENT = 20;
    private final long INTRINSIC_GAS_COST = 290_000L;
    private final long GAS_LIMIT = 300_000L;

    @BeforeEach
    void setup() {
        CommonProcessorSetup.setup(gasCalculator);

        var operationRegistry = new OperationRegistry();
        MainnetEVMs.registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        operations.forEach(operationRegistry::put);
        when(globalDynamicProperties.evmVersion()).thenReturn(EVM_VERSION_0_30);
        var evm30 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
        Map<String, Provider<MessageCallProcessor>> mcps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new MessageCallProcessor(evm30, new PrecompileContractRegistry()));
        Map<String, Provider<ContractCreationProcessor>> ccps =
                Map.of(
                        EVM_VERSION_0_30,
                        () ->
                                new ContractCreationProcessor(
                                        gasCalculator, evm30, true, List.of(), 1));

        createEvmTxProcessor =
                new CreateEvmTxProcessor(
                        worldState,
                        livePricesSource,
                        codeCache,
                        globalDynamicProperties,
                        gasCalculator,
                        mcps,
                        ccps,
                        blockMetaSource);
    }

    @Test
    void assertSuccessfulExecution() {
        givenValidMock(true);
        givenSenderWithBalance(350_000L);
        var result =
                createEvmTxProcessor.execute(
                        sender,
                        receiver.getId().asEvmAddress(),
                        33_333L,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime);
        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(codeCache).invalidate(receiver.getId().asEvmAddress());
    }

    @Test
    void assertSuccessfulExecutionEth() {
        givenValidMockEth(true);

        var evmAccount = mock(EvmAccount.class);
        given(updater.getOrCreateSenderAccount(any())).willReturn(evmAccount);
        var senderMutableAccount = mock(MutableAccount.class);
        given(evmAccount.getMutable()).willReturn(senderMutableAccount);
        given(senderMutableAccount.getBalance()).willReturn(Wei.of(2000L));

        var result =
                createEvmTxProcessor.executeEth(
                        sender,
                        receiver.getId().asEvmAddress(),
                        33_333L,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        relayer,
                        BigInteger.valueOf(10_000L),
                        55_555L);
        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(codeCache).invalidate(receiver.getId().asEvmAddress());
    }

    @Test
    void assertSuccessExecutionChargesCorrectMinimumGas() {
        givenValidMock(true);
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
        givenSenderWithBalance(350_000L);
        var result =
                createEvmTxProcessor.execute(
                        sender,
                        receiver.getId().asEvmAddress(),
                        GAS_LIMIT,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime);
        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), GAS_LIMIT - GAS_LIMIT * MAX_REFUND_PERCENT / 100);
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void assertSuccessExecutionChargesCorrectGasWhenGasUsedIsLargerThanMinimum() {
        givenValidMock(true);
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(5);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, true))
                .willReturn(INTRINSIC_GAS_COST);
        givenSenderWithBalance(350_000L);
        var result =
                createEvmTxProcessor.execute(
                        sender,
                        receiver.getId().asEvmAddress(),
                        GAS_LIMIT,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime);
        assertTrue(result.isSuccessful());
        assertEquals(INTRINSIC_GAS_COST, result.getGasUsed());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void assertFailedExecution() {
        givenValidMock(false);
        // and:
        given(gasCalculator.mStoreOperationGasCost(any(), anyLong())).willReturn(200L);
        given(gasCalculator.mLoadOperationGasCost(any(), anyLong())).willReturn(30L);
        given(gasCalculator.memoryExpansionGasCost(any(), anyLong(), anyLong())).willReturn(5000L);
        givenSenderWithBalance(350_000L);

        // when:
        var result =
                createEvmTxProcessor.execute(
                        sender,
                        receiver.getId().asEvmAddress(),
                        33_333L,
                        0,
                        Bytes.fromHexString(
                                "6080604052348015600f57600080fd5b506000604e576040517f08c379a"
                                    + "00000000000000000000000000000000000000000000000000000000081"
                                    + "526004016045906071565b60405180910390fd5b60c9565b6000605d601"
                                    + "183608f565b915060668260a0565b602082019050919050565b60006020"
                                    + "8201905081810360008301526088816052565b9050919050565b6000828"
                                    + "25260208201905092915050565b7f636f756c64206e6f74206578656375"
                                    + "7465000000000000000000000000000000600082015250565b603f80610"
                                    + "0d76000396000f3fe6080604052600080fdfea2646970667358221220d8"
                                    + "2b5e4f0118f9b6972aae9287dfe93930fdbc1e62ca10ea7ac70bde1c0ad"
                                    + "d2464736f6c63430008070033"),
                        consensusTime);

        // then:
        assertFalse(result.isSuccessful());
    }

    @Test
    void assertIsContractCallFunctionality() {
        assertEquals(HederaFunctionality.ContractCreate, createEvmTxProcessor.getFunctionType());
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        doReturn(Optional.of(receiver.getId().asEvmAddress())).when(transaction).getTo();
        given(transaction.getSender()).willReturn(sender.getId().asEvmAddress());
        given(transaction.getValue()).willReturn(Wei.of(1L));
        final MessageFrame.Builder commonInitialFrame =
                MessageFrame.builder()
                        .messageFrameStack(mock(Deque.class))
                        .maxStackSize(MAX_STACK_SIZE)
                        .worldUpdater(mock(WorldUpdater.class))
                        .initialGas(GAS_LIMIT)
                        .originator(sender.getId().asEvmAddress())
                        .gasPrice(Wei.ZERO)
                        .sender(sender.getId().asEvmAddress())
                        .value(Wei.of(transaction.getValue().getAsBigInteger()))
                        .apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
                        .blockValues(mock(BlockValues.class))
                        .depth(0)
                        .completer(__ -> {})
                        .miningBeneficiary(Address.ZERO)
                        .blockHashLookup(h -> null);
        // when:
        MessageFrame buildMessageFrame =
                createEvmTxProcessor.buildInitialFrame(
                        commonInitialFrame, (Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

        // expect:
        assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
        assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
    }

    @Test
    void throwsWhenSenderCannotCoverUpfrontCost() {
        givenInvalidMock();
        givenSenderWithBalance(123);

        Address receiver = this.receiver.getId().asEvmAddress();
        assertFailsWith(
                () ->
                        createEvmTxProcessor.execute(
                                sender, receiver, 333_333L, 1234L, Bytes.EMPTY, consensusTime),
                ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimit() {
        givenInvalidMock();
        givenExtantSender();

        Address receiver = this.receiver.getId().asEvmAddress();
        assertFailsWith(
                () ->
                        createEvmTxProcessor.execute(
                                sender, receiver, 33_333L, 1234L, Bytes.EMPTY, consensusTime),
                INSUFFICIENT_GAS);
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimitAndGasLimitIsEqualToMaxGasLimit() {
        givenInvalidMock();
        givenExtantSender();
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, true))
                .willReturn(MAX_GAS_LIMIT + 1L);

        Address receiver = this.receiver.getId().asEvmAddress();
        assertFailsWith(
                () ->
                        createEvmTxProcessor.execute(
                                sender, receiver, 33_333L, 1234L, Bytes.EMPTY, consensusTime),
                INSUFFICIENT_GAS);
    }

    private void givenInvalidMock() {
        given(worldState.updater()).willReturn(updater);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, true)).willReturn(100_000L);
    }

    private void givenValidMock(boolean expectedSuccess) {
        given(worldState.updater()).willReturn(updater);
        given(worldState.updater().updater()).willReturn(updater);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        var evmAccount = mock(EvmAccount.class);

        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(evmAccount);
        given(worldState.updater()).willReturn(updater);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, true)).willReturn(0L);

        var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
        given(senderMutableAccount.getNonce()).willReturn(0L);
        given(senderMutableAccount.getCode()).willReturn(Bytes.EMPTY);

        if (expectedSuccess) {
            given(gasCalculator.codeDepositGasCost(0)).willReturn(0L);
        }
        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        given(updater.getSenderAccount(any())).willReturn(evmAccount);
        given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.getOrCreate(any())).willReturn(evmAccount);
        given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.getSbhRefund()).willReturn(0L);

        given(updater.getSenderAccount(any())).willReturn(evmAccount);
        given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.updater().getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
    }

    private void givenValidMockEth(boolean expectedSuccess) {
        given(worldState.updater()).willReturn(updater);
        given(worldState.updater().updater()).willReturn(updater);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        var evmAccount = mock(EvmAccount.class);

        given(worldState.updater()).willReturn(updater);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, true)).willReturn(0L);

        var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));
        given(senderMutableAccount.getNonce()).willReturn(0L);
        given(senderMutableAccount.getCode()).willReturn(Bytes.EMPTY);

        if (expectedSuccess) {
            given(gasCalculator.codeDepositGasCost(0)).willReturn(0L);
        }
        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        given(updater.getSenderAccount(any())).willReturn(evmAccount);
        given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.getOrCreate(any())).willReturn(evmAccount);
        given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.getSbhRefund()).willReturn(0L);

        given(updater.getSenderAccount(any())).willReturn(evmAccount);
        given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.updater().getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
    }

    private void givenExtantSender() {
        givenSenderWithBalance(-1L);
    }

    private void givenSenderWithBalance(final long amount) {
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);

        if (amount >= 0) {
            given(mutableSenderAccount.getBalance()).willReturn(Wei.of(amount));
        }
    }
}
