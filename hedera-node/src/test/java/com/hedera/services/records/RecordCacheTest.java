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
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
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

	private ExpirableTxnRecord jaRecord = ExpirableTxnRecord.fromGprc(aRecord);

	private Cache delegate;
	private RecordCache subject;

	@BeforeEach
	private void setup() {
		delegate = mock(Cache.class);
		subject = new RecordCache(delegate);
	}

	@Test
	public void getsNullReceiptWhenMissing() {
		// expect:
		assertNull(subject.getReceipt(txnIdA));
	}

	@Test
	public void getsNullRecordWhenMissing() {
		// expect:
		assertNull(subject.getRecord(txnIdA));
	}

	@Test
	public void getsNullRecordWhenPreconsensus() {
		given(delegate.getIfPresent(txnIdA)).willReturn(Optional.empty());

		// expect:
		assertNull(subject.getRecord(txnIdA));
	}

	@Test
	public void getsRecordWhenPresent() {
		given(delegate.getIfPresent(txnIdA)).willReturn(Optional.of(jaRecord));

		// expect:
		assertEquals(aRecord, subject.getRecord(txnIdA));
	}

	@Test
	public void getsReceiptWithKnownStatusPostConsensus() {
		given(delegate.getIfPresent(txnIdA)).willReturn(Optional.of(jaRecord));

		// expect:
		assertEquals(knownReceipt, subject.getReceipt(txnIdA));
	}

	@Test
	public void addsEmptyOptionalForPreconsensusReceipt() {
		// when:
		subject.addPreConsensus(txnIdB);

		// then:
		verify(delegate).put(txnIdB, Optional.empty());
	}

	@Test
	public void delegatesToPutPostConsensus() {
		// when:
		subject.setPostConsensus(txnIdA, jaRecord);

		// then:
		verify(delegate).put(txnIdA, Optional.of(jaRecord));
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
		com.swirlds.common.Transaction platformTxn = new com.swirlds.common.Transaction(signedTxn.toByteArray());
		// and:
		ArgumentCaptor<Optional<ExpirableTxnRecord>> captor = ArgumentCaptor.forClass(Optional.class);

		// given:
		PlatformTxnAccessor accessor = uncheckedAccessorFor(platformTxn);

		// when:
		subject.setFailInvalid(accessor, consensusTime);

		// then:
		verify(delegate).put(argThat(txnId::equals), captor.capture());
		// and:
		ExpirableTxnRecord record = captor.getValue().get();
		assertEquals("FAIL_INVALID", record.getReceipt().getStatus());
		assertEquals("Catastrophe!", record.getMemo());
		assertEquals(txnId, record.getTxnId().toGrpc());
		assertEquals(asTimestamp(consensusTime), record.getConsensusTimestamp().toGrpc());
		assertArrayEquals(sha384HashOf(accessor).toByteArray(), record.getTxnHash(), "Wrong hash!");
	}

	@Test
	public void getsReceiptWithUnknownStatusPreconsensus() {
		given(delegate.getIfPresent(txnIdA)).willReturn(Optional.empty());

		// expect:
		assertEquals(unknownReceipt, subject.getReceipt(txnIdA));
	}

	@Test
	public void usesDelegateToTestRecordPresence() {
		given(delegate.getIfPresent(txnIdC)).willReturn(null);
		given(delegate.getIfPresent(txnIdB)).willReturn(Optional.empty());
		given(delegate.getIfPresent(txnIdA)).willReturn(Optional.of(jaRecord));

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
