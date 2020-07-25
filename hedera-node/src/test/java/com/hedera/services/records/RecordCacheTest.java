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

import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hedera.services.utils.PlatformTxnAccessor.uncheckedAccessorFor;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.cache.Cache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.utils.MiscUtils.sha384HashOf;

@RunWith(JUnitPlatform.class)
class RecordCacheTest {
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

	private Cache<TransactionID, Optional<TransactionRecord>> delegate;
	private RecordCache subject;

	@BeforeEach
	private void setup() {
//		delegate = (Cache<TransactionID, Optional<TransactionRecord>>)mock(Cache.class);
//		subject = new RecordCache(delegate);

		creator = mock(EntityCreator.class);
		histories = (Map<TransactionID, TxnIdRecentHistory>)mock(Map.class);
		receiptCache = (Cache<TransactionID, Boolean>)mock(Cache.class);
		subject = new RecordCache(creator, receiptCache, histories);
	}

	@Test
	public void getsReceiptWithKnownStatusPostConsensus() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.legacyQueryableRecord()).willReturn(record);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertEquals(knownReceipt, subject.getReceipt(txnIdA));
	}


	@Test
	public void getsNullReceiptWhenMissing() {
		// expect:
		assertNull(subject.getReceipt(txnIdA));
	}

	@Test
	public void getsReceiptWithUnknownStatusPreconsensus() {
		given(histories.get(txnIdA)).willReturn(null);
		given(receiptCache.getIfPresent(txnIdA)).willReturn(Boolean.TRUE);

		// expect:
		assertEquals(unknownReceipt, subject.getReceipt(txnIdA));
	}


	@Test
	public void getsNullRecordWhenMissing() {
		// expect:
		assertNull(subject.getRecord(txnIdA));
	}

	@Test
	public void getsNullRecordWhenPreconsensus() {
		given(histories.get(txnIdA)).willReturn(null);

		// expect:
		assertNull(subject.getRecord(txnIdA));
	}

	@Test
	public void getsNullRecordWhenNotLegacyQueryable() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.legacyQueryableRecord()).willReturn(null);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertNull(subject.getRecord(txnIdA));
	}

	@Test
	public void getsRecordWhenPresent() {
		// setup:
		TxnIdRecentHistory history = mock(TxnIdRecentHistory.class);

		given(history.legacyQueryableRecord()).willReturn(record);
		given(histories.get(txnIdA)).willReturn(history);

		// expect:
		assertEquals(aRecord, subject.getRecord(txnIdA));
	}

	@Test
	public void addsEmptyOptionalForPreconsensusReceipt() {
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
				record,
				submittingMember);

		// then:
		verify(history).observe(record, aRecord.getReceipt().getStatus(), submittingMember);
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
		given(creator.createExpiringPayerRecord(any(), any(), anyLong())).willReturn(expectedRecord);

		// when:
		subject.setFailInvalid(
				effectivePayer,
				accessor,
				consensusTime,
				submittingMember);

		// then:
		verify(history).observe(
				argThat(expectedRecord::equals),
				argThat(FAIL_INVALID::equals),
				longThat(l -> l == submittingMember));
	}

	@Test
	public void usesDelegateToTestRecordPresence() {
		given(delegate.getIfPresent(txnIdC)).willReturn(null);
		given(delegate.getIfPresent(txnIdB)).willReturn(Optional.empty());
		given(delegate.getIfPresent(txnIdA)).willReturn(Optional.of(aRecord));

		// when:
		boolean hasA = subject.isRecordPresent(txnIdA);
		boolean hasB = subject.isRecordPresent(txnIdB);
		boolean hasC = subject.isRecordPresent(txnIdC);

		// then:
		verify(delegate, times(3)).getIfPresent(any());
		// and:
		assertTrue(hasA);
		assertFalse(hasB);
		assertFalse(hasC);
	}

	@Test
	public void usesDelegateToTestReceiptPresence() {
		given(delegate.getIfPresent(txnIdA)).willReturn(null);
		given(delegate.getIfPresent(txnIdB)).willReturn(Optional.empty());

		// when:
		boolean hasA = subject.isReceiptPresent(txnIdA);
		boolean hasB = subject.isReceiptPresent(txnIdB);

		// then:
		verify(delegate, times(2)).getIfPresent(any());
		// and:
		assertFalse(hasA);
		assertTrue(hasB);
	}
}
