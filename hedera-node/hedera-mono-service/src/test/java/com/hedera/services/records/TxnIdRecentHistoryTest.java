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

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TxnIdRecentHistoryTest {
    private static final Instant now = Instant.now();

    private TxnIdRecentHistory subject;

    @BeforeEach
    void setup() {
        subject = new TxnIdRecentHistory();
    }

    @Test
    void safeToExpireFromEmptyHistory() {
        subject.observe(recordOf(0, 0, INVALID_NODE_ACCOUNT), INVALID_NODE_ACCOUNT);
        assertEquals(BELIEVED_UNIQUE, subject.currentDuplicityFor(1));
        subject.observe(recordOf(1, 0, SUCCESS), SUCCESS);

        subject.forgetExpiredAt(expiryAtOffset(0));
        assertDoesNotThrow(() -> subject.forgetExpiredAt(expiryAtOffset(1)));
    }

    @Test
    void getsMemory() {
        subject.classifiableRecords = List.of(mock(ExpirableTxnRecord.class));
        assertFalse(subject.isForgotten());

        subject.classifiableRecords = null;
        subject.unclassifiableRecords = List.of(mock(ExpirableTxnRecord.class));
        assertFalse(subject.isForgotten());

        subject.unclassifiableRecords = null;
        assertTrue(subject.isForgotten());
    }

    @Test
    void classifiesAsExpected() {
        subject.observe(recordOf(0, 0, INVALID_NODE_ACCOUNT), INVALID_NODE_ACCOUNT);
        assertEquals(BELIEVED_UNIQUE, subject.currentDuplicityFor(1));

        subject.observe(recordOf(1, 1, SUCCESS), SUCCESS);
        assertEquals(NODE_DUPLICATE, subject.currentDuplicityFor(1));
        assertEquals(DUPLICATE, subject.currentDuplicityFor(2));
    }

    @Test
    void restoresFromStagedAsExpected() {
        subject.stage(recordOf(2, 7, INVALID_NODE_ACCOUNT));
        subject.stage(recordOf(1, 1, SUCCESS));
        subject.stage(recordOf(1, 0, INVALID_PAYER_SIGNATURE));
        subject.stage(recordOf(2, 3, DUPLICATE_TRANSACTION));
        subject.stage(recordOf(1, 2, DUPLICATE_TRANSACTION));
        subject.stage(recordOf(3, 5, DUPLICATE_TRANSACTION));
        subject.stage(recordOf(1, 6, INVALID_PAYER_SIGNATURE));
        subject.stage(recordOf(2, 4, DUPLICATE_TRANSACTION));

        subject.observeStaged();

        assertEquals(
                List.of(
                        memoIdentifying(1, 1, SUCCESS),
                        memoIdentifying(2, 3, DUPLICATE_TRANSACTION),
                        memoIdentifying(3, 5, DUPLICATE_TRANSACTION),
                        memoIdentifying(1, 2, DUPLICATE_TRANSACTION),
                        memoIdentifying(2, 4, DUPLICATE_TRANSACTION)),
                subject.classifiableRecords.stream().map(sr -> sr.getMemo()).collect(toList()));
        assertEquals(
                List.of(
                        memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE),
                        memoIdentifying(1, 6, INVALID_PAYER_SIGNATURE),
                        memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)),
                subject.unclassifiableRecords.stream().map(sr -> sr.getMemo()).collect(toList()));
        assertNull(subject.memory);
    }

    @Test
    void prioritizesClassifiableRecords() {
        givenSomeWellKnownHistoryWithListOfSizeGreaterThanOne();

        final var priority = subject.priorityRecord();

        assertEquals(memoIdentifying(1, 1, SUCCESS), priority.getMemo());
    }

    @Test
    void returnsEmptyIfForgotten() {
        assertNull(subject.priorityRecord());
    }

    @Test
    void recognizesEmptyDuplicates() {
        assertTrue(subject.allDuplicateRecords().isEmpty());
    }

    @Test
    void returnsUnclassifiableIfOnlyAvailable() {
        subject.observe(recordOf(1, 0, INVALID_PAYER_SIGNATURE), INVALID_PAYER_SIGNATURE);

        final var priority = subject.priorityRecord();

        assertEquals(memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE), priority.getMemo());
    }

    @Test
    void nothingHappensWithNoHistory() {
        assertDoesNotThrow(() -> subject.forgetExpiredAt(expiryAtOffset(1)));
    }

    @Test
    void forgetsFromListOfSizeGreaterThanOneAsExpected() {
        givenSomeWellKnownHistoryWithListOfSizeGreaterThanOne();

        subject.forgetExpiredAt(expiryAtOffset(4));

        assertEquals(
                List.of(memoIdentifying(3, 5, DUPLICATE_TRANSACTION)),
                subject.classifiableRecords.stream()
                        .map(ExpirableTxnRecord::getMemo)
                        .collect(toList()));
        assertEquals(
                List.of(
                        memoIdentifying(1, 6, INVALID_PAYER_SIGNATURE),
                        memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)),
                subject.unclassifiableRecords.stream()
                        .map(ExpirableTxnRecord::getMemo)
                        .collect(toList()));
    }

    @Test
    void canStillClassifyDuplicatesAfterSomeHistoryIsExpired() {
        givenSomeWellKnownHistoryWithListOfSizeGreaterThanOne();

        subject.forgetExpiredAt(expiryAtOffset(4));

        assertEquals(DUPLICATE, subject.currentDuplicityFor(2));
        assertEquals(NODE_DUPLICATE, subject.currentDuplicityFor(3));
    }

    @Test
    void forgetsAsExpectedFromListOfSizeOne() {
        givenSomeWellKnownHistoryWithListOfSizeOne();

        subject.forgetExpiredAt(expiryAtOffset(4));

        assertEquals(
                List.of(memoIdentifying(3, 5, DUPLICATE_TRANSACTION)),
                subject.classifiableRecords.stream()
                        .map(ExpirableTxnRecord::getMemo)
                        .collect(toList()));
        assertEquals(0, subject.unclassifiableRecords.size());
    }

    @Test
    void forgetsNothingFromListOfSizeOneWhenNotExpired() {
        givenJustOneUnclassifiableRecordHistory();

        subject.forgetExpiredAt(expiryAtOffset(-1));

        assertEquals(1, subject.unclassifiableRecords.size());
    }

    @Test
    void omitsPriorityWhenUnclassifiable() {
        subject.observe(recordOf(1, 0, INVALID_PAYER_SIGNATURE), INVALID_PAYER_SIGNATURE);
        subject.observe(recordOf(2, 1, INVALID_NODE_ACCOUNT), INVALID_NODE_ACCOUNT);

        final var duplicates = subject.allDuplicateRecords();

        assertEquals(
                List.of(memoIdentifying(2, 1, INVALID_NODE_ACCOUNT)),
                duplicates.stream().map(ExpirableTxnRecord::getMemo).collect(toList()));
    }

    @Test
    void returnsOrderedDuplicates() {
        givenSomeWellKnownHistoryWithListOfSizeGreaterThanOne();

        final var records = subject.allDuplicateRecords();

        assertEquals(
                List.of(
                        memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE),
                        memoIdentifying(1, 2, DUPLICATE_TRANSACTION),
                        memoIdentifying(2, 3, DUPLICATE_TRANSACTION),
                        memoIdentifying(2, 4, DUPLICATE_TRANSACTION),
                        memoIdentifying(3, 5, DUPLICATE_TRANSACTION),
                        memoIdentifying(1, 6, INVALID_PAYER_SIGNATURE),
                        memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)),
                records.stream().map(ExpirableTxnRecord::getMemo).collect(toList()));
    }

    @Test
    void forgetsFromExpiredClassifiableListOfSizeOne() {
        subject.observe(recordOf(1, 1, SUCCESS), SUCCESS);

        subject.forgetExpiredAt(expiryAtOffset(1));

        assertTrue(subject.isForgotten());

        subject.observe(recordOf(1, 1, SUCCESS), SUCCESS);
        subject.forgetExpiredAt(expiryAtOffset(0));
        assertEquals(NODE_DUPLICATE, subject.currentDuplicityFor(1));
    }

    private void givenSomeWellKnownHistoryWithListOfSizeGreaterThanOne() {
        subject.observe(recordOf(1, 0, INVALID_PAYER_SIGNATURE), INVALID_PAYER_SIGNATURE);
        subject.observe(recordOf(1, 1, SUCCESS), SUCCESS);
        subject.observe(recordOf(1, 2, DUPLICATE_TRANSACTION), DUPLICATE_TRANSACTION);
        subject.observe(recordOf(2, 3, DUPLICATE_TRANSACTION), DUPLICATE_TRANSACTION);
        subject.observe(recordOf(2, 4, DUPLICATE_TRANSACTION), DUPLICATE_TRANSACTION);
        subject.observe(recordOf(3, 5, DUPLICATE_TRANSACTION), DUPLICATE_TRANSACTION);
        subject.observe(recordOf(1, 6, INVALID_PAYER_SIGNATURE), INVALID_PAYER_SIGNATURE);
        subject.observe(recordOf(2, 7, INVALID_NODE_ACCOUNT), INVALID_NODE_ACCOUNT);
    }

    private void givenSomeWellKnownHistoryWithListOfSizeOne() {
        subject.observe(recordOf(1, 0, INVALID_PAYER_SIGNATURE), INVALID_PAYER_SIGNATURE);
        subject.observe(recordOf(1, 1, SUCCESS), SUCCESS);
        subject.observe(recordOf(1, 2, DUPLICATE_TRANSACTION), DUPLICATE_TRANSACTION);
        subject.observe(recordOf(3, 5, DUPLICATE_TRANSACTION), DUPLICATE_TRANSACTION);
    }

    private void givenJustOneUnclassifiableRecordHistory() {
        subject.observe(recordOf(1, 0, INVALID_PAYER_SIGNATURE), INVALID_PAYER_SIGNATURE);
    }

    private static final ExpirableTxnRecord recordOf(
            final long submittingMember,
            final long consensusOffsetSecs,
            final ResponseCodeEnum status) {
        final var payerRecord =
                TransactionRecord.newBuilder()
                        .setConsensusTimestamp(
                                Timestamp.newBuilder().setSeconds(consensusOffsetSecs))
                        .setMemo(memoIdentifying(submittingMember, consensusOffsetSecs, status))
                        .setReceipt(TransactionReceipt.newBuilder().setStatus(status))
                        .build();
        final var expirableRecord = fromGprc(payerRecord);
        expirableRecord.setExpiry(expiryAtOffset(consensusOffsetSecs));
        expirableRecord.setSubmittingMember(submittingMember);
        return expirableRecord;
    }

    private static final long expiryAtOffset(final long l) {
        return now.getEpochSecond() + 1 + l;
    }

    private static final String memoIdentifying(
            final long submittingMember,
            final long consensusOffsetSecs,
            final ResponseCodeEnum status) {
        return String.format(
                "%d submitted @ %d past -> %s", submittingMember, consensusOffsetSecs, status);
    }
}
