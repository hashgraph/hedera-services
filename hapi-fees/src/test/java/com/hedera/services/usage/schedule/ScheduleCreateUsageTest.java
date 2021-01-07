package com.hedera.services.usage.schedule;/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateUsageTest {

	Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	byte[] transactionBody = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09};
	long now = 1_000L;
	int scheduledTXExpiry = 1000;
	AccountID payer = IdUtils.asAccount("0.0.2");

	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	SignatureMap sigMap = SignatureMap.newBuilder()
			.addSigPair(
					SignaturePair.newBuilder()
							.setPubKeyPrefix(ByteString.copyFrom(new byte[]{0x01}))
							.setECDSA384(ByteString.copyFrom(new byte[]{0x01, 0x02}))
							.build()
			).build();

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
		var expectedTxBytes = transactionBody.length;
		var expectedRamBytes = baseRamBytes();
		givenBaseOp();

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage, scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForAdminKey() {
		// setup:
		var expectedTxBytes = transactionBody.length + FeeBuilder.getAccountKeyStorageSize(adminKey);
		var expectedRamBytes = baseRamBytes() + FeeBuilder.getAccountKeyStorageSize(adminKey);
		givenOpWithAdminKey();

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage, scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForPayer() {
		// setup:
		var expectedTxBytes = transactionBody.length + BASIC_ENTITY_ID_SIZE;
		var expectedRamBytes = baseRamBytes();
		givenOpWithPayer();

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage, scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForSigMap() {
		// setup:
		var expectedTxBytes = transactionBody.length + SCHEDULE_ENTITY_SIZES.bptScheduleReprGiven(sigMap);
		var expectedRamBytes = baseRamBytes() + SCHEDULE_ENTITY_SIZES.sigBytesInScheduleReprGiven(sigMap);
		givenOpWithSigMap();

		// and:
		subject = ScheduleCreateUsage.newEstimate(txn, sigUsage, scheduledTXExpiry);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * scheduledTXExpiry);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private long baseRamBytes() {
		return SCHEDULE_ENTITY_SIZES.bytesInBaseReprGiven(transactionBody);
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

	private void givenOpWithSigMap() {
		op = ScheduleCreateTransactionBody.newBuilder()
				.setTransactionBody(ByteString.copyFrom(transactionBody))
				.setSigMap(sigMap)
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
