package com.hedera.services.usage.schedule;

/*
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ScheduleCreateUsageTest {
	Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	AccountID payer = IdUtils.asAccount("0.0.2");
	byte[] transactionBody = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(payer))
			.build().toByteArray();
	SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(1_234_567L)
			.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
					.setDeleteAccountID(payer))
			.build();

	long now = 1_000L;
	int scheduledTXExpiry = 1000;
	String memo = "Just some memo!";

	int scheduledTxnIdSize = BASIC_TX_ID_SIZE + BOOL_SIZE;

	ScheduleCreateTransactionBody op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	ScheduleCreateUsage subject;

	@BeforeEach
	public void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TxnUsage.estimatorFactory = factory;
	}

	@Test
	public void createsExpectedDeltaForTXExpiry() {
		// setup:
		int numSigs = 3, sigSize = 144, numPayerKeys = 1;
		SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

		givenBaseOp();
		given(base.getSigUsage()).willReturn(sigUsage);

		var expectedTxBytes = scheduledTxn.getSerializedSize();
		var expectedRamBytes = baseRamBytes() + SCHEDULE_ENTITY_SIZES.sigBytesForAddingSigningKeys(2);

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage)
				.givenScheduledTxn(scheduledTxn)
				.givenScheduledTxExpirationTimeSecs(scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForAdminKey() {
		// setup:
		int numSigs = 3, sigSize = 144, numPayerKeys = 1;
		SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
		int adminKeySize = FeeBuilder.getAccountKeyStorageSize(adminKey);

		var expectedTxBytes = scheduledTxn.getSerializedSize() + adminKeySize;
		var expectedRamBytes = baseRamBytes() + adminKeySize + SCHEDULE_ENTITY_SIZES.sigBytesForAddingSigningKeys(2);

		givenOpWithAdminKey();
		given(base.getSigUsage()).willReturn(sigUsage);

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage)
				.givenScheduledTxn(scheduledTxn)
				.givenScheduledTxExpirationTimeSecs(scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForPayer() {
		// setup:
		int numSigs = 3, sigSize = 144, numPayerKeys = 1;
		SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

		var expectedTxBytes = scheduledTxn.getSerializedSize() + BASIC_ENTITY_ID_SIZE;
		var expectedRamBytes = baseRamBytes() + SCHEDULE_ENTITY_SIZES.sigBytesForAddingSigningKeys(2);

		givenOpWithPayer();
		given(base.getSigUsage()).willReturn(sigUsage);

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage)
				.givenScheduledTxn(scheduledTxn)
				.givenScheduledTxExpirationTimeSecs(scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForMemo() {
		// setup:
		int numSigs = 3, sigSize = 144, numPayerKeys = 1;
		SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

		var expectedTxBytes = scheduledTxn.getSerializedSize() + memo.length();
		var expectedRamBytes = baseRamBytesWithMemo() + SCHEDULE_ENTITY_SIZES.sigBytesForAddingSigningKeys(2);

		givenOpWithMemo();
		given(base.getSigUsage()).willReturn(sigUsage);

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage)
				.givenScheduledTxn(scheduledTxn)
				.givenScheduledTxExpirationTimeSecs(scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private long baseRamBytes() {
		return SCHEDULE_ENTITY_SIZES.bytesInReprGiven(scheduledTxn, 0);
	}

	private long baseRamBytesWithMemo() {
		return SCHEDULE_ENTITY_SIZES.bytesInReprGiven(scheduledTxn, ByteString.copyFromUtf8(memo).size());
	}

	private void givenBaseOp() {
		op = ScheduleCreateTransactionBody.newBuilder()
				.setTransactionBody(ByteString.copyFrom(transactionBody))
				.build();
		setTxn();
	}

	private void givenOpWithAdminKey() {
		op = ScheduleCreateTransactionBody.newBuilder()
				.setTransactionBody(ByteString.copyFrom(transactionBody))
				.setAdminKey(adminKey)
				.build();
		setTxn();
	}

	private void givenOpWithPayer() {
		op = ScheduleCreateTransactionBody.newBuilder()
				.setTransactionBody(ByteString.copyFrom(transactionBody))
				.setPayerAccountID(payer)
				.build();
		setTxn();
	}

	private void givenOpWithMemo() {
		op = ScheduleCreateTransactionBody.newBuilder()
				.setTransactionBody(ByteString.copyFrom(transactionBody))
				.setMemo(memo)
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setScheduleCreate(op)
				.build();
	}
}
