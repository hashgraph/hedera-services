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
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ContractCreateHandlerTest extends ContractHandlerTestBase {
    @Mock
    private RootProxyWorldUpdater baseProxyWorldUpdater;

    @Mock
    private TransactionComponent component;

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionComponent.Factory factory;

    @Mock
    private ContextTransactionProcessor processor;

    @Mock
    private ContractCreateRecordBuilder recordBuilder;

    private ContractCreateHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ContractCreateHandler(() -> factory);
    }

    @Test
    void delegatesToCreatedComponentAndExposesSuccess() {
        given(factory.create(handleContext, HederaFunctionality.CONTRACT_CREATE))
                .willReturn(component);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.recordBuilder(ContractCreateRecordBuilder.class)).willReturn(recordBuilder);
        given(baseProxyWorldUpdater.getCreatedContractIds()).willReturn(List.of(CALLED_CONTRACT_ID));
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(expectedResult, SUCCESS_RESULT.finalStatus());
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCreateResult(expectedResult)).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void delegatesToCreatedComponentAndThrowsFailure() {
        given(factory.create(handleContext, HederaFunctionality.CONTRACT_CREATE))
                .willReturn(component);
        given(component.contextTransactionProcessor()).willReturn(processor);
        given(handleContext.recordBuilder(ContractCreateRecordBuilder.class)).willReturn(recordBuilder);
        final var expectedResult = HALT_RESULT.asProtoResultOf(baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(expectedResult, HALT_RESULT.finalStatus());
        given(processor.call()).willReturn(expectedOutcome);

        given(recordBuilder.contractID(null)).willReturn(recordBuilder);
        given(recordBuilder.contractCreateResult(expectedResult)).willReturn(recordBuilder);
        assertFailsWith(INVALID_SIGNATURE, () -> subject.handle(handleContext));
    }

    @Test
    @DisplayName("Adds valid admin key")
    void validAdminKey() throws PreCheckException {
        final var txn = contractCreateTransaction(adminKey, null);
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 1);
        assertThat(context.payerKey()).isEqualTo(payerKey);
        //        FUTURE: uncomment this after JKey removal
        //        assertIterableEquals(List.of(adminHederaKey), meta.requiredNonPayerKeys());
    }

    @Test
    @DisplayName("admin key with contractID is not added")
    void adminKeyWithContractID() throws PreCheckException {
        final var txn = contractCreateTransaction(adminContractKey, null);
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 0);
        assertThat(context.payerKey()).isEqualTo(payerKey);
    }

    @Test
    @DisplayName("autoRenew account key is added")
    void autoRenewAccountIdAdded() throws PreCheckException {
        final var txn = contractCreateTransaction(adminContractKey, autoRenewAccountId);
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 1);
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(autoRenewKey);
    }

    @Test
    @DisplayName("autoRenew account key is not added when it is sentinel value")
    void autoRenewAccountIdAsSentinelNotAdded() throws PreCheckException {
        final var txn = contractCreateTransaction(adminContractKey, asAccount("0.0.0"));
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 0);
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("autoRenew account and adminKey both added")
    void autoRenewAccountIdAndAdminBothAdded() throws PreCheckException {
        final var txn = contractCreateTransaction(adminKey, autoRenewAccountId);
        final var context = new FakePreHandleContext(accountStore, txn);
        subject.preHandle(context);

        basicMetaAssertions(context, 2);
        assertThat(context.payerKey()).isEqualTo(payerKey);
        //        FUTURE: uncomment this after JKey removal
        //        assertEquals(List.of(adminHederaKey, autoRenewHederaKey),
        // meta.requiredNonPayerKeys());
    }

    private TransactionBody contractCreateTransaction(final Key adminKey, final AccountID autoRenewId) {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        final var createTxnBody = ContractCreateTransactionBody.newBuilder().memo("Create Contract");
        if (adminKey != null) {
            createTxnBody.adminKey(adminKey);
        }

        if (autoRenewId != null) {
            if (!autoRenewId.equals(asAccount("0.0.0"))) {
                final var autoRenewAccount = mock(Account.class);
                given(accountStore.getAccountById(autoRenewId)).willReturn(autoRenewAccount);
                given(autoRenewAccount.key()).willReturn(autoRenewKey);
            }
            createTxnBody.autoRenewAccountId(autoRenewId);
        }

        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .contractCreateInstance(createTxnBody)
                .build();
    }
}
