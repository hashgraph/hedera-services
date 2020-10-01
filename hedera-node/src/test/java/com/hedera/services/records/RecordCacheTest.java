package com.hedera.services.records;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.MonotonicFullQueueExpiries;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.MiscUtils.sha384HashOf;
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

@RunWith(JUnitPlatform.class)
class RecordCacheTest {
	long someExpiry = 1_234_567L;
	long submittingMember = 1L;
	private TransactionID txnIdA = TransactionID.newBuilder()
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
			.setAccountID(asAccount("0.0.2"))
			.build();
	private TransactionID txnIdB = TransactionID.newBuilder()
			.setAccountID(asAccount("2.2.0"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
			.build();
	private TransactionID txnIdC = TransactionID.newBuilder()
			.setAccountID(asAccount("2.2.3"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(12_345L).setNanos(54321))
			.build();
	private TransactionReceipt unknownReceipt = TransactionReceipt.newBuilder()
			.setStatus(UNKNOWN)
			.build();
	private ExchangeRate rate = ExchangeRate.newBuilder()
			.setCentEquiv(1)
			.setHbarEquiv(12)
			.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(555L).build())
			.build();
	private TransactionReceipt knownReceipt = TransactionReceipt.newBuilder()
			.setStatus(SUCCESS)
			.setAccountID(asAccount("0.0.2"))
			.setExchangeRate(ExchangeRateSet.newBuilder().setCurrentRate(rate).setNextRate(rate))
			.build();
	private TransactionRecord aRecord = TransactionRecord.newBuilder()
			.setMemo("Something")
			.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(500L))
			.setReceipt(knownReceipt)
			.setTransactionID(txnIdA)
			.setTransactionFee(123L)
			.build();

	private ExpirableTxnRecord record = ExpirableTxnRecord.fromGprc(aRecord);

	private EntityCreator creator;
	private Cache<TransactionID, Boolean> receiptCache;
	private Map<TransactionID, TxnIdRecentHistory> histories;

	private RecordCache subject;

	@BeforeEach
	private void setup() {
		creator = mock(EntityCreator.class);
		histories = (Map<TransactionID, TxnIdRecentHistory>)mock(Map.class);
		receiptCache = (Cache<TransactionID, Boolean>)mock(Cache.class);
		subject = new RecordCache(creator, receiptCache, histories);
	}

	@Test
	public void doesDiForCreator() {
		// expect:
		creator.setRecordCache(subject);
	}

	@Test
	public void expiresOtherForgottenHistory() {
		// setup:
		subject = new RecordCache(creator, receiptCache, new HashMap<>());

		// given:
		record.setExpiry(someExpiry);
		subject.setPostConsensus(txnIdA, SUCCESS, record);
		subject.trackForExpiry(record);

		// when:
		subject.forgetAnyOtherExpiredHistory(someExpiry + 1);

		// then:
		assertFalse(subject.isReceiptPresent(txnIdA));
	}

	@Test
	public void tracksExpiringTxnIds() {
		// setup:
		subject.recordExpiries = mock(MonotonicFullQueueExpiries.class);
		// and:
		record.setExpiry(someExpiry);

		// when:
		subject.trackForExpiry(record);

		// then:
		verify(subject.recordExpiries).track(txnIdA, someExpiry);
	}

	@Test
	public void getsReceiptWithKnownStatusPostConsensus() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.priorityRecord()).willReturn(record);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertEquals(knownReceipt, subject.getPriorityReceipt(txnIdA));
	}

	@Test
	public void getsDuplicateRecordsAsExpected() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);
		var duplicateRecords = List.of(ExpirableTxnRecord.fromGprc(aRecord));

		given(history.duplicateRecords()).willReturn(duplicateRecords);
		given(histories.get(txnIdA)).willReturn(history);

		// when:
		var actual = subject.getDuplicateRecords(txnIdA);

		// expect:
		assertEquals(List.of(aRecord), actual);
	}

	@Test
	public void getsEmptyDuplicateListForMissing() {
		// expect:
		assertTrue(subject.getDuplicateReceipts(txnIdA).isEmpty());
	}

	@Test
	public void getsDuplicateReceiptsAsExpected() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);
		var duplicateRecords = List.of(ExpirableTxnRecord.fromGprc(aRecord));

		given(history.duplicateRecords()).willReturn(duplicateRecords);
		given(histories.get(txnIdA)).willReturn(history);

		// when:
		var duplicateReceipts = subject.getDuplicateReceipts(txnIdA);

		// expect:
		assertEquals(List.of(duplicateRecords.get(0).getReceipt().toGrpc()), duplicateReceipts);
	}

	@Test
	public void getsNullReceiptWhenMissing() {
		// expect:
		assertNull(subject.getPriorityReceipt(txnIdA));
	}

	@Test
	public void getsReceiptWithUnknownStatusPreconsensus() {
		given(histories.get(txnIdA)).willReturn(null);
		given(receiptCache.getIfPresent(txnIdA)).willReturn(Boolean.TRUE);

		// expect:
		assertEquals(unknownReceipt, subject.getPriorityReceipt(txnIdA));
	}

	@Test
	public void getsReceiptWithUnknownStatusWhenNoPriorityRecordExists() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.priorityRecord()).willReturn(null);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertEquals(unknownReceipt, subject.getPriorityReceipt(txnIdA));
	}

	@Test
	public void getsNullRecordWhenMissing() {
		// expect:
		assertNull(subject.getPriorityRecord(txnIdA));
	}

	@Test
	public void getsNullRecordWhenPreconsensus() {
		given(histories.get(txnIdA)).willReturn(null);

		// expect:
		assertNull(subject.getPriorityRecord(txnIdA));
	}

	@Test
	public void getsNullRecordWhenNoPriorityExists() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.priorityRecord()).willReturn(null);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertNull(subject.getPriorityRecord(txnIdA));
	}

	@Test
	public void getsRecordWhenPresent() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.priorityRecord()).willReturn(record);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertEquals(aRecord, subject.getPriorityRecord(txnIdA));
	}

	@Test
	public void addsMarkerForPreconsensusReceipt() {
		// when:
		subject.addPreConsensus(txnIdB);

		// then:
		verify(receiptCache).put(txnIdB, Boolean.TRUE);
	}

	@Test
	public void delegatesToPutPostConsensus() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(histories.computeIfAbsent(argThat(txnIdA::equals), any())).willReturn(history);

		// when:
		subject.setPostConsensus(
				txnIdA,
				aRecord.getReceipt().getStatus(),
				record);
		// then:
		verify(history).observe(record, aRecord.getReceipt().getStatus());
	}

	@Test
	public void managesFailInvalidRecordsAsExpected() {
		// setup:
		Instant consensusTime = Instant.now();
		TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build();
		Transaction signedTxn = Transaction.newBuilder()
				.setBody(TransactionBody.newBuilder()
					.setTransactionID(txnId)
					.setMemo("Catastrophe!"))
				.build();
		// and:
		com.swirlds.common.Transaction platformTxn = new com.swirlds.common.Transaction(signedTxn.toByteArray());
		// and:
		ArgumentCaptor<ExpirableTxnRecord> captor = ArgumentCaptor.forClass(ExpirableTxnRecord.class);
		// and:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);
		// and:
		AccountID effectivePayer = IdUtils.asAccount("0.0.3");

		given(histories.computeIfAbsent(argThat(txnId::equals), any())).willReturn(history);

		// given:
		PlatformTxnAccessor accessor = uncheckedAccessorFor(platformTxn);
		// and:
		var grpc = TransactionRecord.newBuilder()
				.setTransactionID(txnId)
				.setReceipt(TransactionReceipt.newBuilder().setStatus(FAIL_INVALID))
				.setMemo(accessor.getTxn().getMemo())
				.setTransactionHash(sha384HashOf(accessor))
				.setConsensusTimestamp(asTimestamp(consensusTime))
				.build();
		var expectedRecord = ExpirableTxnRecord.fromGprc(grpc);
		expectedRecord.setExpiry(consensusTime.getEpochSecond() + 180);
		expectedRecord.setSubmittingMember(submittingMember);
		given(creator.createExpiringPayerRecord(any(), any(), anyLong(), anyLong())).willReturn(expectedRecord);

		// when:
		subject.setFailInvalid(
				effectivePayer,
				accessor,
				consensusTime,
				submittingMember);

		// then:
		verify(history).observe(
				argThat(expectedRecord::equals),
				argThat(FAIL_INVALID::equals));
	}

	@Test
	public void usesHistoryThenCacheToTestReceiptPresence() {
		given(histories.containsKey(txnIdA)).willReturn(true);
		given(receiptCache.getIfPresent(txnIdA)).willReturn(null);
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
}
