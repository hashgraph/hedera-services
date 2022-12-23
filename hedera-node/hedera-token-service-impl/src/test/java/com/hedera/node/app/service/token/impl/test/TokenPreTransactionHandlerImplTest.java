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
package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.TokenPreTransactionHandlerImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
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

    @Mock private ReadableKVState aliases;
    @Mock private ReadableKVState accounts;
    @Mock private ReadableStates states;
    @Mock private MerkleAccount payerAccount;
    @Mock private ReadableTokenStore tokenStore;
    @Mock private PreHandleContext context;

    private ReadableAccountStore accountStore;
    private TokenPreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        given(accounts.get(payerNum)).willReturn(payerAccount);
        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);

        accountStore = new ReadableAccountStore(states);

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
                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
                                new ReadableTokenStore.TokenMetadata(
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
        basicMetadataAssertions(meta, 1, false, OK);
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
        basicMetadataAssertions(meta, 0, true, INVALID_TOKEN_ID);
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
                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
                                new ReadableTokenStore.TokenMetadata(
                                        null, null, null, null, null, null, null, false, null),
                                INVALID_TOKEN_ID));

        final var meta = subject.preHandleWipeTokenAccount(txn, payer);

        assertEquals(expectedMeta.txnBody(), meta.txnBody());
        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
        basicMetadataAssertions(meta, 0, true, INVALID_TOKEN_ID);
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
                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
                                new ReadableTokenStore.TokenMetadata(
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
        basicMetadataAssertions(meta, 0, true, TOKEN_HAS_NO_WIPE_KEY);
        assertEquals(payerKey, meta.payerKey());
    }

    @Test
    void tokenPauseUnpauseVanilla() {
        final var pauseKey = (JKey) randomKey;
        final var pauseTxn = tokenPauseTransaction(true);
        final var unpauseTxn = tokenUnpauseTransaction(true);
        final var expectedPauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(pauseTxn)
                        .build();
        final var expectedUnpauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(unpauseTxn)
                        .build();

        given(tokenStore.getTokenMeta(any()))
                .willReturn(
                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
                                new ReadableTokenStore.TokenMetadata(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Optional.of(pauseKey),
                                        false,
                                        null),
                                null));

        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);

        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
        basicMetadataAssertions(pauseMeta, 1, false, OK);
        basicMetadataAssertions(unpauseMeta, 1, false, OK);
        assertEquals(payerKey, pauseMeta.payerKey());
        assertEquals(payerKey, unpauseMeta.payerKey());
        assertIterableEquals(List.of(pauseKey), pauseMeta.requiredNonPayerKeys());
        assertIterableEquals(List.of(pauseKey), unpauseMeta.requiredNonPayerKeys());
    }

    @Test
    void tokenPauseUnpauseNoTokenFails() {
        final var pauseTxn = tokenPauseTransaction(false);
        final var unpauseTxn = tokenUnpauseTransaction(false);
        final var expectedPauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(pauseTxn)
                        .build();
        final var expectedUnpauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(unpauseTxn)
                        .build();

        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);

        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
        basicMetadataAssertions(pauseMeta, 0, true, INVALID_TOKEN_ID);
        basicMetadataAssertions(unpauseMeta, 0, true, INVALID_TOKEN_ID);
        assertEquals(payerKey, pauseMeta.payerKey());
        assertEquals(payerKey, unpauseMeta.payerKey());
    }

    @Test
    void tokenPauseUnpauseNoKeyAddedIfTokenMetaFailed() {
        final var pauseTxn = tokenPauseTransaction(true);
        final var unpauseTxn = tokenUnpauseTransaction(true);
        final var expectedPauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(pauseTxn)
                        .build();
        final var expectedUnpauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(unpauseTxn)
                        .build();

        given(tokenStore.getTokenMeta(any()))
                .willReturn(
                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
                                new ReadableTokenStore.TokenMetadata(
                                        null, null, null, null, null, null, null, false, null),
                                INVALID_TOKEN_ID));

        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);

        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
        basicMetadataAssertions(pauseMeta, 0, true, INVALID_TOKEN_ID);
        basicMetadataAssertions(unpauseMeta, 0, true, INVALID_TOKEN_ID);
        assertEquals(payerKey, pauseMeta.payerKey());
        assertEquals(payerKey, unpauseMeta.payerKey());
    }

    @Test
    void tokenPauseUnpauseNoPauseKeyFails() {
        final var pauseTxn = tokenPauseTransaction(true);
        final var unpauseTxn = tokenUnpauseTransaction(true);
        final var expectedPauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(pauseTxn)
                        .build();
        final var expectedUnpauseMeta =
                new SigTransactionMetadataBuilder(accountStore)
                        .payerKeyFor(payer)
                        .txnBody(unpauseTxn)
                        .build();

        given(tokenStore.getTokenMeta(any()))
                .willReturn(
                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
                                new ReadableTokenStore.TokenMetadata(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Optional.empty(),
                                        false,
                                        null),
                                null));

        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);

        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
        basicMetadataAssertions(pauseMeta, 0, true, TOKEN_HAS_NO_PAUSE_KEY);
        basicMetadataAssertions(unpauseMeta, 0, true, TOKEN_HAS_NO_PAUSE_KEY);
        assertEquals(payerKey, pauseMeta.payerKey());
        assertEquals(payerKey, unpauseMeta.payerKey());
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

    private TransactionBody tokenPauseTransaction(boolean withToken) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var pauseTxBody =
                TokenPauseTransactionBody.newBuilder()
                        .setToken(
                                TokenID.newBuilder()
                                        .setTokenNum(666)
                                        .setRealmNum(0)
                                        .setShardNum(0)
                                        .build());

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setTokenPause(
                        withToken
                                ? pauseTxBody
                                : TokenPauseTransactionBody.getDefaultInstance().toBuilder())
                .build();
    }

    private TransactionBody tokenUnpauseTransaction(boolean withToken) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var unpauseTxBody =
                TokenUnpauseTransactionBody.newBuilder()
                        .setToken(
                                TokenID.newBuilder()
                                        .setTokenNum(666)
                                        .setRealmNum(0)
                                        .setShardNum(0)
                                        .build());

        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setTokenUnpause(
                        withToken
                                ? unpauseTxBody
                                : TokenUnpauseTransactionBody.getDefaultInstance().toBuilder())
                .build();
    }

    private void basicMetadataAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
        assertEquals(failed, meta.failed());
        assertEquals(failureStatus, meta.status());
    }
}
