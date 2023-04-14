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
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoApproveAllowanceHandlerTest extends CryptoHandlerTestBase {
    private final TokenID nft = TokenID.newBuilder().tokenNum(56789).build();
    private final TokenID token = TokenID.newBuilder().tokenNum(6789).build();
    private final AccountID spender = AccountID.newBuilder().accountNum(12345).build();
    private final AccountID delegatingSpender = AccountID.newBuilder().accountNum(1234567).build();
    private final AccountID owner = AccountID.newBuilder().accountNum(123456).build();
    private final Key ownerKey = A_COMPLEX_KEY;
    private final HederaKey ownerHederaKey = asHederaKey(ownerKey).get();
    private final CryptoAllowance cryptoAllowance =
            CryptoAllowance.newBuilder().spender(spender).owner(owner).amount(10L).build();
    private final TokenAllowance tokenAllowance =
            TokenAllowance.newBuilder()
                    .spender(spender)
                    .amount(10L)
                    .tokenId(token)
                    .owner(owner)
                    .build();
    @Mock private Account ownerAccount;

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

    @Test
    void cryptoApproveAllowanceVanilla() throws PreCheckException {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(owner.accountNum())))
                .willReturn(ownerAccount);
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(id, false);
        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(accountHederaKey, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoApproveAllowanceFailsWithInvalidOwner() throws PreCheckException {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(owner.accountNum())))
                .willReturn(null);

        final var txn = cryptoApproveAllowanceTransaction(id, false);
        final var context = new PreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ALLOWANCE_OWNER_ID);
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() throws PreCheckException {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(owner.accountNum())))
                .willReturn(ownerAccount);
        given(ownerAccount.key()).willReturn(ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(owner, false);
        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 0);
        assertEquals(ownerKey, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() throws PreCheckException {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(owner.accountNum())))
                .willReturn(ownerAccount);
        given(ownerAccount.key()).willReturn(ownerKey);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(delegatingSpender.accountNum())))
                .willReturn(account);

        final var txn = cryptoApproveAllowanceTransaction(id, true);
        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(ownerKey);
    }

    @Test
    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() throws PreCheckException {
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(owner.accountNum())))
                .willReturn(ownerAccount);
        given(ownerAccount.key()).willReturn(ownerKey);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(delegatingSpender.accountNum())))
                .willReturn(null);

        final var txn = cryptoApproveAllowanceTransaction(id, true);
        final var context = new PreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_DELEGATING_SPENDER);
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle());
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
