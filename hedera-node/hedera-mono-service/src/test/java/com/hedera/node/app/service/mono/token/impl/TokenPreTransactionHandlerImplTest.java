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
package com.hedera.node.app.service.mono.token.impl;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenPreTransactionHandlerImplTest {
    private final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private final AccountID payer = asAccount("0.0.3");
    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    private final HederaKey randomKey = asHederaKey(A_COMPLEX_KEY).get();
    private final Long payerNum = payer.getAccountNum();
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";

    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock private States states;
    @Mock private MerkleAccount payerAccount;
    @Mock private TokenStore tokenStore;
    @Mock private PreHandleContext context;

    private AccountStore accountStore;
    private TokenPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        given(accounts.get(payerNum)).willReturn(Optional.of(payerAccount));
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);

        accountStore = new AccountStore(states);

        subject = new TokenPreTransactionHandlerImpl(accountStore, tokenStore, context);
    }

    @Test
    void tokenWipeVanilla() {
        final var wipeKey = (JKey) randomKey;
        final var txn = tokenWipeTransaction(true);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        given(tokenStore.getTokenMeta(any()))
                .willReturn(
                        new TokenStore.TokenMetaOrLookupFailureReason(
                                new TokenStore.TokenMetadata(
                                        null,
                                        null,
                                        Optional.of(wipeKey),
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        null),
                                null));

        final var meta = subject.preHandleWipeTokenAccount(txn, payer);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(meta, 1, false, OK);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(wipeKey), meta.requiredNonPayerKeys());
    }

    @Test
    void tokenWipeNoTokenFails() {
        final var txn = tokenWipeTransaction(false);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        final var meta = subject.preHandleWipeTokenAccount(txn, payer);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(meta, 0, true, INVALID_TOKEN_ID);
        assertEquals(payerKey, meta.payerKey());
    }

    @Test
    void tokenWipeNoKeyAddedIfTokenMetaFailed() {
        final var txn = tokenWipeTransaction(true);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        given(tokenStore.getTokenMeta(any()))
                .willReturn(
                        new TokenStore.TokenMetaOrLookupFailureReason(
                                new TokenStore.TokenMetadata(
                                        null, null, null, null, null, null, null, false, null),
                                INVALID_TOKEN_ID));

        final var meta = subject.preHandleWipeTokenAccount(txn, payer);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(meta, 0, true, INVALID_TOKEN_ID);
        assertEquals(payerKey, meta.payerKey());
    }

    @Test
    void tokenWipeNoWipeKeyFails() {
        final var txn = tokenWipeTransaction(true);
        final var expectedMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(txn)
                        .build();

        given(tokenStore.getTokenMeta(any()))
                .willReturn(
                        new TokenStore.TokenMetaOrLookupFailureReason(
                                new TokenStore.TokenMetadata(
                                        null,
                                        null,
                                        Optional.empty(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        null),
                                null));

        final var meta = subject.preHandleWipeTokenAccount(txn, payer);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetaAssertions(meta, 0, true, TOKEN_HAS_NO_WIPE_KEY);
        assertEquals(payerKey, meta.payerKey());
    }

    private TransactionBody tokenWipeTransaction(boolean withToken) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var wipeTxBody =
                TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(
                                TokenID.newBuilder()
                                        .setTokenNum(666)
                                        .setRealmNum(0)
                                        .setShardNum(0)
                                        .build());

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setTokenWipe(
                        withToken
                                ? wipeTxBody
                                : TokenWipeAccountTransactionBody.getDefaultInstance().toBuilder())
                .build();
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
        assertEquals(failed, meta.failed());
        assertEquals(failureStatus, meta.status());
    }
}
