/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenPreTransactionHandlerImplTest {
    //    private static final TokenWipeAccountTransactionBody DEFAULT_TOKEN_WIPE_INSTANCE =
    //            TokenWipeAccountTransactionBody.newBuilder().build();
    //
    //    private static final TokenPauseTransactionBody DEFAULT_TOKEN_PAUSE_INSTANCE =
    //            TokenPauseTransactionBody.newBuilder().build();
    //
    //    private static final TokenUnpauseTransactionBody DEFAULT_TOKEN_UNPAUSE_INSTANCE =
    //            TokenUnpauseTransactionBody.newBuilder().build();
    //
    //    private final Timestamp consensusTimestamp =
    //            Timestamp.newBuilder().seconds(1_234_567L).build();
    //    private final AccountID payer = asAccount("0.0.3");
    //    private final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    //    private final HederaKey randomKey = asHederaKey(A_COMPLEX_KEY).get();
    //    private final Long payerNum = payer.accountNum().orElse(0L);
    //    private static final String ACCOUNTS = "ACCOUNTS";
    //    private static final String ALIASES = "ALIASES";
    //
    //    @Mock protected ReadableKVState<Long, MerkleAccount> aliases;
    //    @Mock protected ReadableKVState<Long, MerkleAccount> accounts;
    //    @Mock private ReadableStates states;
    //    @Mock private MerkleAccount payerAccount;
    //    @Mock private ReadableTokenStore tokenStore;
    //    @Mock private PreHandleContext context;
    //
    //    private ReadableAccountStore accountStore;
    //    private TokenPreTransactionHandlerImpl subject;
    //
    //    @BeforeEach
    //    void setUp() {
    //        given(states.<Long, MerkleAccount>get(ACCOUNTS)).willReturn(accounts);
    //        given(states.<Long, MerkleAccount>get(ALIASES)).willReturn(aliases);
    //        given(accounts.get(payerNum)).willReturn(payerAccount);
    //        given(payerAccount.getAccountKey()).willReturn((JKey) payerKey);
    //
    //        accountStore = new ReadableAccountStore(states);
    //
    //        subject = new TokenPreTransactionHandlerImpl(accountStore, tokenStore, context);
    //    }
    //
    //    @Test
    //    void tokenWipeVanilla() {
    //        final var wipeKey = (JKey) randomKey;
    //        final var txn = tokenWipeTransaction(true);
    //        final var expectedMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(txn)
    //                        .build();
    //
    //        given(tokenStore.getTokenMeta(any()))
    //                .willReturn(
    //                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
    //                                new ReadableTokenStore.TokenMetadata(
    //                                        null,
    //                                        null,
    //                                        Optional.of(wipeKey),
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        false,
    //                                        null),
    //                                null));
    //
    //        final var meta = subject.preHandleWipeTokenAccount(txn, payer);
    //
    //        assertEquals(expectedMeta.txnBody(), meta.txnBody());
    //        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
    //        basicMetadataAssertions(meta, 1, false, OK);
    //        assertEquals(payerKey, meta.payerKey());
    //        assertIterableEquals(List.of(wipeKey), meta.requiredNonPayerKeys());
    //    }
    //
    //    @Test
    //    void tokenWipeNoTokenFails() {
    //        final var txn = tokenWipeTransaction(false);
    //        final var expectedMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(txn)
    //                        .build();
    //
    //        final var meta = subject.preHandleWipeTokenAccount(txn, payer);
    //
    //        assertEquals(expectedMeta.txnBody(), meta.txnBody());
    //        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
    //        basicMetadataAssertions(meta, 0, true, INVALID_TOKEN_ID);
    //        assertEquals(payerKey, meta.payerKey());
    //    }
    //
    //    @Test
    //    void tokenWipeNoKeyAddedIfTokenMetaFailed() {
    //        final var txn = tokenWipeTransaction(true);
    //        final var expectedMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(txn)
    //                        .build();
    //
    //        given(tokenStore.getTokenMeta(any()))
    //                .willReturn(
    //                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
    //                                new ReadableTokenStore.TokenMetadata(
    //                                        null, null, null, null, null, null, null, false,
    // null),
    //                                INVALID_TOKEN_ID));
    //
    //        final var meta = subject.preHandleWipeTokenAccount(txn, payer);
    //
    //        assertEquals(expectedMeta.txnBody(), meta.txnBody());
    //        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
    //        basicMetadataAssertions(meta, 0, true, INVALID_TOKEN_ID);
    //        assertEquals(payerKey, meta.payerKey());
    //    }
    //
    //    @Test
    //    void tokenWipeNoWipeKeyFails() {
    //        final var txn = tokenWipeTransaction(true);
    //        final var expectedMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(txn)
    //                        .build();
    //
    //        given(tokenStore.getTokenMeta(any()))
    //                .willReturn(
    //                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
    //                                new ReadableTokenStore.TokenMetadata(
    //                                        null,
    //                                        null,
    //                                        Optional.empty(),
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        false,
    //                                        null),
    //                                null));
    //
    //        final var meta = subject.preHandleWipeTokenAccount(txn, payer);
    //
    //        assertEquals(expectedMeta.txnBody(), meta.txnBody());
    //        assertFalse(meta.requiredNonPayerKeys().contains(payerKey));
    //        basicMetadataAssertions(meta, 0, true, TOKEN_HAS_NO_WIPE_KEY);
    //        assertEquals(payerKey, meta.payerKey());
    //    }
    //
    //    @Test
    //    void tokenPauseUnpauseVanilla() {
    //        final var pauseKey = (JKey) randomKey;
    //        final var pauseTxn = tokenPauseTransaction(true);
    //        final var unpauseTxn = tokenUnpauseTransaction(true);
    //        final var expectedPauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(pauseTxn)
    //                        .build();
    //        final var expectedUnpauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(unpauseTxn)
    //                        .build();
    //
    //        given(tokenStore.getTokenMeta(any()))
    //                .willReturn(
    //                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
    //                                new ReadableTokenStore.TokenMetadata(
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        Optional.of(pauseKey),
    //                                        false,
    //                                        null),
    //                                null));
    //
    //        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
    //        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);
    //
    //        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
    //        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
    //        basicMetadataAssertions(pauseMeta, 1, false, OK);
    //        basicMetadataAssertions(unpauseMeta, 1, false, OK);
    //        assertEquals(payerKey, pauseMeta.payerKey());
    //        assertEquals(payerKey, unpauseMeta.payerKey());
    //        assertIterableEquals(List.of(pauseKey), pauseMeta.requiredNonPayerKeys());
    //        assertIterableEquals(List.of(pauseKey), unpauseMeta.requiredNonPayerKeys());
    //    }
    //
    //    @Test
    //    void tokenPauseUnpauseNoTokenFails() {
    //        final var pauseTxn = tokenPauseTransaction(false);
    //        final var unpauseTxn = tokenUnpauseTransaction(false);
    //        final var expectedPauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(pauseTxn)
    //                        .build();
    //        final var expectedUnpauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(unpauseTxn)
    //                        .build();
    //
    //        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
    //        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);
    //
    //        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
    //        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
    //        basicMetadataAssertions(pauseMeta, 0, true, INVALID_TOKEN_ID);
    //        basicMetadataAssertions(unpauseMeta, 0, true, INVALID_TOKEN_ID);
    //        assertEquals(payerKey, pauseMeta.payerKey());
    //        assertEquals(payerKey, unpauseMeta.payerKey());
    //    }
    //
    //    @Test
    //    void tokenPauseUnpauseNoKeyAddedIfTokenMetaFailed() {
    //        final var pauseTxn = tokenPauseTransaction(true);
    //        final var unpauseTxn = tokenUnpauseTransaction(true);
    //        final var expectedPauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(pauseTxn)
    //                        .build();
    //        final var expectedUnpauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(unpauseTxn)
    //                        .build();
    //
    //        given(tokenStore.getTokenMeta(any()))
    //                .willReturn(
    //                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
    //                                new ReadableTokenStore.TokenMetadata(
    //                                        null, null, null, null, null, null, null, false,
    // null),
    //                                INVALID_TOKEN_ID));
    //
    //        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
    //        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);
    //
    //        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
    //        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
    //        basicMetadataAssertions(pauseMeta, 0, true, INVALID_TOKEN_ID);
    //        basicMetadataAssertions(unpauseMeta, 0, true, INVALID_TOKEN_ID);
    //        assertEquals(payerKey, pauseMeta.payerKey());
    //        assertEquals(payerKey, unpauseMeta.payerKey());
    //    }
    //
    //    @Test
    //    void tokenPauseUnpauseNoPauseKeyFails() {
    //        final var pauseTxn = tokenPauseTransaction(true);
    //        final var unpauseTxn = tokenUnpauseTransaction(true);
    //        final var expectedPauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(pauseTxn)
    //                        .build();
    //        final var expectedUnpauseMeta =
    //                new SigTransactionMetadataBuilder(accountStore)
    //                        .payerKeyFor(payer)
    //                        .txnBody(unpauseTxn)
    //                        .build();
    //
    //        given(tokenStore.getTokenMeta(any()))
    //                .willReturn(
    //                        new ReadableTokenStore.TokenMetaOrLookupFailureReason(
    //                                new ReadableTokenStore.TokenMetadata(
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        null,
    //                                        Optional.empty(),
    //                                        false,
    //                                        null),
    //                                null));
    //
    //        final var pauseMeta = subject.preHandlePauseToken(pauseTxn, payer);
    //        final var unpauseMeta = subject.preHandleUnpauseToken(unpauseTxn, payer);
    //
    //        assertEquals(expectedPauseMeta.txnBody(), pauseMeta.txnBody());
    //        assertEquals(expectedUnpauseMeta.txnBody(), unpauseMeta.txnBody());
    //        basicMetadataAssertions(pauseMeta, 0, true, TOKEN_HAS_NO_PAUSE_KEY);
    //        basicMetadataAssertions(unpauseMeta, 0, true, TOKEN_HAS_NO_PAUSE_KEY);
    //        assertEquals(payerKey, pauseMeta.payerKey());
    //        assertEquals(payerKey, unpauseMeta.payerKey());
    //    }
    //
    //    private TransactionBody tokenWipeTransaction(boolean withToken) {
    //        final var transactionID =
    //                TransactionID.newBuilder()
    //                        .accountID(payer)
    //                        .transactionValidStart(consensusTimestamp);
    //        final var wipeTxBody =
    //                TokenWipeAccountTransactionBody.newBuilder()
    //                        .token(
    //                                TokenID.newBuilder()
    //                                        .tokenNum(666)
    //                                        .realmNum(0)
    //                                        .shardNum(0)
    //                                        .build());
    //
    //        return TransactionBody.newBuilder()
    //                .transactionID(transactionID)
    //                .tokenWipe(
    //                        withToken
    //                                ? wipeTxBody
    //                                : DEFAULT_TOKEN_WIPE_INSTANCE.copyBuilder())
    //                .build();
    //    }
    //
    //    private TransactionBody tokenPauseTransaction(boolean withToken) {
    //        final var transactionID =
    //                TransactionID.newBuilder()
    //                        .accountID(payer)
    //                        .transactionValidStart(consensusTimestamp);
    //        final var pauseTxBody =
    //                TokenPauseTransactionBody.newBuilder()
    //                        .token(
    //                                TokenID.newBuilder()
    //                                        .tokenNum(666)
    //                                        .realmNum(0)
    //                                        .shardNum(0)
    //                                        .build());
    //
    //        return TransactionBody.newBuilder()
    //                .transactionID(transactionID)
    //                .tokenPause(
    //                        withToken
    //                                ? pauseTxBody
    //                                : DEFAULT_TOKEN_PAUSE_INSTANCE.copyBuilder())
    //                .build();
    //    }
    //
    //    private TransactionBody tokenUnpauseTransaction(boolean withToken) {
    //        final var transactionID =
    //                TransactionID.newBuilder()
    //                        .accountID(payer)
    //                        .transactionValidStart(consensusTimestamp);
    //        final var unpauseTxBody =
    //                TokenUnpauseTransactionBody.newBuilder()
    //                        .token(
    //                                TokenID.newBuilder()
    //                                        .tokenNum(666)
    //                                        .realmNum(0)
    //                                        .shardNum(0)
    //                                        .build());
    //
    //        return TransactionBody.newBuilder()
    //                .transactionID(transactionID)
    //                .tokenUnpause(
    //                        withToken
    //                                ? unpauseTxBody
    //                                : DEFAULT_TOKEN_UNPAUSE_INSTANCE.copyBuilder())
    //                .build();
    //    }
    //
    //    private void basicMetadataAssertions(
    //            final TransactionMetadata meta,
    //            final int keysSize,
    //            final boolean failed,
    //            final ResponseCodeEnum failureStatus) {
    //        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
    //        assertEquals(failed, meta.failed());
    //        assertEquals(failureStatus, meta.status());
    //    }
    //
    //    private AccountID asAccount(String v) {
    //        String[] parts = v.split("[.]");
    //        long[] nativeParts = Stream.of(parts).mapToLong(Long::valueOf).toArray();
    //        return AccountID.newBuilder()
    //                .shardNum(nativeParts[0])
    //                .realmNum(nativeParts[1])
    //                .accountNum(nativeParts[2])
    //                .build();
    //    }
}
