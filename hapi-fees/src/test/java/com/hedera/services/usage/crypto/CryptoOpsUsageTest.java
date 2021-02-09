package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CryptoOpsUsageTest {
	long secs = 500_000L;
	long now = 1_234_567L;
	Key key = KeyUtils.A_COMPLEX_KEY;
	String memo = "That abler soul, which thence doth flow";
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

	EstimatorFactory factory;
	TxnUsageEstimator base;

	CryptoCreateTransactionBody creationOp;
	TransactionBody txn;

	CryptoOpsUsage subject = new CryptoOpsUsage();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		CryptoOpsUsage.txnEstimateFactory = factory;
	}

	@AfterEach
	void cleanup() {
		CryptoOpsUsage.txnEstimateFactory = TxnUsageEstimator::new;
	}

	@Test
	void estimatesCreationAsExpected() {
		givenCreationOp();
		// and given:
		long rb = reprSize();
		long bytesUsed = reprSize() - CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + LONG_SIZE;

		// when:
		var estimate = subject.cryptoCreateUsage(txn, sigUsage);

		// then:
		assertEquals(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(bytesUsed);
		verify(base).addRbs(rb * secs);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private long reprSize() {
		return CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr()
				/* The proxy account */
				+ BASIC_ENTITY_ID_SIZE
				+ memo.length()
				+ FeeBuilder.getAccountKeyStorageSize(key);
	}

	private void givenCreationOp() {
		creationOp = CryptoCreateTransactionBody.newBuilder()
				.setProxyAccountID(IdUtils.asAccount("0.0.75231"))
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(secs).build())
				.setMemo(memo)
				.setKey(key)
				.build();
		setCreateTxn();
	}

	private void setCreateTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setCryptoCreateAccount(creationOp) .build();
	}
}
