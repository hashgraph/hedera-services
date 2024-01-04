/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.NodeInfo;
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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ExpiryManagerTest {
    private final long now = 1_234_567L;
    private final long start = now - 180L;
    private final long firstThen = now - 1;
    private final long secondThen = now + 1;
    private static final long nodeId = 4L;
    private final AccountID nodeAccountId = IdUtils.asAccount("0.0.7");
    private final AccountID aGrpcId = IdUtils.asAccount("0.0.1234");
    private final AccountID bGrpcId = IdUtils.asAccount("0.0.4567");
    private final EntityNum aKey = EntityNum.fromAccountId(aGrpcId);
    private final EntityNum nodeKey = EntityNum.fromAccountId(nodeAccountId);
    private final MerkleAccount anAccount = new MerkleAccount();

    private final MerkleMap<EntityNum, MerkleAccount> liveAccounts = new MerkleMap<>();
    private final Map<TransactionID, TxnIdRecentHistory> liveTxnHistories = new HashMap<>();

    private final FCQueue<ExpirableTxnRecord> liveRecords = new FCQueue<>();
    private final Map<EntityNum, Queue<ExpirableTxnRecord>> liveQueryableRecords = new HashMap<>();

    @Mock
    private NodeInfo nodeInfo;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private ExpiryManager subject;

    @Test
    void rebuildsExpectedRecordsFromStateWithoutConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(liveAccounts)), nodeInfo);
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
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
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
    void rebuildsExpectedDueDiligenceFailureQueryableRecordsFromStateWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
        final var priorityRecord = recordWith(aGrpcId, start);
        final var diligenceFailureRecord = recordWith(aGrpcId, start, INVALID_PAYER_SIGNATURE);
        final var priorityExpiring = expiring(priorityRecord, firstThen);
        final var diligenceFailureExpiring = expiring(diligenceFailureRecord, secondThen);
        liveRecords.offer(priorityExpiring);
        liveRecords.offer(diligenceFailureExpiring);
        given(nodeInfo.accountKeyOf(nodeId)).willReturn(nodeKey);

        subject.reviewExistingPayerRecords();

        assertEquals(new LinkedList<>(List.of(priorityExpiring)), liveQueryableRecords.get(aKey));
        assertEquals(new LinkedList<>(List.of(diligenceFailureExpiring)), liveQueryableRecords.get(nodeKey));
    }

    @Test
    void doesntThrowIfSubmittingMemberSomehowMissingWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
        final var priorityRecord = recordWith(aGrpcId, start);
        final var diligenceFailureRecord = recordWith(aGrpcId, start, INVALID_PAYER_SIGNATURE);
        final var priorityExpiring = expiring(priorityRecord, firstThen);
        final var diligenceFailureExpiring = expiring(diligenceFailureRecord, secondThen);
        liveRecords.offer(priorityExpiring);
        liveRecords.offer(diligenceFailureExpiring);
        given(nodeInfo.accountKeyOf(nodeId)).willThrow(IllegalArgumentException.class);

        assertDoesNotThrow(subject::reviewExistingPayerRecords);
        assertEquals(new LinkedList<>(List.of(priorityExpiring)), liveQueryableRecords.get(aKey));
        assertFalse(liveQueryableRecords.containsKey(nodeKey));
    }

    @Test
    void expiresRecordsAsExpectedWithoutConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories, () -> RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(liveAccounts)), nodeInfo);
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
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
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
    void expiresDiligenceFailureRecordsAsExpectedWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
        final var diligenceFailureRecord = recordWith(aGrpcId, start, INVALID_PAYER_SIGNATURE);
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
        addConsolidatedLiveRecord(expiring(diligenceFailureRecord, firstThen), nodeKey);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(diligenceFailureRecord, INVALID_PAYER_SIGNATURE);
        subject.trackRecordInState(aGrpcId, firstThen);
        given(nodeInfo.accountKeyOf(nodeId)).willReturn(nodeKey);

        subject.purge(now);

        assertTrue(liveRecords.isEmpty());
        assertNull(liveQueryableRecords.get(nodeKey));
    }

    @Test
    void justLogsErrorWhenDiligenceFailureHasMissingMemberIdWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
        final var diligenceFailureRecord = recordWith(aGrpcId, start, INVALID_PAYER_SIGNATURE);
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
        addConsolidatedLiveRecord(expiring(diligenceFailureRecord, firstThen), nodeKey);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(diligenceFailureRecord, INVALID_PAYER_SIGNATURE);
        subject.trackRecordInState(aGrpcId, firstThen);
        given(nodeInfo.accountKeyOf(nodeId)).willThrow(IllegalArgumentException.class);

        assertDoesNotThrow(() -> subject.purge(now));

        assertTrue(liveRecords.isEmpty());
        assertThat(logCaptor.warnLogs(), contains(startsWith("Address book does not have member id 4 ")));
    }

    @Test
    void managesPayerRecordsAsExpectedWithConsolidatedFcq() {
        subject = new ExpiryManager(
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
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
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
        final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();

        final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
        addConsolidatedLiveRecord(firstRecord);
        liveTxnHistories
                .computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory())
                .observe(firstRecord, OK);
        liveQueryableRecords.clear();

        assertDoesNotThrow(() -> subject.purge(now));

        assertThat(
                logCaptor.errorLogs(),
                contains(startsWith("No queryable records found for payer EntityNum{value=1234}")));
    }

    @Test
    void justLogsGivenMismatchedQueryableRecords() {
        subject = new ExpiryManager(
                liveTxnHistories,
                () -> RecordsStorageAdapter.fromConsolidated(liveRecords, liveQueryableRecords),
                nodeInfo);
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
                liveTxnHistories, () -> RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(liveAccounts)), nodeInfo);
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
        addConsolidatedLiveRecord(
                expirableTxnRecord,
                expirableTxnRecord.getTxnId().getPayerAccount().asNum());
    }

    private void addConsolidatedLiveRecord(
            @NonNull final ExpirableTxnRecord expirableTxnRecord, @NonNull final EntityNum key) {
        liveRecords.add(expirableTxnRecord);
        liveQueryableRecords.computeIfAbsent(key, ignore -> new LinkedList<>()).add(expirableTxnRecord);
    }

    private ExpirableTxnRecord expiring(final ExpirableTxnRecord expirableTxnRecord, final long at) {
        expirableTxnRecord.setExpiry(at);
        return expirableTxnRecord;
    }

    private static ExpirableTxnRecord recordWith(final AccountID payer, final long validStartSecs) {
        return recordWith(payer, validStartSecs, SUCCESS);
    }

    private static ExpirableTxnRecord recordWith(
            final AccountID payer, final long validStartSecs, final ResponseCodeEnum status) {
        final var synthRecord = ExpirableTxnRecord.newBuilder()
                .setTxnId(TxnId.fromGrpc(TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(validStartSecs))
                        .build()))
                .setConsensusTime(RichInstant.fromJava(Instant.now()))
                .setReceipt(TxnReceipt.newBuilder().setStatus(status.name()).build())
                .build();
        synthRecord.setSubmittingMember(nodeId);
        return synthRecord;
    }
}
