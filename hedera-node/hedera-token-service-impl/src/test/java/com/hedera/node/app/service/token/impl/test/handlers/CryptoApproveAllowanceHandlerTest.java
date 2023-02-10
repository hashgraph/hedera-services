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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.BoolValue;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoApproveAllowanceHandlerTest extends CryptoHandlerTestBase {
    private final TokenID nft = asToken("0.0.56789");
    private final TokenID token = asToken("0.0.6789");
    private final AccountID spender = asAccount("0.0.12345");
    private final AccountID delegatingSpender = asAccount("0.0.1234567");
    private final AccountID owner = asAccount("0.0.123456");
    private final HederaKey ownerKey = asHederaKey(A_COMPLEX_KEY).get();

    @Mock private MerkleAccount ownerAccount;

    private final CryptoAllowance cryptoAllowance =
            CryptoAllowance.newBuilder().setSpender(spender).setOwner(owner).setAmount(10L).build();
    private final TokenAllowance tokenAllowance =
            TokenAllowance.newBuilder()
                    .setSpender(spender)
                    .setAmount(10L)
                    .setTokenId(token)
                    .setOwner(owner)
                    .build();

    private final NftAllowance nftAllowance =
            NftAllowance.newBuilder()
                    .setSpender(spender)
                    .setOwner(owner)
                    .setTokenId(nft)
                    .setApprovedForAll(BoolValue.of(true))
                    .addAllSerialNumbers(List.of(1L, 2L))
                    .build();
    private final NftAllowance nftAllowanceWithDelegatingSpender =
            NftAllowance.newBuilder()
                    .setSpender(spender)
                    .setOwner(owner)
                    .setTokenId(nft)
                    .setApprovedForAll(BoolValue.of(false))
                    .addAllSerialNumbers(List.of(1L, 2L))
                    .setDelegatingSpender(delegatingSpender)
                    .build();

    private CryptoApproveAllowanceHandler subject = new CryptoApproveAllowanceHandler();

    @Test
    void cryptoApproveAllowanceVanilla() {
        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(payer, false);
        final var context = new PrehandleHandlerContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 3, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(
                List.of(ownerKey, ownerKey, ownerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceFailsWithInvalidOwner() {
        given(accounts.get(owner.getAccountNum())).willReturn(null);

        final var txn = cryptoApproveAllowanceTransaction(payer, false);
        final var context = new PrehandleHandlerContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 0, true, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() {
        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(owner, false);
        final var context = new PrehandleHandlerContext(store, txn, owner);
        subject.preHandle(context);
        basicMetaAssertions(context, 0, false, OK);
        assertEquals(ownerKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() {
        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
        given(accounts.get(delegatingSpender.getAccountNum())).willReturn(payerAccount);

        final var txn = cryptoApproveAllowanceTransaction(payer, true);
        final var context = new PrehandleHandlerContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 3, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(
                List.of(ownerKey, ownerKey, payerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() {
        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
        given(accounts.get(delegatingSpender.getAccountNum())).willReturn(null);

        final var txn = cryptoApproveAllowanceTransaction(payer, true);
        final var context = new PrehandleHandlerContext(store, txn, payer);
        subject.preHandle(context);
        assertEquals(payerKey, context.getPayerKey());
        basicMetaAssertions(context, 2, true, INVALID_DELEGATING_SPENDER);
        assertIterableEquals(List.of(ownerKey, ownerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    }

    private TransactionBody cryptoApproveAllowanceTransaction(
            final AccountID id, final boolean isWithDelegatingSpender) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(id)
                        .setTransactionValidStart(consensusTimestamp);
        final var allowanceTxnBody =
                CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addCryptoAllowances(cryptoAllowance)
                        .addTokenAllowances(tokenAllowance)
                        .addNftAllowances(
                                isWithDelegatingSpender
                                        ? nftAllowanceWithDelegatingSpender
                                        : nftAllowance)
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoApproveAllowance(allowanceTxnBody)
                .build();
    }
}
