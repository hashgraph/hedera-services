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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertFailsWith;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractUpdateHandlerTest extends ContractHandlerTestBase {

    final TransactionID transactionID = TransactionID.newBuilder()
            .accountID(payer)
            .transactionValidStart(consensusTimestamp)
            .build();

    @Mock
    private HandleContext context;

    @Mock
    private Account contract;

    @Mock
    private AttributeValidator attributeValidator;

    private ContractUpdateHandler subject;

    @BeforeEach
    public void setUp() {
        subject = new ContractUpdateHandler();
    }

    @Test
    void sigRequiredWithoutKeyFails() throws PreCheckException {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder())
                .transactionID(transactionID)
                .build();
        final var context = new FakePreHandleContext(accountStore, txn);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_CONTRACT_ID);
    }

    @Test
    void invalidAutoRenewAccountIdFails() throws PreCheckException {
        when(accountStore.getContractById(targetContract)).thenReturn(payerAccount);

        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder()
                                .contractID(targetContract)
                                .autoRenewAccountId(asAccount("0.0.11111")) // invalid account
                        )
                .transactionID(transactionID)
                .build();
        final var context = new FakePreHandleContext(accountStore, txn);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void handleWithNullContextFails() {
        final HandleContext context = null;
        assertThrows(NullPointerException.class, () -> subject.handle(context));
    }

    @Test
    void handleWithNullContractUpdateTransactionBodyFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance((ContractUpdateTransactionBody) null)
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertThrows(NullPointerException.class, () -> subject.handle(context));
    }

    @Test
    void handleWithNoContractIdFails() {
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder().contractID((ContractID) null))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertThrows(NullPointerException.class, () -> subject.handle(context));
    }

    @Test
    void handleWithNonExistingContractIdFails() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(
                        ContractUpdateTransactionBody.newBuilder().contractID(targetContract))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_CONTRACT_ID, () -> subject.handle(context));
    }

    @Test
    void handleWithInvalidKeyFails() {
        when(accountStore.getContractById(targetContract)).thenReturn(contract);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(Key.newBuilder()))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_ADMIN_KEY, () -> subject.handle(context));
    }

    @Test
    void handleWithInvalidContractIdKeyFails() {
        when(accountStore.getContractById(targetContract)).thenReturn(contract);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(Key.newBuilder().contractID(ContractID.DEFAULT)))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_ADMIN_KEY, () -> subject.handle(context));
    }

    @Test
    void handleWithAValidContractIdKeyFails() {
        when(accountStore.getContractById(targetContract)).thenReturn(contract);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(Key.newBuilder()
                                .contractID(ContractID.newBuilder().contractNum(100))))
                .transactionID(transactionID)
                .build();
        when(context.body()).thenReturn(txn);

        assertFailsWith(INVALID_ADMIN_KEY, () -> subject.handle(context));
    }

    @Test
    void handleWithInvalidExpirationTimeAndExpiredAndPendingRemovalTrueFails() {
        final var expirationTime = 1L;

        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        doReturn(attributeValidator).when(context).attributeValidator();
        doThrow(HandleException.class).when(attributeValidator).validateExpiry(expirationTime);
        when(contract.expiredAndPendingRemoval()).thenReturn(true);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .expirationTime(Timestamp.newBuilder().seconds(expirationTime)))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, () -> subject.handle(context));
    }

    @Test
    void handleModifyImmutableContract() {
        when(accountStore.getContractById(targetContract)).thenReturn(contract);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(MODIFYING_IMMUTABLE_CONTRACT, () -> subject.handle(context));
    }

    @Test
    void handleWithExpirationTimeLesserThenExpirationSecondsFails() {
        final var expirationTime = 1L;

        doReturn(attributeValidator).when(context).attributeValidator();
        when(accountStore.getContractById(targetContract)).thenReturn(contract);
        when(contract.key()).thenReturn(Key.newBuilder().build());
        when(contract.expirationSecond()).thenReturn(expirationTime + 1);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var txn = TransactionBody.newBuilder()
                .contractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .contractID(targetContract)
                        .adminKey(adminKey)
                        .memo("memo")
                        .expirationTime(Timestamp.newBuilder().seconds(expirationTime)))
                .transactionID(transactionID)
                .build();

        when(context.body()).thenReturn(txn);

        assertFailsWith(EXPIRATION_REDUCTION_NOT_ALLOWED, () -> subject.handle(context));
    }
}
