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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoApproveAllowanceHandlerTest extends CryptoHandlerTestBase {
    @Mock
    private Account ownerAccount;

    private final NftAllowance nftAllowance = NftAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .tokenId(nft)
            .approvedForAll(Boolean.TRUE)
            .serialNumbers(List.of(1L, 2L))
            .build();
    private final NftAllowance nftAllowanceWithDelegatingSpender = NftAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .tokenId(nft)
            .approvedForAll(Boolean.FALSE)
            .serialNumbers(List.of(1L, 2L))
            .delegatingSpender(delegatingSpender)
            .build();

    private CryptoApproveAllowanceHandler subject = new CryptoApproveAllowanceHandler();

    @BeforeEach
    public void setUp() {
        super.setUp();
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(owner, ownerAccount)
                .value(id, account)
                .value(delegatingSpender, account)
                .build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStoreImpl(readableStates);
    }

    @Test
    void cryptoApproveAllowanceVanilla() throws PreCheckException {
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(id, false);
        final var context = new FakePreHandleContext(readableStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoApproveAllowanceFailsWithInvalidOwner() throws PreCheckException {
        readableAccounts = emptyReadableAccountStateBuilder().value(id, account).build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStoreImpl(readableStates);

        final var txn = cryptoApproveAllowanceTransaction(id, false);
        final var context = new FakePreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ALLOWANCE_OWNER_ID);
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() throws PreCheckException {
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(owner, false);
        final var context = new FakePreHandleContext(readableStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertEquals(ownerKey, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() throws PreCheckException {
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(id, true);
        final var context = new FakePreHandleContext(readableStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() throws PreCheckException {
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(id, account)
                .value(owner, ownerAccount)
                .build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableStore = new ReadableAccountStoreImpl(readableStates);
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(id, true);
        final var context = new FakePreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_DELEGATING_SPENDER);
    }

    private TransactionBody cryptoApproveAllowanceTransaction(
            final AccountID id, final boolean isWithDelegatingSpender) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(cryptoAllowance)
                .tokenAllowances(tokenAllowance)
                .nftAllowances(isWithDelegatingSpender ? nftAllowanceWithDelegatingSpender : nftAllowance)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoApproveAllowance(allowanceTxnBody)
                .build();
    }
}
