/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.records;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.google.common.cache.Cache;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordCacheTest {
    private static final long submittingMember = 1L;

    @Mock private EntityCreator creator;
    @Mock private Cache<TransactionID, Boolean> receiptCache;
    @Mock private Map<TransactionID, TxnIdRecentHistory> histories;
    @Mock private TxnIdRecentHistory recentHistory;
    @Mock private TxnIdRecentHistory recentChildHistory;

    private RecordCache subject;

    @BeforeEach
    void setup() {
        subject = new RecordCache(receiptCache, histories);

        subject.setCreator(creator);
    }

    @Test
    void getsReceiptWithKnownStatusPostConsensus() {
        given(recentHistory.priorityRecord()).willReturn(aRecord);
        given(histories.get(txnIdA)).willReturn(recentHistory);

        assertEquals(knownReceipt, subject.getPriorityReceipt(txnIdA));
    }

    @Test
    void getsDuplicateRecordsAsExpected() {
        final var duplicateRecords = List.of(aRecord);
        given(recentHistory.allDuplicateRecords()).willReturn(duplicateRecords);
        given(histories.get(txnIdA)).willReturn(recentHistory);

        final var actual = subject.getDuplicateRecords(txnIdA);

        assertEquals(List.of(aRecord.asGrpc()), actual);
    }

    @Test
    void getsChildRecordsAsExpected() {
        final var expectedChildren = List.of(aChildRecord.asGrpc());

        given(recentHistory.priorityRecord()).willReturn(aRecord);
        given(histories.get(txnIdA)).willReturn(recentHistory);
        given(histories.get(txnIdA.toBuilder().setNonce(1).build())).willReturn(recentChildHistory);
        given(recentChildHistory.priorityRecord()).willReturn(aChildRecord);

        final var actual = subject.getChildRecords(txnIdA);

        assertEquals(expectedChildren, actual);
    }

    @Test
    void getsChildReceiptsAsExpected() {
        final var expectedChildren = List.of(aChildRecord.asGrpc().getReceipt());

        given(recentHistory.priorityRecord()).willReturn(aRecord);
        given(histories.get(txnIdA)).willReturn(recentHistory);
        given(histories.get(txnIdA.toBuilder().setNonce(1).build())).willReturn(recentChildHistory);
        given(recentChildHistory.priorityRecord()).willReturn(aChildRecord);

        final var actual = subject.getChildReceipts(txnIdA);

        assertEquals(expectedChildren, actual);
    }

    @Test
    void getsNoChildReceiptsIfParentRecordMissingOrUnknownOrHasNoChildren() {
        assertSame(Collections.emptyList(), subject.getChildReceipts(txnIdA));

        given(histories.get(txnIdA)).willReturn(recentHistory);
        aRecord.setNumChildRecords((short) 0);
        given(recentHistory.priorityRecord()).willReturn(aRecord);

        assertSame(Collections.emptyList(), subject.getChildReceipts(txnIdA));
    }

    @Test
    void worksAroundExpiredChildRecordInExtraordinaryEdgeCase() {
        given(recentHistory.priorityRecord()).willReturn(aRecord);
        given(histories.get(txnIdA)).willReturn(recentHistory);
        given(histories.get(txnIdA.toBuilder().setNonce(1).build())).willReturn(null);

        final var actual = subject.getChildReceipts(txnIdA);

        assertEquals(List.of(), actual);
    }

    @Test
    void getsEmptyDuplicateListForMissing() {
        assertTrue(subject.getDuplicateReceipts(txnIdA).isEmpty());
    }

    @Test
    void getsDuplicateReceiptsAsExpected() {
        final var history = mock(TxnIdRecentHistory.class);
        final var duplicateRecords = List.of(aRecord);
        given(history.allDuplicateRecords()).willReturn(duplicateRecords);
        given(histories.get(txnIdA)).willReturn(history);

        final var duplicateReceipts = subject.getDuplicateReceipts(txnIdA);

        assertEquals(List.of(duplicateRecords.get(0).getReceipt().toGrpc()), duplicateReceipts);
    }

    @Test
    void getsNullReceiptWhenMissing() {
        assertNull(subject.getPriorityReceipt(txnIdA));
    }

    @Test
    void getsReceiptWithUnknownStatusPreconsensus() {
        given(histories.get(txnIdA)).willReturn(null);
        given(receiptCache.getIfPresent(txnIdA)).willReturn(Boolean.TRUE);

        assertEquals(unknownReceipt, subject.getPriorityReceipt(txnIdA));
    }

    @Test
    void getsReceiptWithUnknownStatusWhenNoPriorityRecordExists() {
        given(recentHistory.priorityRecord()).willReturn(null);
        given(histories.get(txnIdA)).willReturn(recentHistory);

        assertEquals(unknownReceipt, subject.getPriorityReceipt(txnIdA));
    }

    @Test
    void getsNullRecordWhenMissing() {
        assertNull(subject.getPriorityRecord(txnIdA));
    }

    @Test
    void getsNullRecordWhenPreconsensus() {
        given(histories.get(txnIdA)).willReturn(null);

        assertNull(subject.getPriorityRecord(txnIdA));
    }

    @Test
    void getsNullRecordWhenNoPriorityExists() {
        final var history = mock(TxnIdRecentHistory.class);
        given(history.priorityRecord()).willReturn(null);
        given(histories.get(txnIdA)).willReturn(history);

        assertNull(subject.getPriorityRecord(txnIdA));
    }

    @Test
    void getsRecordWhenPresent() {
        given(recentHistory.priorityRecord()).willReturn(aRecord);
        given(histories.get(txnIdA)).willReturn(recentHistory);

        assertEquals(aRecord, subject.getPriorityRecord(txnIdA));
    }

    @Test
    void addsMarkerForPreconsensusReceipt() {
        subject.addPreConsensus(txnIdB);

        verify(receiptCache).put(txnIdB, Boolean.TRUE);
    }

    @Test
    void delegatesToPutPostConsensus() {
        given(histories.computeIfAbsent(argThat(txnIdA::equals), any())).willReturn(recentHistory);

        subject.setPostConsensus(
                txnIdA, ResponseCodeEnum.valueOf(aRecord.getReceipt().getStatus()), aRecord);

        verify(recentHistory)
                .observe(aRecord, ResponseCodeEnum.valueOf(aRecord.getReceipt().getStatus()));
    }

    @Test
    void managesFailInvalidRecordsAsExpected() throws InvalidProtocolBufferException {
        final var consensusTime = Instant.now();
        final var txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build();
        final var signedTxn =
                Transaction.newBuilder()
                        .setBodyBytes(
                                TransactionBody.newBuilder()
                                        .setTransactionID(txnId)
                                        .setMemo("Catastrophe!")
                                        .build()
                                        .toByteString())
                        .build();
        final var platformTxn = new SwirldTransaction(signedTxn.toByteArray());
        final var effectivePayer = IdUtils.asAccount("0.0.3");
        given(histories.computeIfAbsent(argThat(txnId::equals), any())).willReturn(recentHistory);
        final var accessor =
                PlatformTxnAccessor.from(
                        SignedTxnAccessor.from(platformTxn.getContents()), platformTxn);

        final var expirableTxnRecordBuilder =
                ExpirableTxnRecord.newBuilder()
                        .setTxnId(TxnId.fromGrpc(txnId))
                        .setReceipt(TxnReceipt.newBuilder().setStatus(FAIL_INVALID.name()).build())
                        .setMemo(accessor.getTxn().getMemo())
                        .setTxnHash(accessor.getHash())
                        .setConsensusTime(RichInstant.fromJava(consensusTime));
        final var expectedRecord = expirableTxnRecordBuilder.build();
        expectedRecord.setExpiry(consensusTime.getEpochSecond() + 180);
        expectedRecord.setSubmittingMember(submittingMember);

        given(creator.createInvalidFailureRecord(any(), any()))
                .willReturn(expirableTxnRecordBuilder);
        given(creator.saveExpiringRecord(any(), any(), anyLong(), anyLong()))
                .willReturn(expectedRecord);

        subject.setFailInvalid(effectivePayer, accessor, consensusTime, submittingMember);

        verify(recentHistory)
                .observe(argThat(expectedRecord::equals), argThat(FAIL_INVALID::equals));
    }

    @Test
    void managesTriggeredFailInvalidRecordAsExpected() throws InvalidProtocolBufferException {
        final var consensusTime = Instant.now();
        final var txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build();
        final var signedTxn =
                Transaction.newBuilder()
                        .setBodyBytes(
                                TransactionBody.newBuilder()
                                        .setTransactionID(txnId)
                                        .setMemo("Catastrophe!")
                                        .build()
                                        .toByteString())
                        .build();
        final var effectivePayer = IdUtils.asAccount("0.0.3");
        final var effectiveScheduleID = IdUtils.asSchedule("0.0.123");
        given(histories.computeIfAbsent(argThat(txnId::equals), any())).willReturn(recentHistory);
        final var accessor = SignedTxnAccessor.from(signedTxn.toByteArray());
        final var expirableTxnRecordBuilder =
                ExpirableTxnRecord.newBuilder()
                        .setTxnId(TxnId.fromGrpc(txnId))
                        .setReceipt(TxnReceipt.newBuilder().setStatus(FAIL_INVALID.name()).build())
                        .setMemo(accessor.getTxn().getMemo())
                        .setTxnHash(accessor.getHash())
                        .setConsensusTime(RichInstant.fromJava(consensusTime))
                        .setScheduleRef(fromGrpcScheduleId(effectiveScheduleID));
        final var expirableTxnRecord = expirableTxnRecordBuilder.build();
        given(creator.createInvalidFailureRecord(any(), any()))
                .willReturn(expirableTxnRecordBuilder);
        given(creator.saveExpiringRecord(any(), any(), anyLong(), anyLong()))
                .willReturn(expirableTxnRecord);

        // when:
        subject.setFailInvalid(effectivePayer, accessor, consensusTime, submittingMember);

        // then:
        verify(recentHistory).observe(expirableTxnRecord, FAIL_INVALID);
    }

    @Test
    void usesHistoryThenCacheToTestReceiptPresence() {
        given(histories.containsKey(txnIdA)).willReturn(true);
        given(histories.containsKey(txnIdB)).willReturn(false);
        given(receiptCache.getIfPresent(txnIdB)).willReturn(RecordCache.MARKER);
        given(histories.containsKey(txnIdC)).willReturn(false);
        given(receiptCache.getIfPresent(txnIdC)).willReturn(null);

        final var hasA = subject.isReceiptPresent(txnIdA);
        final var hasB = subject.isReceiptPresent(txnIdB);
        final var hasC = subject.isReceiptPresent(txnIdC);

        assertTrue(hasA);
        assertTrue(hasB);
        assertFalse(hasC);
    }

    private static final TransactionID txnIdA =
            TransactionID.newBuilder()
                    .setTransactionValidStart(
                            Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
                    .setAccountID(asAccount("0.0.2"))
                    .build();
    private static final TransactionID txnIdB =
            TransactionID.newBuilder()
                    .setAccountID(asAccount("2.2.0"))
                    .setTransactionValidStart(
                            Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
                    .build();
    private static final TransactionID txnIdC =
            TransactionID.newBuilder()
                    .setAccountID(asAccount("2.2.3"))
                    .setTransactionValidStart(
                            Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
                    .build();
    private static final TxnReceipt unknownReceipt =
            TxnReceipt.newBuilder().setStatus(UNKNOWN.name()).build();
    private static final ExchangeRate rate =
            ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(555L).build())
                    .build();
    private static final TxnReceipt knownReceipt =
            TxnReceipt.newBuilder()
                    .setStatus(SUCCESS.name())
                    .setAccountId(EntityId.fromGrpcAccountId(asAccount("0.0.2")))
                    .setExchangeRates(
                            ExchangeRates.fromGrpc(
                                    ExchangeRateSet.newBuilder()
                                            .setCurrentRate(rate)
                                            .setNextRate(rate)
                                            .build()))
                    .build();
    private static final TxnReceipt knownChildReceipt =
            TxnReceipt.newBuilder().setStatus(INVALID_SIGNATURE.name()).build();
    private final ExpirableTxnRecord aRecord =
            ExpirableTxnRecord.newBuilder()
                    .setMemo("Something")
                    .setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(500L)))
                    .setReceipt(knownReceipt)
                    .setTxnId(TxnId.fromGrpc(txnIdA))
                    .setFee(123L)
                    .setNumChildRecords((short) 1)
                    .build();
    private final ExpirableTxnRecord aChildRecord =
            ExpirableTxnRecord.newBuilder()
                    .setMemo("Something else")
                    .setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(501L)))
                    .setReceipt(knownChildReceipt)
                    .setTxnId(TxnId.fromGrpc(txnIdA).withNonce(1))
                    .setParentConsensusTime(Instant.ofEpochSecond(500L))
                    .build();
}
