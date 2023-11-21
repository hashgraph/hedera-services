/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall.OutputFn.STANDARD_OUTPUT_FN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchForResponseCodeHtsCallTest extends HtsCallTestBase {
    @Mock
    private DispatchForResponseCodeHtsCall.FailureCustomizer failureCustomizer;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private DispatchGasCalculator dispatchGasCalculator;

    @Mock
    private ContractCallRecordBuilder recordBuilder;

    private DispatchForResponseCodeHtsCall<SingleTransactionRecordBuilder> subject;

    @BeforeEach
    void setUp() {
        subject = new DispatchForResponseCodeHtsCall<>(
                mockEnhancement(),
                gasCalculator,
                AccountID.DEFAULT,
                TransactionBody.DEFAULT,
                SingleTransactionRecordBuilder.class,
                verificationStrategy,
                dispatchGasCalculator,
                failureCustomizer,
                STANDARD_OUTPUT_FN);
    }

    @Test
    void successResultNotCustomized() {
        given(systemContractOperations.dispatch(
                        TransactionBody.DEFAULT,
                        verificationStrategy,
                        AccountID.DEFAULT,
                        ContractCallRecordBuilder.class))
                .willReturn(recordBuilder);
        given(dispatchGasCalculator.gasRequirement(
                        TransactionBody.DEFAULT, gasCalculator, mockEnhancement(), AccountID.DEFAULT))
                .willReturn(123L);
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var pricedResult = subject.execute();
        final var contractResult = pricedResult.fullResult().result().getOutput();
        assertArrayEquals(ReturnTypes.encodedRc(SUCCESS).array(), contractResult.toArray());

        verifyNoInteractions(failureCustomizer);
    }

    @Test
    void failureResultCustomized() {
        given(systemContractOperations.dispatch(
                        TransactionBody.DEFAULT,
                        verificationStrategy,
                        AccountID.DEFAULT,
                        ContractCallRecordBuilder.class))
                .willReturn(recordBuilder);
        given(dispatchGasCalculator.gasRequirement(
                        TransactionBody.DEFAULT, gasCalculator, mockEnhancement(), AccountID.DEFAULT))
                .willReturn(123L);
        given(recordBuilder.status()).willReturn(INVALID_ACCOUNT_ID).willReturn(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        given(failureCustomizer.customize(TransactionBody.DEFAULT, INVALID_ACCOUNT_ID, mockEnhancement()))
                .willReturn(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        final var pricedResult = subject.execute();
        final var contractResult = pricedResult.fullResult().result().getOutput();
        assertArrayEquals(
                ReturnTypes.encodedRc(INVALID_TREASURY_ACCOUNT_FOR_TOKEN).array(), contractResult.toArray());
    }
}
