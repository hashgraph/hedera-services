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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HALT_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallHandlerTest extends ContractHandlerTestBase {
    @Mock
    private TransactionComponent component;

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionComponent.Factory factory;

    @Mock
    private ContextTransactionProcessor processor;

    @Mock
    private ContractCallRecordBuilder recordBuilder;

    @Mock
    private RootProxyWorldUpdater baseProxyWorldUpdater;

    private final ContractCallHandler subject = new ContractCallHandler(() -> factory);

    @Test
    void delegatesToCreatedComponentAndExposesSuccess() {
        given(factory.create(handleContext, HederaFunctionality.CONTRACT_CALL)).willReturn(component);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.recordBuilder(ContractCallRecordBuilder.class)).willReturn(recordBuilder);
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(expectedResult, SUCCESS_RESULT.finalStatus());
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCallResult(expectedResult)).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void delegatesToCreatedComponentAndThrowsOnFailure() {
        given(factory.create(handleContext, HederaFunctionality.CONTRACT_CALL)).willReturn(component);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.recordBuilder(ContractCallRecordBuilder.class)).willReturn(recordBuilder);
        final var expectedResult = HALT_RESULT.asProtoResultOf(baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(expectedResult, HALT_RESULT.finalStatus());
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.contractID(null)).willReturn(recordBuilder);
        given(recordBuilder.contractCallResult(expectedResult)).willReturn(recordBuilder);

        assertFailsWith(INVALID_SIGNATURE, () -> subject.handle(handleContext));
    }

    @Test
    @DisplayName("Succeeds for valid payer account")
    void validPayer() throws PreCheckException {
        final var txn = contractCallTransaction();
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertThat(context.payerKey()).isEqualTo(payerKey);
    }

    private TransactionBody contractCallTransaction() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .gas(1_234)
                        .amount(1_234L)
                        .contractID(targetContract))
                .build();
    }
}
