/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.expiry;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.TxnIdRecentHistory;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ExpiryManagerTest {
    private final long now = 1_234_567L;
    private final long start = now - 180L;
    private final long firstThen = now - 1;
    private final long secondThen = now + 1;
    private final AccountID aGrpcId = IdUtils.asAccount("0.0.2");
    private final AccountID bGrpcId = IdUtils.asAccount("0.0.4");
    private final EntityNum aKey = EntityNum.fromAccountId(aGrpcId);
    private final MerkleAccount anAccount = new MerkleAccount();

    private final MerkleMap<EntityNum, MerkleAccount> liveAccounts = new MerkleMap<>();
    private final Map<TransactionID, TxnIdRecentHistory> liveTxnHistories = new HashMap<>();

    private final FCQueue<ExpirableTxnRecord> liveRecords = new FCQueue<>();
    private final Map<EntityNum, Queue<ExpirableTxnRecord>> liveQueryableRecords = new HashMap<>();

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private ExpiryManager subject;

    @Test
    void rebuildsExpectedRecordsFromStateWithoutConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(liveAccounts)));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
        final var leftoverTxnId = recordWith(bGrpcId, now).getTxnId().toGrpc();
        liveTxnHistories.put(leftoverTxnId, new TxnIdRecentHistory());
        anAccount.records().offer(expiring(recordWith(aGrpcId, start), firstThen));
        anAccount.records().offer(expiring(recordWith(aGrpcId, start), secondThen));
        liveAccounts.put(aKey, anAccount);

        subject.reviewExistingPayerRecords();

        // then:
        assertFalse(liveTxnHistories.containsKey(leftoverTxnId));
        assertEquals(firstThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
        assertEquals(
                secondThen,
                liveTxnHistories.get(newTxnId).allDuplicateRecords().get(0).getExpiry());
    }

    @Test
    void rebuildsExpectedRecordsFromStateWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
        final var leftoverTxnId = recordWith(bGrpcId, now).getTxnId().toGrpc();
        liveTxnHistories.put(leftoverTxnId, new TxnIdRecentHistory());
        liveQueryableRecords.put(aKey, new LinkedList<>(List.of(expiring(recordWith(bGrpcId, now), firstThen))));
        final var priorityExpiring = expiring(recordWith(aGrpcId, start), firstThen);
        final var duplicateExpiring = expiring(recordWith(aGrpcId, start), secondThen);
        liveRecords.offer(priorityExpiring);
        liveRecords.offer(duplicateExpiring);

        subject.reviewExistingPayerRecords();

        // then:
        assertFalse(liveTxnHistories.containsKey(leftoverTxnId));
        assertEquals(firstThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
        assertEquals(
                secondThen,
                liveTxnHistories.get(newTxnId).allDuplicateRecords().get(0).getExpiry());
        assertEquals(liveQueryableRecords.get(aKey), new LinkedList<>(List.of(priorityExpiring, duplicateExpiring)));
    }

    @Test
    void expiresRecordsAsExpectedWithoutConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(liveAccounts)));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
        liveAccounts.put(aKey, anAccount);

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addLiveRecord(aKey, firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        subject.trackRecordInState(aGrpcId, firstThen);

        final var secondRecord = expiring(recordWith(aGrpcId, start), secondThen);
        addLiveRecord(aKey, secondRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(secondRecord, OK);
        subject.trackRecordInState(aGrpcId, secondThen);

        subject.purge(now);

        assertEquals(1, liveAccounts.get(aKey).records().size());
        assertEquals(secondThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
    }

    @Test
    void expiresRecordsAsExpectedWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addConsolidatedLiveRecord(firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        subject.trackRecordInState(aGrpcId, firstThen);

        final var secondRecord = expiring(recordWith(aGrpcId, start), secondThen);
        addConsolidatedLiveRecord(secondRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(secondRecord, OK);
        subject.trackRecordInState(aGrpcId, secondThen);

        subject.purge(now);

        assertEquals(1, liveRecords.size());
        assertEquals(secondThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
        assertEquals(liveQueryableRecords.get(aKey), new LinkedList<>(List.of(secondRecord)));
    }

    @Test
    void managesPayerRecordsAsExpectedWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addConsolidatedLiveRecord(firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        subject.trackRecordInState(aGrpcId, firstThen);

        subject.purge(now);

        assertTrue(liveRecords.isEmpty());
        assertFalse(liveQueryableRecords.containsKey(aKey));
    }

    @Test
    void justLogsGivenMissingQueryableRecords() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addConsolidatedLiveRecord(firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        liveQueryableRecords.clear();

        assertDoesNotThrow(() -> subject.purge(now));

        assertThat(
                logCaptor.errorLogs(), contains(startsWith("No queryable records found for payer EntityNum{value=2}")));
    }

    @Test
    void justLogsGivenMismatchedQueryableRecords() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addConsolidatedLiveRecord(firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        liveQueryableRecords.clear();
        liveQueryableRecords
                .computeIfAbsent(aKey, ignore -> new LinkedList<>())
                .add(expiring(recordWith(bGrpcId, start), firstThen));

        assertDoesNotThrow(() -> subject.purge(now));

        assertThat(logCaptor.errorLogs(), contains(startsWith("Inconsistent queryable record")));
    }

    @Test
    void expiresLoneRecordAsExpected() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(liveAccounts)));
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
        liveAccounts.put(aKey, anAccount);

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addLiveRecord(aKey, firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        subject.trackRecordInState(aGrpcId, firstThen);

        subject.purge(now);

        assertEquals(0, liveAccounts.get(aKey).records().size());
        assertFalse(liveTxnHistories.containsKey(newTxnId));
    }

    private void addLiveRecord(EntityNum key, ExpirableTxnRecord expirableTxnRecord) {
        final var mutableAccount = liveAccounts.getForModify(key);
        mutableAccount.records().offer(expirableTxnRecord);
        liveAccounts.replace(aKey, mutableAccount);
    }

    private void addConsolidatedLiveRecord(@NonNull final ExpirableTxnRecord expirableTxnRecord) {
        liveRecords.add(expirableTxnRecord);
        final var payerNum = expirableTxnRecord.getTxnId().getPayerAccount().asNum();
        liveQueryableRecords
                .computeIfAbsent(payerNum, ignore -> new LinkedList<>())
                .add(expirableTxnRecord);
    }

    private ExpirableTxnRecord expiring(final ExpirableTxnRecord expirableTxnRecord, final long at) {
        final var ans = expirableTxnRecord;
        ans.setExpiry(at);
        ans.setSubmittingMember(0L);
        return ans;
    }

    private static ExpirableTxnRecord recordWith(final AccountID payer, final long validStartSecs) {
        return ExpirableTxnRecord.newBuilder()
                .setTxnId(TxnId.fromGrpc(TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(validStartSecs))
                        .build()))
                .setConsensusTime(RichInstant.fromJava(Instant.now()))
                .setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build())
                .build();
    }
}
