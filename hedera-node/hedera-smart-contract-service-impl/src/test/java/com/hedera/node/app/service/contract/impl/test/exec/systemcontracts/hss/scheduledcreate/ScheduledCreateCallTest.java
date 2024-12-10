/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hss.scheduledcreate;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasPlus;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.ClassicCreatesCall.FIXED_GAS_COST;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_SCHEDULE_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CONTRACT_ACCOUNT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledCreateCallTest extends CallTestBase {

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private MessageFrame frame;

    @Mock
    private ContractCallStreamBuilder recordBuilder;

    private ScheduledCreateCall subject;
    private TransactionBody syntheticScheduleCreate;

    @Test
    void executionFailsWithInsufficientFee() {
        // given

        syntheticScheduleCreate = TransactionBody.newBuilder()
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                        .symbol("TEST")
                                        .name("Test Token")
                                        .treasury(SENDER_ID)
                                        .build())
                                .build())
                        .build())
                .build();

        prepareCall();
        given(frame.getValue()).willReturn(Wei.of(0));
        given(gasCalculator.feeCalculatorPriceInTinyBars(any(), any())).willReturn(1000L);
        given(gasCalculator.gasCostInTinybars(100000L)).willReturn(100L);
        given(systemContractOperations.externalizePreemptedDispatch(any(), any(), any()))
                .willReturn(recordBuilder);
        // when
        final var result = subject.execute(frame).fullResult().result();

        // then
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(RC_AND_ADDRESS_ENCODER
                        .encodeElements((long) INSUFFICIENT_TX_FEE.protoOrdinal(), ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }

    @Test
    void executionFailsWithMissingTokenSymbol() {
        // given
        syntheticScheduleCreate = TransactionBody.newBuilder()
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                        .name("Test Token")
                                        .treasury(SENDER_ID)
                                        .build())
                                .build())
                        .build())
                .build();

        prepareCall();

        given(frame.getValue()).willReturn(Wei.of(1100));
        given(gasCalculator.feeCalculatorPriceInTinyBars(any(), any())).willReturn(1000L);
        given(gasCalculator.gasCostInTinybars(100000L)).willReturn(100L);
        // when
        final var result = subject.execute(frame).fullResult();

        // then
        final var expectedOutput = gasOnly(
                        revertResult(MISSING_TOKEN_SYMBOL, FIXED_GAS_COST), MISSING_TOKEN_SYMBOL, false)
                .fullResult();
        assertEquals(State.REVERT, result.result().getState());
        assertEquals(expectedOutput.result().getOutput(), result.result().getOutput());
    }

    @Test
    void executionFailsWithMissingTokenName() {
        // given
        syntheticScheduleCreate = TransactionBody.newBuilder()
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                        .symbol("TEST")
                                        .treasury(SENDER_ID)
                                        .build())
                                .build())
                        .build())
                .build();

        prepareCall();

        given(frame.getValue()).willReturn(Wei.of(1100));
        given(gasCalculator.feeCalculatorPriceInTinyBars(any(), any())).willReturn(1000L);
        given(gasCalculator.gasCostInTinybars(100000L)).willReturn(100L);
        // when
        final var result = subject.execute(frame).fullResult();

        // then
        final var expectedOutput = gasOnly(
                        revertResult(MISSING_TOKEN_NAME, FIXED_GAS_COST), MISSING_TOKEN_SYMBOL, false)
                .fullResult();
        assertEquals(State.REVERT, result.result().getState());
        assertEquals(expectedOutput.result().getOutput(), result.result().getOutput());
    }

    @Test
    void executionFailsWithMissingTokenTreasury() {
        // given
        syntheticScheduleCreate = TransactionBody.newBuilder()
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                        .symbol("TEST")
                                        .name("Test Token")
                                        .build())
                                .build())
                        .build())
                .build();

        prepareCall();

        given(frame.getValue()).willReturn(Wei.of(1100));
        given(gasCalculator.feeCalculatorPriceInTinyBars(any(), any())).willReturn(1000L);
        given(gasCalculator.gasCostInTinybars(100000L)).willReturn(100L);
        // when
        final var result = subject.execute(frame).fullResult();

        // then
        final var expectedOutput = gasOnly(
                        revertResult(INVALID_ACCOUNT_ID, FIXED_GAS_COST), MISSING_TOKEN_SYMBOL, false)
                .fullResult();
        assertEquals(State.REVERT, result.result().getState());
        assertEquals(expectedOutput.result().getOutput(), result.result().getOutput());
    }

    @Test
    void executeHappyPath() {
        // given
        syntheticScheduleCreate = TransactionBody.newBuilder()
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .tokenCreation(TokenCreateTransactionBody.newBuilder()
                                        .symbol("TEST")
                                        .name("Test Token")
                                        .treasury(SENDER_ID)
                                        .build())
                                .build())
                        .build())
                .build();

        prepareCall();

        given(frame.getValue()).willReturn(Wei.of(1100));
        given(gasCalculator.feeCalculatorPriceInTinyBars(any(), any())).willReturn(1000L);
        given(gasCalculator.gasCostInTinybars(100000L)).willReturn(100L);
        given(nativeOperations.getAccount(SENDER_ID)).willReturn(CONTRACT_ACCOUNT);
        given(systemContractOperations.dispatch(any(), any(), any(), any(), any(), any()))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SUCCESS);
        given(recordBuilder.scheduleID()).willReturn(CALLED_SCHEDULE_ID);
        // when
        final var result = subject.execute(frame).fullResult();

        // then
        final var encodedRespose = RC_AND_ADDRESS_ENCODER.encodeElements(
                (long) SUCCESS.protoOrdinal(), headlongAddressOf(CALLED_SCHEDULE_ID));
        final var expectedOutput = gasPlus(
                        successResult(encodedRespose, FIXED_GAS_COST + 100L, recordBuilder), SUCCESS, false, 1100L)
                .fullResult();
        assertEquals(State.COMPLETED_SUCCESS, result.result().getState());
        assertEquals(expectedOutput.result().getOutput(), result.result().getOutput());
    }

    private void prepareCall() {
        subject = new ScheduledCreateCall(
                gasCalculator,
                mockEnhancement(),
                verificationStrategy,
                syntheticScheduleCreate,
                SENDER_ID,
                (body, calc, enh, payer) -> 100L,
                Set.of());
    }
}
