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

package com.hedera.node.app.state.recordcache;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.fixtures.state.FakeSchemaRegistry;
import com.hedera.node.app.spi.fixtures.state.ListWritableQueueState;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.WritableQueueState;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class RecordCacheImplTest {
    private static final int MAX_QUERYABLE_PER_ACCOUNT = 10;
    private static final TransactionReceipt UNHANDLED_RECEIPT =
            TransactionReceipt.newBuilder().status(UNKNOWN).build();
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1001).build();

    private DeduplicationCache dedupeCache;

    @Mock
    WorkingStateAccessor wsa;

    @Mock
    private ConfigProvider props;

    @BeforeEach
    void setUp(
            @Mock final VersionedConfiguration versionedConfig,
            @Mock final HederaConfig hederaConfig,
            @Mock final LedgerConfig ledgerConfig,
            @Mock final NetworkInfo networkInfo) {
        dedupeCache = new DeduplicationCacheImpl(props);
        final var registry = new FakeSchemaRegistry();
        final var state = new FakeHederaState();
        final var svc = new RecordCacheService();
        svc.registerSchemas(registry);
        registry.migrate(svc.getServiceName(), state, networkInfo);
        lenient().when(wsa.getHederaState()).thenReturn(state);
        lenient().when(props.getConfiguration()).thenReturn(versionedConfig);
        lenient().when(versionedConfig.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
        lenient().when(hederaConfig.transactionMaxValidDuration()).thenReturn(180L);
        lenient().when(versionedConfig.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        lenient().when(ledgerConfig.recordsMaxQueryableByAccount()).thenReturn(MAX_QUERYABLE_PER_ACCOUNT);
    }

    private TransactionID transactionID() {
        return transactionID(0);
    }

    private TransactionID transactionID(int nanos) {
        final var now = Instant.now();
        return TransactionID.newBuilder()
                .transactionValidStart(
                        Timestamp.newBuilder().seconds(now.getEpochSecond()).nanos(nanos))
                .accountID(PAYER_ACCOUNT_ID)
                .build();
    }

    @Test
    @DisplayName("Null args to constructor throw NPE")
    @SuppressWarnings("DataFlowIssue")
    void nullArgsToConstructorThrowNPE() {
        assertThatThrownBy(() -> new RecordCacheImpl(null, wsa, props)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RecordCacheImpl(dedupeCache, null, props))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RecordCacheImpl(dedupeCache, wsa, null)).isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("Rebuilds from state")
    final class RebuildTests {
        @Test
        @DisplayName("Construction adds all entries in state to the in memory data structures")
        void reloadsIntoCacheOnConstruction() {
            // Given a state with some entries BEFORE the cache is constructed
            final var payer1 = accountId(1001);
            final var payer2 = accountId(1002);

            final var txId1 = transactionID();
            final var txId2 = txId1.copyBuilder().accountID(payer2).build();

            final var entries = List.of(
                    new TransactionRecordEntry(0, payer1, transactionRecord(SUCCESS, txId1)),
                    new TransactionRecordEntry(1, payer2, transactionRecord(ACCOUNT_IS_IMMUTABLE, txId2)),
                    new TransactionRecordEntry(2, payer2, transactionRecord(DUPLICATE_TRANSACTION, txId2)),
                    new TransactionRecordEntry(3, payer1, transactionRecord(DUPLICATE_TRANSACTION, txId1)));

            final var state = wsa.getHederaState();
            assertThat(state).isNotNull();
            final var services = state.createWritableStates(RecordCacheService.NAME);
            final WritableQueueState<TransactionRecordEntry> queue =
                    services.getQueue(RecordCacheService.TXN_RECORD_QUEUE);
            assertThat(queue).isNotNull();
            entries.forEach(queue::add);
            ((ListWritableQueueState<?>) queue).commit();

            // When we create the cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);

            // Everything that was in state can be queried
            assertThat(cache.getRecord(txId1)).isEqualTo(entries.get(0).transactionRecord());
            assertThat(cache.getReceipt(txId1))
                    .isEqualTo(entries.get(0).transactionRecordOrThrow().receipt());
            assertThat(cache.getRecords(txId1))
                    .containsExactly(
                            entries.get(0).transactionRecord(), entries.get(3).transactionRecord());
            assertThat(cache.getRecords(payer1))
                    .containsExactly(
                            entries.get(0).transactionRecord(), entries.get(3).transactionRecord());
            assertThat(cache.getReceipts(txId1))
                    .containsExactly(
                            entries.get(0).transactionRecordOrThrow().receipt(),
                            entries.get(3).transactionRecordOrThrow().receipt());
            assertThat(cache.getReceipts(payer1))
                    .containsExactly(
                            entries.get(0).transactionRecordOrThrow().receipt(),
                            entries.get(3).transactionRecordOrThrow().receipt());

            assertThat(cache.getRecord(txId2)).isEqualTo(entries.get(1).transactionRecord());
            assertThat(cache.getReceipt(txId2))
                    .isEqualTo(entries.get(1).transactionRecordOrThrow().receipt());
            assertThat(cache.getRecords(txId2))
                    .containsExactly(
                            entries.get(1).transactionRecord(), entries.get(2).transactionRecord());
            assertThat(cache.getRecords(payer2))
                    .containsExactly(
                            entries.get(1).transactionRecord(), entries.get(2).transactionRecord());
            assertThat(cache.getReceipts(txId2))
                    .containsExactly(
                            entries.get(1).transactionRecordOrThrow().receipt(),
                            entries.get(2).transactionRecordOrThrow().receipt());
            assertThat(cache.getReceipts(payer2))
                    .containsExactly(
                            entries.get(1).transactionRecordOrThrow().receipt(),
                            entries.get(2).transactionRecordOrThrow().receipt());
        }

        @Test
        @DisplayName("Rebuild replaces all entries in the in memory data structures")
        void reloadsIntoCache() {
            // Given a state with some entries and a cache created with that state
            final var oldPayer = accountId(1003);
            final var oldTxId =
                    transactionID().copyBuilder().accountID(oldPayer).build();
            final var oldEntry = new TransactionRecordEntry(0, oldPayer, transactionRecord(SUCCESS, oldTxId));

            final var state = wsa.getHederaState();
            assertThat(state).isNotNull();
            final var services = state.createWritableStates(RecordCacheService.NAME);
            final WritableQueueState<TransactionRecordEntry> queue =
                    services.getQueue(RecordCacheService.TXN_RECORD_QUEUE);
            assertThat(queue).isNotNull();
            queue.add(oldEntry);
            ((ListWritableQueueState<?>) queue).commit();

            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);

            // When we replace the data "behind the scenes" (emulating a reconnect) and call rebuild
            final var payer1 = accountId(1001);
            final var payer2 = accountId(1002);

            final var txId1 = transactionID();
            final var txId2 = txId1.copyBuilder().accountID(payer2).build();

            final var entries = List.of(
                    new TransactionRecordEntry(0, payer1, transactionRecord(SUCCESS, txId1)),
                    new TransactionRecordEntry(1, payer2, transactionRecord(ACCOUNT_IS_IMMUTABLE, txId2)),
                    new TransactionRecordEntry(2, payer2, transactionRecord(DUPLICATE_TRANSACTION, txId2)),
                    new TransactionRecordEntry(3, payer1, transactionRecord(DUPLICATE_TRANSACTION, txId1)));

            assertThat(queue.poll()).isEqualTo(oldEntry);
            entries.forEach(queue::add);
            ((ListWritableQueueState<?>) queue).commit();

            cache.rebuild();

            // Then we find the new state is in the cache
            assertThat(cache.getRecord(txId1)).isEqualTo(entries.get(0).transactionRecord());
            assertThat(cache.getReceipt(txId1))
                    .isEqualTo(entries.get(0).transactionRecordOrThrow().receipt());
            assertThat(cache.getRecords(txId1))
                    .containsExactly(
                            entries.get(0).transactionRecord(), entries.get(3).transactionRecord());
            assertThat(cache.getRecords(payer1))
                    .containsExactly(
                            entries.get(0).transactionRecord(), entries.get(3).transactionRecord());
            assertThat(cache.getReceipts(txId1))
                    .containsExactly(
                            entries.get(0).transactionRecordOrThrow().receipt(),
                            entries.get(3).transactionRecordOrThrow().receipt());
            assertThat(cache.getReceipts(payer1))
                    .containsExactly(
                            entries.get(0).transactionRecordOrThrow().receipt(),
                            entries.get(3).transactionRecordOrThrow().receipt());

            assertThat(cache.getRecord(txId2)).isEqualTo(entries.get(1).transactionRecord());
            assertThat(cache.getReceipt(txId2))
                    .isEqualTo(entries.get(1).transactionRecordOrThrow().receipt());
            assertThat(cache.getRecords(txId2))
                    .containsExactly(
                            entries.get(1).transactionRecord(), entries.get(2).transactionRecord());
            assertThat(cache.getRecords(payer2))
                    .containsExactly(
                            entries.get(1).transactionRecord(), entries.get(2).transactionRecord());
            assertThat(cache.getReceipts(txId2))
                    .containsExactly(
                            entries.get(1).transactionRecordOrThrow().receipt(),
                            entries.get(2).transactionRecordOrThrow().receipt());
            assertThat(cache.getReceipts(payer2))
                    .containsExactly(
                            entries.get(1).transactionRecordOrThrow().receipt(),
                            entries.get(2).transactionRecordOrThrow().receipt());
            // And the old state is not in the cache
            assertThat(cache.getRecord(oldTxId)).isNull();
            assertThat(cache.getReceipt(oldTxId)).isNull();
            assertThat(cache.getRecords(oldTxId)).isEmpty();
            assertThat(cache.getReceipts(oldTxId)).isEmpty();
            assertThat(cache.getRecords(oldPayer)).isEmpty();
            assertThat(cache.getReceipts(oldPayer)).isEmpty();
        }

        private AccountID accountId(final int num) {
            return AccountID.newBuilder().accountNum(num).build();
        }

        private TransactionRecord transactionRecord(ResponseCodeEnum status, TransactionID txId) {
            return TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(TransactionReceipt.newBuilder().status(status))
                    .build();
        }
    }

    @Nested
    @DisplayName("Queries for receipts")
    final class ReceiptQueryTests {
        @Test
        @DisplayName("Query for receipt for no such txn returns null")
        void queryForReceiptForNoSuchTxnReturnsNull() {
            // Given a transaction unknown to the record cache and de-duplication cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var missingTxId = transactionID();

            // When we look up the receipt, then we get null
            assertThat(cache.getReceipt(missingTxId)).isNull();
        }

        @Test
        @DisplayName("Query for receipts for no such txn returns EMPTY LIST")
        void queryForReceiptsForNoSuchTxnReturnsNull() {
            // Given a transaction unknown to the record cache and de-duplication cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var missingTxId = transactionID();

            // When we look up the receipts, then we get an empty list
            assertThat(cache.getReceipts(missingTxId)).isEmpty();
        }

        @Test
        @DisplayName("Query for receipts for an account ID with no receipts returns EMPTY LIST")
        void queryForReceiptsForAccountWithNoRecords() {
            // Given a transaction unknown to the record cache and de-duplication cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);

            // When we look up the receipts, then we get an empty list
            assertThat(cache.getReceipts(PAYER_ACCOUNT_ID)).isEmpty();
        }

        @Test
        @DisplayName("Query for receipt for txn in UNKNOWN state returns UNKNOWN")
        void queryForReceiptForUnhandledTxnReturnsNull() {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var unhandledTxId = transactionID();
            dedupeCache.add(unhandledTxId);

            // When we query for the receipt, then we get the UNKNOWN receipt
            assertThat(cache.getReceipt(unhandledTxId)).isEqualTo(UNHANDLED_RECEIPT);
        }

        @Test
        @DisplayName("Query for receipts for txn in UNKNOWN state returns UNKNOWN")
        void queryForReceiptsForUnhandledTxnReturnsNull() {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var unhandledTxId = transactionID();
            dedupeCache.add(unhandledTxId);

            // When we query for the receipt, then we get the UNKNOWN receipt
            assertThat(cache.getReceipts(unhandledTxId)).containsExactly(UNHANDLED_RECEIPT);
        }

        @Test
        @DisplayName("Query for receipts by account ID for txn in UNKNOWN state returns EMPTY LIST")
        void queryForReceiptsForUnhandledTxnByAccountID() {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var unhandledTxId = transactionID();
            dedupeCache.add(unhandledTxId);

            // Then even though the txn DOES have the payer account ID, we don't return it, because the transaction has
            // not
            // yet been handled, and it is only at handle time that we know for sure who paid for the transaction. In
            // addition, we didn't want to add complexity to account based lookup on the de-duplication cache.
            assertThat(cache.getReceipts(PAYER_ACCOUNT_ID)).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("receiptStatusCodes")
        @DisplayName("Query for receipt for a txn with a proper record")
        void queryForReceiptForTxnWithRecord(@NonNull final ResponseCodeEnum status) {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            final var receipt = TransactionReceipt.newBuilder().status(status).build();
            final var record = TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(receipt)
                    .build();

            // When the record is added to the cache
            cache.add(0, PAYER_ACCOUNT_ID, record, Instant.now());

            // Then we can query for the receipt by transaction ID
            assertThat(cache.getReceipt(txId)).isEqualTo(receipt);
        }

        @ParameterizedTest
        @MethodSource("receiptStatusCodes")
        @DisplayName("Query for receipts for a txn with a proper record")
        void queryForReceiptsForTxnWithRecord(@NonNull final ResponseCodeEnum status) {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            final var receipt = TransactionReceipt.newBuilder().status(status).build();
            final var record = TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(receipt)
                    .build();

            // When the record is added to the cache
            cache.add(0, PAYER_ACCOUNT_ID, record, Instant.now());

            // Then we can query for the receipt by transaction ID
            assertThat(cache.getReceipts(txId)).containsExactly(receipt);
        }

        @ParameterizedTest
        @MethodSource("receiptStatusCodes")
        @DisplayName("Query for receipts for an account ID with a proper record")
        void queryForReceiptsForAccountIdWithRecord(@NonNull final ResponseCodeEnum status) {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            final var receipt = TransactionReceipt.newBuilder().status(status).build();
            final var record = TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(receipt)
                    .build();

            // When the record is added to the cache
            cache.add(0, PAYER_ACCOUNT_ID, record, Instant.now());

            // Then we can query for the receipt by transaction ID
            assertThat(cache.getReceipts(PAYER_ACCOUNT_ID)).containsExactly(receipt);
        }

        @ParameterizedTest
        @ValueSource(ints = {20, 30, 40})
        @DisplayName(
                "Only up to recordsMaxQueryableByAccount receipts are returned for an account ID with multiple records")
        void queryForManyReceiptsForAccountID(final int numRecords) {
            // Given a number of transactions with several records each, all for the same payer
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            // Normally consensus time is AFTER the transaction ID time by a couple of seconds
            var consensusTime = Instant.now().plusSeconds(2);
            for (int i = 0; i < numRecords; i++) {
                final var txId = transactionID(i);
                for (int j = 0; j < 3; j++) {
                    consensusTime = consensusTime.plus(1, ChronoUnit.NANOS);
                    final var status = j == 0 ? OK : DUPLICATE_TRANSACTION;
                    final var receipt =
                            TransactionReceipt.newBuilder().status(status).build();
                    final var record = TransactionRecord.newBuilder()
                            .transactionID(txId)
                            .receipt(receipt)
                            .build();
                    cache.add(0, PAYER_ACCOUNT_ID, record, consensusTime);
                }
            }

            // When we query for the receipts for the payer account ID
            final var receipts = cache.getReceipts(PAYER_ACCOUNT_ID);

            // Then we get back the most recent recordsMaxQueryableByAccount receipts
            assertThat(receipts).hasSize(MAX_QUERYABLE_PER_ACCOUNT);
        }

        static Stream<Arguments> receiptStatusCodes() {
            final var allValues = new HashSet<>(Arrays.asList(ResponseCodeEnum.values()));
            allValues.remove(UNKNOWN);
            return allValues.stream().map(Arguments::of);
        }
    }

    @Nested
    @DisplayName("Query for records")
    final class RecordQueryTests {
        @Test
        @DisplayName("Query for record for unknown txn returns null")
        void queryForRecordForUnknownTxnReturnsNull() {
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var missingTxId = transactionID();

            assertThat(cache.getRecord(missingTxId)).isNull();
        }

        @Test
        @DisplayName("Query for records for unknown txn returns EMPTY LIST")
        void queryForRecordsForUnknownTxnReturnsNull() {
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var missingTxId = transactionID();

            assertThat(cache.getRecords(missingTxId)).isEmpty();
        }

        @Test
        @DisplayName("Query for record for account ID with no receipts returns EMPTY LIST")
        void queryForRecordByAccountForUnknownTxn() {
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);

            assertThat(cache.getRecords(PAYER_ACCOUNT_ID)).isEmpty();
        }

        @Test
        @DisplayName("Query for record for tx with receipt in UNKNOWN state returns null")
        void queryForRecordForUnknownTxn() {
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            dedupeCache.add(txId);

            assertThat(cache.getRecord(txId)).isNull();
        }

        @Test
        @DisplayName("Query for records for tx with receipt in UNKNOWN state returns EMPTY LIST")
        void queryForRecordsForUnknownTxn() {
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            dedupeCache.add(txId);

            assertThat(cache.getRecords(txId)).isEmpty();
        }

        @Test
        @DisplayName("Query for records for tx by account ID with receipt in UNKNOWN state returns EMPTY LIST")
        void queryForRecordsByAccountForUnknownTxn() {
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            dedupeCache.add(txId);

            assertThat(cache.getRecords(PAYER_ACCOUNT_ID)).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("receiptStatusCodes")
        @DisplayName("Query for record for a txn with a proper record")
        void queryForRecordForTxnWithRecord(@NonNull final ResponseCodeEnum status) {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            final var receipt = TransactionReceipt.newBuilder().status(status).build();
            final var record = TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(receipt)
                    .build();

            // When the record is added to the cache
            cache.add(0, PAYER_ACCOUNT_ID, record, Instant.now());

            // Then we can query for the receipt by transaction ID
            assertThat(cache.getRecord(txId)).isEqualTo(record);
        }

        @ParameterizedTest
        @MethodSource("receiptStatusCodes")
        @DisplayName("Query for records for a txn with a proper record")
        void queryForRecordsForTxnWithRecord(@NonNull final ResponseCodeEnum status) {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            final var receipt = TransactionReceipt.newBuilder().status(status).build();
            final var record = TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(receipt)
                    .build();

            // When the record is added to the cache
            cache.add(0, PAYER_ACCOUNT_ID, record, Instant.now());

            // Then we can query for the receipt by transaction ID
            assertThat(cache.getRecords(txId)).containsExactly(record);
        }

        @ParameterizedTest
        @MethodSource("receiptStatusCodes")
        @DisplayName("Query for records for an account ID with a proper record")
        void queryForRecordsForAccountIdWithRecord(@NonNull final ResponseCodeEnum status) {
            // Given a transaction known to the de-duplication cache but not the record cache
            final var cache = new RecordCacheImpl(dedupeCache, wsa, props);
            final var txId = transactionID();
            final var receipt = TransactionReceipt.newBuilder().status(status).build();
            final var record = TransactionRecord.newBuilder()
                    .transactionID(txId)
                    .receipt(receipt)
                    .build();

            // When the record is added to the cache
            cache.add(0, PAYER_ACCOUNT_ID, record, Instant.now());

            // Then we can query for the receipt by transaction ID
            assertThat(cache.getRecords(PAYER_ACCOUNT_ID)).containsExactly(record);
        }

        static Stream<Arguments> receiptStatusCodes() {
            final var allValues = new HashSet<>(Arrays.asList(ResponseCodeEnum.values()));
            allValues.remove(UNKNOWN);
            return allValues.stream().map(Arguments::of);
        }
    }
}
