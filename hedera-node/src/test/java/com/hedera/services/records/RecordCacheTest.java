package com.hedera.services.records;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.cache.Cache;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.MonotonicFullQueueExpiries;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.TriggeredTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hedera.services.utils.PlatformTxnAccessor.uncheckedAccessorFor;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordCacheTest {
	long someExpiry = 1_234_567L;
	long submittingMember = 1L;

	@Mock
	private EntityCreator creator;
	@Mock
	private Cache<TransactionID, Boolean> receiptCache;
	@Mock
	private Map<TransactionID, TxnIdRecentHistory> histories;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private TxnIdRecentHistory recentHistory;
	@Mock
	MonotonicFullQueueExpiries<TransactionID> recordExpiries;

	private RecordCache subject;

	@BeforeEach
	private void setup() {
		subject = new RecordCache(receiptCache, histories);

		subject.setCreator(creator);
	}

	@Test
	void getsReceiptWithKnownStatusPostConsensus() {
		// setup:
		given(recentHistory.priorityRecord()).willReturn(aRecord);
		given(histories.get(txnIdA)).willReturn(recentHistory);

		// expect:
		assertEquals(knownReceipt, subject.getPriorityReceipt(txnIdA));
	}

	@Test
	void getsDuplicateRecordsAsExpected() {
		// setup:
		var duplicateRecords = List.of(aRecord);

		given(recentHistory.duplicateRecords()).willReturn(duplicateRecords);
		given(histories.get(txnIdA)).willReturn(recentHistory);

		// when:
		var actual = subject.getDuplicateRecords(txnIdA);

		// expect:
		assertEquals(List.of(aRecord.asGrpc()), actual);
	}

	@Test
	void getsEmptyDuplicateListForMissing() {
		// expect:
		assertTrue(subject.getDuplicateReceipts(txnIdA).isEmpty());
	}

	@Test
	void getsDuplicateReceiptsAsExpected() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);
		var duplicateRecords = List.of(aRecord);

		given(history.duplicateRecords()).willReturn(duplicateRecords);
		given(histories.get(txnIdA)).willReturn(history);

		// when:
		var duplicateReceipts = subject.getDuplicateReceipts(txnIdA);

		// expect:
		assertEquals(List.of(duplicateRecords.get(0).getReceipt().toGrpc()), duplicateReceipts);
	}

	@Test
	void getsNullReceiptWhenMissing() {
		// expect:
		assertNull(subject.getPriorityReceipt(txnIdA));
	}

	@Test
	void getsReceiptWithUnknownStatusPreconsensus() {
		given(histories.get(txnIdA)).willReturn(null);
		given(receiptCache.getIfPresent(txnIdA)).willReturn(Boolean.TRUE);

		// expect:
		assertEquals(unknownReceipt, subject.getPriorityReceipt(txnIdA));
	}

	@Test
	void getsReceiptWithUnknownStatusWhenNoPriorityRecordExists() {
		given(recentHistory.priorityRecord()).willReturn(null);
		given(histories.get(txnIdA)).willReturn(recentHistory);

		// expect:
		assertEquals(unknownReceipt, subject.getPriorityReceipt(txnIdA));
	}

	@Test
	void getsNullRecordWhenMissing() {
		// expect:
		assertNull(subject.getPriorityRecord(txnIdA));
	}

	@Test
	void getsNullRecordWhenPreconsensus() {
		given(histories.get(txnIdA)).willReturn(null);

		// expect:
		assertNull(subject.getPriorityRecord(txnIdA));
	}

	@Test
	void getsNullRecordWhenNoPriorityExists() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.priorityRecord()).willReturn(null);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertNull(subject.getPriorityRecord(txnIdA));
	}

	@Test
	void getsRecordWhenPresent() {
		given(recentHistory.priorityRecord()).willReturn(aRecord);
		given(histories.get(txnIdA)).willReturn(recentHistory);

		// expect:
		assertEquals(aRecord, subject.getPriorityRecord(txnIdA));
	}

	@Test
	void addsMarkerForPreconsensusReceipt() {
		// when:
		subject.addPreConsensus(txnIdB);

		// then:
		verify(receiptCache).put(txnIdB, Boolean.TRUE);
	}

	@Test
	void delegatesToPutPostConsensus() {
		given(histories.computeIfAbsent(argThat(txnIdA::equals), any())).willReturn(recentHistory);

		// when:
		subject.setPostConsensus(
				txnIdA,
				ResponseCodeEnum.valueOf(aRecord.getReceipt().getStatus()),
				aRecord);
		// then:
		verify(recentHistory).observe(aRecord, ResponseCodeEnum.valueOf(aRecord.getReceipt().getStatus()));
	}

	@Test
	void managesFailInvalidRecordsAsExpected() {
		// setup:
		Instant consensusTime = Instant.now();
		TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build();
		Transaction signedTxn = Transaction.newBuilder()
				.setBodyBytes(TransactionBody.newBuilder()
						.setTransactionID(txnId)
						.setMemo("Catastrophe!")
						.build().toByteString())
				.build();
		// and:
		SwirldTransaction platformTxn = new SwirldTransaction(signedTxn.toByteArray());
		// and:
		AccountID effectivePayer = IdUtils.asAccount("0.0.3");

		given(histories.computeIfAbsent(argThat(txnId::equals), any())).willReturn(recentHistory);

		// given:
		PlatformTxnAccessor accessor = uncheckedAccessorFor(platformTxn);
		// and:

		var expirableTxnRecordBuilder = ExpirableTxnRecord.newBuilder()
				.setTxnId(TxnId.fromGrpc(txnId))
				.setReceipt(TxnReceipt.newBuilder().setStatus(FAIL_INVALID.name()).build())
				.setMemo(accessor.getTxn().getMemo())
				.setTxnHash(accessor.getHash())
				.setConsensusTime(RichInstant.fromJava(consensusTime));
		var expectedRecord = expirableTxnRecordBuilder.build();
		expectedRecord.setExpiry(consensusTime.getEpochSecond() + 180);
		expectedRecord.setSubmittingMember(submittingMember);

		given(creator.buildFailedExpiringRecord(any(), any())).willReturn(expirableTxnRecordBuilder);
		given(creator.saveExpiringRecord(any(), any(), anyLong(), anyLong())).willReturn(
				expectedRecord);

		// when:
		subject.setFailInvalid(
				effectivePayer,
				accessor,
				consensusTime,
				submittingMember);

		// then:
		verify(recentHistory).observe(
				argThat(expectedRecord::equals),
				argThat(FAIL_INVALID::equals));
	}

	@Test
	void managesTriggeredFailInvalidRecordAsExpected() throws InvalidProtocolBufferException {
		// setup:
		Instant consensusTime = Instant.now();
		TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build();
		Transaction signedTxn = Transaction.newBuilder()
				.setBodyBytes(TransactionBody.newBuilder()
						.setTransactionID(txnId)
						.setMemo("Catastrophe!")
						.build().toByteString())
				.build();
		// and:
		AccountID effectivePayer = IdUtils.asAccount("0.0.3");
		ScheduleID effectiveScheduleID = IdUtils.asSchedule("0.0.123");

		given(histories.computeIfAbsent(argThat(txnId::equals), any())).willReturn(recentHistory);

		// given:
		TxnAccessor accessor = new TriggeredTxnAccessor(signedTxn.toByteArray(), effectivePayer, effectiveScheduleID);
		// and:
		var expirableTxnRecordBuilder = ExpirableTxnRecord.newBuilder()
				.setTxnId(TxnId.fromGrpc(txnId))
				.setReceipt(TxnReceipt.newBuilder().setStatus(FAIL_INVALID.name()).build())
				.setMemo(accessor.getTxn().getMemo())
				.setTxnHash(accessor.getHash())
				.setConsensusTime(RichInstant.fromJava(consensusTime))
				.setScheduleRef(fromGrpcScheduleId(effectiveScheduleID));
		var expirableTxnRecord = expirableTxnRecordBuilder.build();
		given(creator.buildFailedExpiringRecord(any(), any())).willReturn(expirableTxnRecordBuilder);
		given(creator.saveExpiringRecord(any(), any(), anyLong(), anyLong())).willReturn(expirableTxnRecord);

		// when:
		subject.setFailInvalid(
				effectivePayer,
				accessor,
				consensusTime,
				submittingMember);

		// then:
		verify(recentHistory).observe(expirableTxnRecord, FAIL_INVALID);
	}


	@Test
	void usesHistoryThenCacheToTestReceiptPresence() {
		given(histories.containsKey(txnIdA)).willReturn(true);
		// and:
		given(histories.containsKey(txnIdB)).willReturn(false);
		given(receiptCache.getIfPresent(txnIdB)).willReturn(RecordCache.MARKER);
		// and:
		given(histories.containsKey(txnIdC)).willReturn(false);
		given(receiptCache.getIfPresent(txnIdC)).willReturn(null);

		// when:
		boolean hasA = subject.isReceiptPresent(txnIdA);
		boolean hasB = subject.isReceiptPresent(txnIdB);
		boolean hasC = subject.isReceiptPresent(txnIdC);

		// then:
		assertTrue(hasA);
		assertTrue(hasB);
		assertFalse(hasC);
	}

	private final TransactionID txnIdA = TransactionID.newBuilder()
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
			.setAccountID(asAccount("0.0.2"))
			.build();
	private final TransactionID txnIdB = TransactionID.newBuilder()
			.setAccountID(asAccount("2.2.0"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
			.build();
	private final TransactionID txnIdC = TransactionID.newBuilder()
			.setAccountID(asAccount("2.2.3"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
			.build();
	private final TxnReceipt unknownReceipt = TxnReceipt.newBuilder()
			.setStatus(UNKNOWN.name())
			.build();
	private final ExchangeRate rate = ExchangeRate.newBuilder()
			.setCentEquiv(1)
			.setHbarEquiv(12)
			.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(555L).build())
			.build();
	private final TxnReceipt knownReceipt = TxnReceipt.newBuilder()
			.setStatus(SUCCESS.name())
			.setAccountId(EntityId.fromGrpcAccountId(asAccount("0.0.2")))
			.setExchangeRates(
					ExchangeRates.fromGrpc(ExchangeRateSet.newBuilder().setCurrentRate(rate).setNextRate(rate).build()))
			.build();
	private final ExpirableTxnRecord aRecord = ExpirableTxnRecord.newBuilder()
			.setMemo("Something")
			.setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(500L)))
			.setReceipt(knownReceipt)
			.setTxnId(TxnId.fromGrpc(txnIdA))
			.setFee(123L)
			.build();
}
