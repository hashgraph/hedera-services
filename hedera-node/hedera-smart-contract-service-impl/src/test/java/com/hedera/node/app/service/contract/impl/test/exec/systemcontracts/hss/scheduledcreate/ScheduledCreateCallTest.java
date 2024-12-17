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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasPlus;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_SCHEDULE_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CONTRACT_ACCOUNT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate.ScheduledCreateCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
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

    @Mock
    private HtsCallFactory htsCallFactory;

    private ScheduledCreateCall subject;
    private TransactionBody syntheticScheduleCreate;

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
        final var expectedOutput = gasPlus(successResult(encodedRespose, 100L, recordBuilder), SUCCESS, false, 1100L)
                .fullResult();
        assertEquals(State.COMPLETED_SUCCESS, result.result().getState());
        assertEquals(expectedOutput.result().getOutput(), result.result().getOutput());
    }

    private void prepareCall() {
        subject = new ScheduledCreateCall(
                gasCalculator,
                mockEnhancement(),
                verificationStrategy,
                SENDER_ID,
                (body, calc, enh, payer) -> 100L,
                Set.of(),
                Bytes.of(),
                htsCallFactory,
                false);
    }
}
