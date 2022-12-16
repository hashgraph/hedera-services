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
package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.BALANCE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.records.RecordSubmissions;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.UpdateTrackingLedgerAccount;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.txns.crypto.AutoCreationLogic;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.AltBN128AddPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaMessageCallProcessorTest {

    private static final String HEDERA_PRECOMPILE_ADDRESS_STRING = "0x1337";
    private static final String HTS_PRECOMPILE_ADDRESS_STRING = "0x167";
    private static final Address HEDERA_PRECOMPILE_ADDRESS =
            Address.fromHexString(HEDERA_PRECOMPILE_ADDRESS_STRING);
    private static final Address HTS_PRECOMPILE_ADDRESS =
            Address.fromHexString(HTS_PRECOMPILE_ADDRESS_STRING);
    private static final Address RECIPIENT_ADDRESS = Address.fromHexString("0xcafecafe01");
    private static final BalanceChange BALANCE_CHANGE =
            BalanceChange.changingHbar(
                    AccountAmount.newBuilder()
                            .setAccountID(
                                    AccountID.newBuilder()
                                            .setAlias(
                                                    ByteStringUtils.wrapUnsafely(
                                                            RECIPIENT_ADDRESS.toArrayUnsafe()))
                                            .build())
                            .build(),
                    null);
    private static final Address SENDER_ADDRESS = Address.fromHexString("0xcafecafe02");
    private static final PrecompiledContract.PrecompileContractResult NO_RESULT =
            new PrecompiledContract.PrecompileContractResult(
                    null, true, MessageFrame.State.COMPLETED_FAILED, Optional.empty());
    private static final PrecompiledContract.PrecompileContractResult RESULT =
            new PrecompiledContract.PrecompileContractResult(
                    Bytes.of(1), true, MessageFrame.State.COMPLETED_SUCCESS, Optional.empty());
    private static final long GAS_ONE = 1L;
    private static final long GAS_ONE_K = 1_000L;
    private static final long GAS_ONE_M = 1_000_000L;
    private final Bytes output = Bytes.of("output".getBytes());
    HederaMessageCallProcessor subject;
    @Mock private EVM evm;
    @Mock private PrecompileContractRegistry precompiles;
    @Mock private MessageFrame frame;
    @Mock private OperationTracer operationTrace;
    @Mock private WorldUpdater worldUpdater;
    @Mock private PrecompiledContract nonHtsPrecompile;
    @Mock private HTSPrecompiledContract htsPrecompile;
    @Mock private HederaOperationTracer hederaTracer;
    @Mock private HederaStackedWorldStateUpdater updater;
    @Mock private AutoCreationLogic autoCreationLogic;
    @Mock private RecordSubmissions recordSubmissions;
    @Mock private InfrastructureFactory infrastructureFactory;

    @BeforeEach
    void setup() {
        subject =
                new HederaMessageCallProcessor(
                        evm,
                        precompiles,
                        Map.of(
                                HEDERA_PRECOMPILE_ADDRESS_STRING, nonHtsPrecompile,
                                HTS_PRECOMPILE_ADDRESS_STRING, htsPrecompile),
                        infrastructureFactory);
    }

    @Test
    void callsHederaPrecompile() {
        given(frame.getRemainingGas()).willReturn(1337L);
        given(frame.getInputData()).willReturn(Bytes.of(1));
        given(frame.getContractAddress()).willReturn(HEDERA_PRECOMPILE_ADDRESS);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), eq(frame))).willReturn(RESULT);

        subject.start(frame, hederaTracer);

        verify(nonHtsPrecompile).computePrecompile(Bytes.of(1), frame);
        verify(nonHtsPrecompile).getName();
        verify(hederaTracer).tracePrecompileCall(frame, GAS_ONE, Bytes.of(1));
        verify(hederaTracer).tracePrecompileResult(frame, ContractActionType.SYSTEM);
        verify(frame).decrementRemainingGas(GAS_ONE);
        verify(frame).setOutputData(Bytes.of(1));
        verify(frame).setState(COMPLETED_SUCCESS);
        verify(frame, times(1)).getState();
        verifyNoMoreInteractions(nonHtsPrecompile, frame, hederaTracer);
    }

    @Test
    void treatsHtsPrecompileSpecial() {
        given(frame.getRemainingGas()).willReturn(1337L);
        given(frame.getInputData()).willReturn(Bytes.of(1));
        given(frame.getContractAddress()).willReturn(HTS_PRECOMPILE_ADDRESS);
        given(frame.getState()).willReturn(CODE_SUCCESS);
        given(htsPrecompile.getName()).willReturn("HTS");
        given(htsPrecompile.computeCosted(any(), eq(frame)))
                .willReturn(Pair.of(GAS_ONE, Bytes.of(1)));

        subject.start(frame, hederaTracer);

        verify(frame, times(1)).getState();
        verify(hederaTracer).tracePrecompileCall(frame, GAS_ONE, Bytes.of(1));
        verify(hederaTracer).tracePrecompileResult(frame, ContractActionType.SYSTEM);
        verify(frame).decrementRemainingGas(GAS_ONE);
        verify(frame).setOutputData(Bytes.of(1));
        verify(frame).setState(COMPLETED_SUCCESS);
        verifyNoMoreInteractions(htsPrecompile, frame, hederaTracer);
    }

    @Test
    void callsParent() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));
        doCallRealMethod().when(frame).setState(CODE_EXECUTING);

        subject.start(frame, hederaTracer);

        verify(hederaTracer, never()).tracePrecompileResult(frame, ContractActionType.PRECOMPILE);
        verifyNoMoreInteractions(nonHtsPrecompile, frame);
    }

    @Test
    void callsParentWithNonTokenAccountReceivingNoValue() {
        final var sender = new UpdateTrackingLedgerAccount<>(SENDER_ADDRESS, null);
        sender.setBalance(Wei.of(123));
        final var receiver = new UpdateTrackingLedgerAccount<>(RECIPIENT_ADDRESS, null);

        given(frame.getWorldUpdater()).willReturn(updater);
        given(frame.getValue()).willReturn(Wei.of(123));
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));
        given(updater.getSenderAccount(frame)).willReturn(sender);
        given(updater.getOrCreate(RECIPIENT_ADDRESS)).willReturn(receiver);
        given(updater.get(RECIPIENT_ADDRESS)).willReturn(receiver);
        doCallRealMethod().when(frame).setState(CODE_EXECUTING);

        subject.start(frame, hederaTracer);

        verify(hederaTracer, never()).tracePrecompileResult(frame, ContractActionType.PRECOMPILE);
        verifyNoMoreInteractions(nonHtsPrecompile, frame);
        assertEquals(123, receiver.getBalance().getAsBigInteger().longValue());
    }

    @Test
    void rejectsValueBeingSentToTokenAccount() {
        given(frame.getWorldUpdater()).willReturn(updater);
        given(frame.getValue()).willReturn(Wei.of(123));
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(true);
        doCallRealMethod().when(frame).setState(EXCEPTIONAL_HALT);

        subject.start(frame, hederaTracer);

        verify(frame)
                .setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
        verify(frame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
        verify(hederaTracer, never()).tracePrecompileResult(frame, ContractActionType.PRECOMPILE);
        verifyNoMoreInteractions(nonHtsPrecompile, frame);
    }

    @Test
    void callsParentWithPrecompile() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
        final var precompile = Address.fromHexString("0x1");
        given(frame.getContractAddress()).willReturn(precompile);
        given(precompiles.get(precompile))
                .willReturn(AltBN128AddPrecompiledContract.byzantium(null));

        subject.start(frame, hederaTracer);

        verify(hederaTracer).tracePrecompileResult(frame, ContractActionType.PRECOMPILE);
    }

    @Test
    void insufficientGasReverts() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(frame.getState()).willReturn(CODE_EXECUTING);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE_M);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, operationTrace);

        verify(frame).setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(frame).decrementRemainingGas(GAS_ONE_K);
        verify(nonHtsPrecompile).computePrecompile(Bytes.EMPTY, frame);
        verify(nonHtsPrecompile).getName();
        verify(nonHtsPrecompile).gasRequirement(Bytes.EMPTY);
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE_M, null);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, operationTrace);
    }

    @Test
    void precompileError() {
        given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(frame.getState()).willReturn(CODE_EXECUTING);
        given(nonHtsPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
        given(nonHtsPrecompile.computePrecompile(any(), any())).willReturn(NO_RESULT);

        subject.executeHederaPrecompile(nonHtsPrecompile, frame, operationTrace);

        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, null);
        verify(nonHtsPrecompile).getName();
        verify(nonHtsPrecompile).gasRequirement(Bytes.EMPTY);
        verifyNoMoreInteractions(nonHtsPrecompile, frame, operationTrace);
    }

    @Test
    void revertedPrecompileReturns() {
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
        given(htsPrecompile.computeCosted(any(), any())).willReturn(Pair.of(GAS_ONE, output));
        given(htsPrecompile.getName()).willReturn("HTS");
        given(frame.getState()).willReturn(REVERT);

        subject.executeHederaPrecompile(htsPrecompile, frame, operationTrace);

        verify(frame).getState();
        verify(htsPrecompile).computeCosted(Bytes.EMPTY, frame);
        verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, output);
        verifyNoMoreInteractions(htsPrecompile, frame, operationTrace);
    }

    @Test
    void executesLazyCreate() {
        given(frame.getSenderAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getValue()).willReturn(Wei.of(1000L));
        final var gasPrice = Wei.of(5);
        given(frame.getGasPrice()).willReturn(gasPrice);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(false);
        given(updater.get(RECIPIENT_ADDRESS)).willReturn(null);
        final var initialGas = 1_000_000L;
        given(frame.getRemainingGas()).willReturn(initialGas);
        final var transactionalLedger = mock(TransactionalLedger.class);
        given(updater.trackingAccounts()).willReturn(transactionalLedger);
        final var creationFee = 500L;
        given(infrastructureFactory.newAutoCreationLogicScopedTo(updater))
                .willReturn(autoCreationLogic);
        given(infrastructureFactory.newRecordSubmissionsScopedTo(updater))
                .willReturn(recordSubmissions);
        given(
                        autoCreationLogic.create(
                                BALANCE_CHANGE, transactionalLedger, List.of(BALANCE_CHANGE)))
                .willReturn(Pair.of(ResponseCodeEnum.OK, creationFee));

        subject.start(frame, hederaTracer);

        verify(frame).decrementRemainingGas(creationFee / gasPrice.toLong());
        verify(autoCreationLogic).submitRecords(recordSubmissions);
        verify(updater)
                .trackLazilyCreatedAccount(
                        EntityIdUtils.asTypedEvmAddress(BALANCE_CHANGE.accountId()));
        verify(frame, times(2)).getState();
        verify(hederaTracer, never()).tracePrecompileCall(any(), anyLong(), any());
    }

    @Test
    void executesLazyCreateNotSufficientGas() {
        given(frame.getSenderAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getValue()).willReturn(Wei.of(1000L));
        final var gasPrice = Wei.of(5);
        given(frame.getGasPrice()).willReturn(gasPrice);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(false);
        given(updater.get(RECIPIENT_ADDRESS)).willReturn(null);
        final var initialGas = 50L;
        given(frame.getRemainingGas()).willReturn(initialGas);
        final var transactionalLedger = mock(TransactionalLedger.class);
        given(updater.trackingAccounts()).willReturn(transactionalLedger);
        final var change =
                BalanceChange.changingHbar(
                        AccountAmount.newBuilder()
                                .setAccountID(
                                        AccountID.newBuilder()
                                                .setAlias(
                                                        ByteStringUtils.wrapUnsafely(
                                                                RECIPIENT_ADDRESS.toArrayUnsafe()))
                                                .build())
                                .build(),
                        null);
        final var creationFee = 500L;
        given(infrastructureFactory.newAutoCreationLogicScopedTo(updater))
                .willReturn(autoCreationLogic);
        given(autoCreationLogic.create(change, transactionalLedger, List.of(change)))
                .willReturn(Pair.of(ResponseCodeEnum.OK, creationFee));

        subject.start(frame, hederaTracer);

        verify(frame).decrementRemainingGas(initialGas);
        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(frame, times(2)).getState();
        verify(hederaTracer).traceAccountCreationResult(frame, Optional.of(INSUFFICIENT_GAS));
        verify(transactionalLedger, never()).set(change.accountId(), BALANCE, 1000L);
        verifyNoMoreInteractions(autoCreationLogic);
        verify(hederaTracer, never()).tracePrecompileCall(any(), anyLong(), any());
    }

    @Test
    void lazyCreateFailsAsExpectedWhenAutoCreationWasNotSuccessful() {
        given(frame.getSenderAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
        given(frame.getValue()).willReturn(Wei.of(1000L));
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.isTokenAddress(RECIPIENT_ADDRESS)).willReturn(false);
        given(updater.get(RECIPIENT_ADDRESS)).willReturn(null);
        final var initialGas = 1_000_000L;
        given(frame.getRemainingGas()).willReturn(initialGas);
        final var transactionalLedger = mock(TransactionalLedger.class);
        given(updater.trackingAccounts()).willReturn(transactionalLedger);
        final BalanceChange change =
                BalanceChange.changingHbar(
                        AccountAmount.newBuilder()
                                .setAccountID(
                                        AccountID.newBuilder()
                                                .setAlias(
                                                        ByteStringUtils.wrapUnsafely(
                                                                RECIPIENT_ADDRESS.toArrayUnsafe()))
                                                .build())
                                .build(),
                        null);
        given(infrastructureFactory.newAutoCreationLogicScopedTo(updater))
                .willReturn(autoCreationLogic);
        given(autoCreationLogic.create(change, transactionalLedger, List.of(change)))
                .willReturn(
                        Pair.of(
                                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED,
                                0));

        subject.start(frame, hederaTracer);

        verify(frame).decrementRemainingGas(frame.getRemainingGas());
        verify(frame).setState(EXCEPTIONAL_HALT);
        verify(frame, times(2)).getState();
        verify(hederaTracer)
                .traceAccountCreationResult(frame, Optional.of(FAILURE_DURING_LAZY_ACCOUNT_CREATE));
        verifyNoMoreInteractions(autoCreationLogic);
        verify(hederaTracer, never()).tracePrecompileCall(any(), anyLong(), any());
    }
}
