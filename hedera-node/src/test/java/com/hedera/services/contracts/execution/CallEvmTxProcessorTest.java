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
import static com.hedera.services.contracts.ContractsV_0_31Module.EVM_VERSION_0_31;
import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.doCallRealMethod;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.traceability.ContractActionType;
import com.hedera.services.contracts.execution.traceability.HederaTracer;
import com.hedera.services.contracts.execution.traceability.SolidityAction;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.ResourceLimitException;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
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
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer.ExecuteOperation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallEvmTxProcessorTest {
    private static final int MAX_STACK_SIZE = 1024;
    public static final long ONE_HBAR = 100_000_000L;

    @Mock private LivePricesSource livePricesSource;
    @Mock private HederaWorldState worldState;
    @Mock private CodeCache codeCache;
    @Mock private GlobalDynamicProperties globalDynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private Set<Operation> operations;
    @Mock private Transaction transaction;
    @Mock private HederaWorldState.Updater updater;
    @Mock private HederaStackedWorldStateUpdater stackedUpdater;
    @Mock private AliasManager aliasManager;
    @Mock private HederaBlockValues hederaBlockValues;
    @Mock private BlockValues blockValues;
    @Mock private InHandleBlockMetaSource blockMetaSource;

    private final Account sender = new Account(new Id(0, 0, 1002));
    private final Account receiver = new Account(new Id(0, 0, 1006));
    private final Account relayer = new Account(new Id(0, 0, 1007));
    private final Address receiverAddress = receiver.getId().asEvmAddress();
    private final Instant consensusTime = Instant.now();
    private final int MAX_GAS_LIMIT = 10_000_000;
    private final int MAX_REFUND_PERCENT = 20;
    private final long INTRINSIC_GAS_COST = 290_000L;
    private final long GAS_LIMIT = 300_000L;

    private CallEvmTxProcessor callEvmTxProcessor;
    private String mcpVersion;
    private String ccpVersion;

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
                        () -> {
                            mcpVersion = EVM_VERSION_0_30;
                            return new MessageCallProcessor(
                                    evm30, new PrecompileContractRegistry());
                        },
                        EVM_VERSION_0_31,
                        () -> {
                            mcpVersion = EVM_VERSION_0_31;
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
                        EVM_VERSION_0_31,
                        () -> {
                            ccpVersion = EVM_VERSION_0_31;
                            return new ContractCreationProcessor(
                                    gasCalculator, evm30, true, List.of(), 1);
                        });

        callEvmTxProcessor =
                new CallEvmTxProcessor(
                        worldState,
                        livePricesSource,
                        codeCache,
                        globalDynamicProperties,
                        gasCalculator,
                        mcps,
                        ccps,
                        aliasManager,
                        blockMetaSource);
    }

    @Test
    void assertSuccessExecution() {
        givenValidMock();
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        givenSenderWithBalance(350_000L);
        var result =
                callEvmTxProcessor.execute(
                        sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);
        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void assertSuccessExecutionEth() {
        givenValidMockEth();

        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        var evmAccount = mock(EvmAccount.class);
        given(updater.getOrCreateSenderAccount(any())).willReturn(evmAccount);
        var senderMutableAccount = mock(MutableAccount.class);
        given(evmAccount.getMutable()).willReturn(senderMutableAccount);

        givenSenderWithBalance(350_000L);
        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        33_333L,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(10_000L),
                        relayer,
                        55_555L);
        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void nonCodeTxRequiresValue() {
        assertFailsWith(
                () ->
                        callEvmTxProcessor.buildInitialFrame(
                                MessageFrame.builder(), receiverAddress, Bytes.EMPTY, 0L),
                INVALID_ETHEREUM_TRANSACTION);
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

        var messageFrame =
                callEvmTxProcessor.buildInitialFrame(protoFrame, receiverAddress, Bytes.EMPTY, 33L);

        assertEquals(Code.EMPTY, messageFrame.getCode());
    }

    @Test
    void assertSuccessExecutionChargesCorrectMinimumGas() {
        givenValidMock();
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        givenSenderWithBalance(350_000L);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        var result =
                callEvmTxProcessor.execute(
                        sender, receiverAddress, GAS_LIMIT, 1234L, Bytes.EMPTY, consensusTime);
        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), GAS_LIMIT - GAS_LIMIT * MAX_REFUND_PERCENT / 100);
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void assertSuccessExecutionChargesCorrectGasWhenGasUsedIsLargerThanMinimum() {
        givenValidMock();
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENT);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false))
                .willReturn(INTRINSIC_GAS_COST);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        givenSenderWithBalance(350_000L);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        var result =
                callEvmTxProcessor.execute(
                        sender, receiverAddress, GAS_LIMIT, 1234L, Bytes.EMPTY, consensusTime);
        assertTrue(result.isSuccessful());
        assertEquals(INTRINSIC_GAS_COST, result.getGasUsed());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void assertSuccessExecutionPopulatesStateChanges() {
        givenValidMock();
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        given(globalDynamicProperties.enabledSidecars())
                .willReturn(EnumSet.of(SidecarType.CONTRACT_STATE_CHANGE));
        givenSenderWithBalance(350_000L);
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final var contractAddress = "0xffff";
        final var slot = 1L;
        final var oldSlotValue = 4L;
        final var newSlotValue = 255L;
        given(updater.getFinalStateChanges())
                .willReturn(
                        Map.of(
                                Address.fromHexString(contractAddress),
                                Map.of(
                                        UInt256.valueOf(slot),
                                        Pair.of(
                                                UInt256.valueOf(oldSlotValue),
                                                UInt256.valueOf(newSlotValue)))));

        final var result =
                callEvmTxProcessor.execute(
                        sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);

        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        assertEquals(1, result.getStateChanges().size());
        final var contractStateChange =
                result.getStateChanges()
                        .get(Address.fromHexString(contractAddress))
                        .get(UInt256.valueOf(slot));
        assertEquals(UInt256.valueOf(oldSlotValue), contractStateChange.getLeft());
        assertEquals(UInt256.valueOf(newSlotValue), contractStateChange.getRight());
    }

    @Test
    void assertSuccessExecutionWithDisabledTraceabilityDoNotPopulatesStorageChanges() {
        givenValidMock();
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        given(globalDynamicProperties.enabledSidecars()).willReturn(Collections.emptySet());
        givenSenderWithBalance(350_000L);
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        final var result =
                callEvmTxProcessor.execute(
                        sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);

        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        assertEquals(0, result.getStateChanges().size());

        verify(updater, never()).getFinalStateChanges();
    }

    @Test
    void assertSuccessExecutionPopulatesContractActionsWhenEnabled() {
        givenValidMock();
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        givenSenderWithBalance(350_000L);
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        final var action =
                new SolidityAction(ContractActionType.CALL, 500L, "input".getBytes(), 0L, 0);
        final var action2 =
                new SolidityAction(ContractActionType.CREATE, 5555L, "input2".getBytes(), 666L, 1);
        try (MockedConstruction<HederaTracer> ignored =
                Mockito.mockConstruction(
                        HederaTracer.class,
                        (mock, context) -> {
                            doCallRealMethod()
                                    .when(mock)
                                    .traceExecution(
                                            any(MessageFrame.class), any(ExecuteOperation.class));
                            doReturn(List.of(action, action2)).when(mock).getActions();
                        })) {

            final var result =
                    callEvmTxProcessor.execute(
                            sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);

            assertEquals(List.of(action, action2), result.getActions());
        }
    }

    @Test
    void throwsWhenSenderCannotCoverUpfrontCost() {
        givenInvalidMock();
        givenSenderWithBalance(123);

        assertFailsWith(
                () ->
                        callEvmTxProcessor.execute(
                                sender,
                                receiverAddress,
                                333_333L,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime),
                ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimit() {
        givenInvalidMock();
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);

        assertFailsWith(
                () ->
                        callEvmTxProcessor.execute(
                                sender,
                                receiverAddress,
                                33_333L,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime),
                INSUFFICIENT_GAS);
    }

    @Test
    void throwsWhenIntrinsicGasCostExceedsGasLimitAndGasLimitIsEqualToMaxGasLimit() {
        givenInvalidMock();
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false))
                .willReturn(MAX_GAS_LIMIT + 1L);

        assertFailsWith(
                () ->
                        callEvmTxProcessor.execute(
                                sender,
                                receiverAddress,
                                MAX_GAS_LIMIT,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime),
                INSUFFICIENT_GAS);
    }

    @Test
    void assertIsContractCallFunctionality() {
        assertEquals(HederaFunctionality.ContractCall, callEvmTxProcessor.getFunctionType());
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        doReturn(Optional.of(receiver.getId().asEvmAddress())).when(transaction).getTo();
        given(codeCache.getIfPresent(any())).willReturn(Code.EMPTY);
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
                callEvmTxProcessor.buildInitialFrame(
                        commonInitialFrame, (Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

        // expect:
        assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
        assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
    }

    @Test
    void assertSuccessEthereumTransactionExecutionChargesBothSenderAndRelayerWithoutRefunds() {
        givenValidMockEth();
        final var MAX_REFUND_PERCENTAGE = 100;
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final long gasLimit = 1000;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(gasLimit);

        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        gasLimit,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
                        relayer,
                        10 * ONE_HBAR);

        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), gasLimit);
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(mutableSenderAccount).decrementBalance(Wei.of(offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount)
                .decrementBalance(Wei.of(gasPrice * gasLimit - offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount, never()).incrementBalance(any());
        verify(mutableSenderAccount, never()).incrementBalance(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void assertResourceExhaustionChargesBothSenderAndRelayerWithoutRefunds() {
        givenValidMockEth();
        final var mockAccounts =
                (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>)
                        mock(TransactionalLedger.class);
        given(updater.trackingAccounts()).willReturn(mockAccounts);

        willThrow(new ResourceLimitException(INSUFFICIENT_BALANCES_FOR_STORAGE_RENT))
                .given(updater)
                .commit();
        final var MAX_REFUND_PERCENTAGE = 100;
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final long gasLimit = 1000;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(gasLimit);

        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        gasLimit,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
                        relayer,
                        10 * ONE_HBAR);

        assertFalse(result.isSuccessful());
        assertEquals(result.getGasUsed(), gasLimit);
        verify(mutableSenderAccount, times(2)).decrementBalance(Wei.of(offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount, times(2))
                .decrementBalance(Wei.of(gasPrice * gasLimit - offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount, never()).incrementBalance(any());
        verify(mutableSenderAccount, never()).incrementBalance(any());
    }

    @Test
    void assertSuccessEthereumTransactionExecutionChargesBothSenderAndRelayerAndRefunds() {
        givenValidMockEth();
        final var MAX_REFUND_PERCENTAGE = 100;
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final int gasLimit = 1000;

        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        gasLimit,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
                        relayer,
                        10 * ONE_HBAR);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getGasUsed());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(mutableSenderAccount).decrementBalance(Wei.of(offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount)
                .decrementBalance(Wei.of(gasPrice * gasLimit - offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount)
                .incrementBalance(Wei.of(gasPrice * gasLimit - offeredGasPrice * gasLimit));
        verify(mutableSenderAccount).incrementBalance(Wei.of(offeredGasPrice * gasLimit));
    }

    @Test
    void
            assertSuccessEthereumTransactionExecutionChargesBothSenderAndRelayerAndRefundsOnlyRelayer() {
        givenValidMockEth();
        final var MAX_REFUND_PERCENTAGE = 1;
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final int gasLimit = 1000;

        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        gasLimit,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
                        relayer,
                        10 * ONE_HBAR);

        assertTrue(result.isSuccessful());
        assertEquals(gasLimit - gasLimit * MAX_REFUND_PERCENTAGE / 100, result.getGasUsed());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(mutableSenderAccount).decrementBalance(Wei.of(offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount)
                .decrementBalance(Wei.of(gasPrice * gasLimit - offeredGasPrice * gasLimit));
        verify(mutableRelayerAccount).incrementBalance(any());
        verify(mutableSenderAccount, never()).incrementBalance(any());
    }

    @Test
    void assertThrowsEthereumTransactionWhenSenderBalanceNotEnoughToCoverFeeWhenBothPay() {
        given(worldState.updater()).willReturn(updater);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
        given(worldState.updater()).willReturn(updater);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.ONE);
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final int gasLimit = 1000;
        final var userOfferedGasPrice =
                BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS);

        assertThrows(
                InvalidTransactionException.class,
                () ->
                        callEvmTxProcessor.executeEth(
                                sender,
                                receiverAddress,
                                gasLimit,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime,
                                userOfferedGasPrice,
                                relayer,
                                10 * ONE_HBAR));
    }

    @Test
    void assertThrowsEthereumTransactionWhenGasAllowanceNotEnoughWhenBothPay() {
        given(worldState.updater()).willReturn(updater);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
        given(worldState.updater()).willReturn(updater);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final int gasLimit = 1000;
        final var userOfferedGasPrice =
                BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS);

        assertThrows(
                InvalidTransactionException.class,
                () ->
                        callEvmTxProcessor.executeEth(
                                sender,
                                receiverAddress,
                                gasLimit,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime,
                                userOfferedGasPrice,
                                relayer,
                                100));
    }

    @Test
    void assertThrowsEthereumTransactionWhenRelayerBalanceNotEnoughToCoverAllowanceWhenBothPay() {
        given(worldState.updater()).willReturn(updater);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
        given(worldState.updater()).willReturn(updater);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(ONE_HBAR * 100));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.ONE);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 10L;
        final int gasLimit = 1000;
        final var userOfferedGasPrice =
                BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS);

        assertThrows(
                InvalidTransactionException.class,
                () ->
                        callEvmTxProcessor.executeEth(
                                sender,
                                receiverAddress,
                                gasLimit,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime,
                                userOfferedGasPrice,
                                relayer,
                                10 * ONE_HBAR));
    }

    @Test
    void assertSuccessEthereumTransactionExecutionChargesRelayerWhenSenderGasPriceIs0() {
        givenValidMockEth();
        final var MAX_REFUND_PERCENTAGE = 100;
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 0L;
        final long gasLimit = 1000;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(gasLimit);

        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        gasLimit,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
                        relayer,
                        10 * ONE_HBAR);

        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), gasLimit);
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(mutableRelayerAccount).decrementBalance(Wei.of(gasPrice * gasLimit));
    }

    @Test
    void assertThrowsEthereumTransactionWhenSenderGasPriceIs0AndAllowanceCannotCoverFees() {
        given(worldState.updater()).willReturn(updater);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
        given(worldState.updater()).willReturn(updater);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 0L;
        final int gasLimit = 1000;
        final var userOfferedGasPrice =
                BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS);

        assertThrows(
                InvalidTransactionException.class,
                () ->
                        callEvmTxProcessor.executeEth(
                                sender,
                                receiverAddress,
                                gasLimit,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime,
                                userOfferedGasPrice,
                                relayer,
                                100));
    }

    @Test
    void
            assertThrowsEthereumTransactionWhenSenderGasPriceIs0AndRelayerDoesNotHaveBalanceForAllowance() {
        given(worldState.updater()).willReturn(updater);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
        given(worldState.updater()).willReturn(updater);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        given(mutableRelayerAccount.getBalance()).willReturn(Wei.ONE);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 0L;
        final int gasLimit = 1000;
        final var userOfferedGasPrice =
                BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS);

        assertThrows(
                InvalidTransactionException.class,
                () ->
                        callEvmTxProcessor.executeEth(
                                sender,
                                receiverAddress,
                                gasLimit,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime,
                                userOfferedGasPrice,
                                relayer,
                                10 * ONE_HBAR));
    }

    @Test
    void
            assertSuccessEthereumTransactionWhenSenderGasPriceBiggerThanGasPriceAndChargesOnlySender() {
        givenValidMockEth();
        final var MAX_REFUND_PERCENTAGE = 100;
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(MAX_REFUND_PERCENTAGE);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(100 * ONE_HBAR));
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 50L;
        final long gasLimit = 1000;
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(gasLimit);

        var result =
                callEvmTxProcessor.executeEth(
                        sender,
                        receiverAddress,
                        gasLimit,
                        1234L,
                        Bytes.EMPTY,
                        consensusTime,
                        BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS),
                        relayer,
                        10 * ONE_HBAR);

        assertTrue(result.isSuccessful());
        assertEquals(result.getGasUsed(), gasLimit);
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
        verify(mutableSenderAccount).decrementBalance(Wei.of(gasPrice * gasLimit));
        verify(mutableRelayerAccount, never()).decrementBalance(Wei.of(gasPrice * gasLimit));
    }

    @Test
    void assertThrowsEthereumTransactionWhenSenderGasPriceBiggerThanGasPriceButBalanceNotEnough() {
        given(worldState.updater()).willReturn(updater);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);
        given(worldState.updater()).willReturn(updater);
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);
        given(mutableSenderAccount.getBalance()).willReturn(Wei.ONE);
        final var wrappedRelayerAccount = mock(EvmAccount.class);
        final var mutableRelayerAccount = mock(MutableAccount.class);
        given(wrappedRelayerAccount.getMutable()).willReturn(mutableRelayerAccount);
        given(updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress()))
                .willReturn(wrappedRelayerAccount);
        final long gasPrice = 40L;
        given(
                        livePricesSource.currentGasPrice(
                                consensusTime, HederaFunctionality.EthereumTransaction))
                .willReturn(gasPrice);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long offeredGasPrice = 50L;
        final int gasLimit = 1000;
        final var userOfferedGasPrice =
                BigInteger.valueOf(offeredGasPrice).multiply(WEIBARS_TO_TINYBARS);

        assertThrows(
                InvalidTransactionException.class,
                () ->
                        callEvmTxProcessor.executeEth(
                                sender,
                                receiverAddress,
                                gasLimit,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime,
                                userOfferedGasPrice,
                                relayer,
                                10 * ONE_HBAR));
    }

    @Test
    void assertSuccessExecutionWithRefund() {
        givenValidMock();
        given(globalDynamicProperties.maxGasRefundPercentage()).willReturn(100);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        givenSenderWithBalance(ONE_HBAR * 10);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        final long gasPrice = 10L;
        given(livePricesSource.currentGasPrice(consensusTime, HederaFunctionality.ContractCall))
                .willReturn(gasPrice);

        var result =
                callEvmTxProcessor.execute(
                        sender, receiverAddress, GAS_LIMIT, 1234L, Bytes.EMPTY, consensusTime);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getGasUsed());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    private void givenInvalidMock() {
        given(worldState.updater()).willReturn(updater);
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(100_000L);
    }

    private void givenValidMock() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);

        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(evmAccount);
        given(updater.getOrCreate(any())).willReturn(evmAccount);
        given(codeCache.getIfPresent(any())).willReturn(Code.EMPTY);

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

    private void givenValidMockEth() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);

        given(updater.getOrCreate(any())).willReturn(evmAccount);
        given(codeCache.getIfPresent(any())).willReturn(Code.EMPTY);

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

    private void givenSenderWithBalance(final long amount) {
        final var wrappedSenderAccount = mock(EvmAccount.class);
        final var mutableSenderAccount = mock(MutableAccount.class);
        given(wrappedSenderAccount.getMutable()).willReturn(mutableSenderAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(wrappedSenderAccount);

        given(mutableSenderAccount.getBalance()).willReturn(Wei.of(amount));
    }

    @Test
    void testEvmVersionLoading() {
        given(globalDynamicProperties.evmVersion()).willReturn(EVM_VERSION_0_31, "vDoesn'tExist");
        given(globalDynamicProperties.dynamicEvmVersion()).willReturn(false, false, true, true);

        givenValidMock();
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        givenSenderWithBalance(350_000L);

        // uses default setup
        callEvmTxProcessor.execute(
                sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);
        assertEquals(EVM_VERSION_0_30, mcpVersion);
        assertEquals(EVM_VERSION_0_30, ccpVersion);

        // version changes, but dynamic not set
        callEvmTxProcessor.execute(
                sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);
        assertEquals(EVM_VERSION_0_30, mcpVersion);
        assertEquals(EVM_VERSION_0_30, ccpVersion);

        // version changes, dynamic set
        callEvmTxProcessor.execute(
                sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);
        assertEquals(EVM_VERSION_0_31, mcpVersion);
        assertEquals(EVM_VERSION_0_31, ccpVersion);

        // bad version
        assertThrows(
                NullPointerException.class,
                () ->
                        callEvmTxProcessor.execute(
                                sender,
                                receiverAddress,
                                33_333L,
                                1234L,
                                Bytes.EMPTY,
                                consensusTime));
    }
}
