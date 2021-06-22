package com.hedera.services.usage;

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

import com.hedera.services.test.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.test.UsageUtils.A_QUERY_USAGES_MATRIX;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.test.UsageUtils.A_USAGE_VECTOR;
import static com.hedera.services.test.UsageUtils.NETWORK_RBH;
import static com.hedera.services.test.UsageUtils.NUM_PAYER_KEYS;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SingletonEstimatorUtilsTest {
	private long maxLifetime = 100 * 365 * 24 * 60 * 60L;
	private String memo = "abcdefgh";
	private SigUsage sigUsage = new SigUsage(3, 256, 2);
	private TransferList transfers = TxnUtils.withAdjustments(
			asAccount("0.0.2"), -2,
			asAccount("0.0.3"), 1,
			asAccount("0.0.4"), 1);

	@Test
	void byteSecondsUsagePeriodsAreCappedAtOneCentury() {
		// given:
		final long oldUsage = 1_234L;
		final long newUsage = 2_345L;
		final long oldLifetime = maxLifetime + 1;
		final long newLifetime = 2 * maxLifetime + 1;
		// and:
		final long cappedChange = maxLifetime * (newUsage - oldUsage);

		// when:
		final var result = ESTIMATOR_UTILS.changeInBsUsage(oldUsage, oldLifetime, newUsage, newLifetime);

		// then:
		assertEquals(cappedChange, result);
	}

	@Test
	void hasExpectedBaseEstimate() {
		// given:
		TransactionBody txn = TransactionBody.newBuilder()
				.setMemo("You won't want to hear this.")
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(123L)
										.setAccountID(AccountID.newBuilder().setAccountNum(75231)))))
				.build();
		// and:
		long expectedBpt = ESTIMATOR_UTILS.baseBodyBytes(txn) + sigUsage.sigsSize();
		long expectedRbs = ESTIMATOR_UTILS.baseRecordBytes(txn) * RECEIPT_STORAGE_TIME_SEC;

		// when:
		var est = ESTIMATOR_UTILS.baseEstimate(txn, sigUsage);

		// then:
		assertEquals(1L * INT_SIZE, est.base().getBpr());
		assertEquals(sigUsage.numSigs(), est.base().getVpt());
		assertEquals(expectedBpt, est.base().getBpt());
		assertEquals(expectedRbs, est.getRbs());
	}

	@Test
	void hasExpectedBaseNetworkRbs() {
		// expect:
		assertEquals( BASIC_RECEIPT_SIZE * RECEIPT_STORAGE_TIME_SEC, ESTIMATOR_UTILS.baseNetworkRbs());
	}

	@Test
	void partitionsAsExpected() {
		// expect:
		assertEquals(
				A_USAGES_MATRIX,
				ESTIMATOR_UTILS.withDefaultTxnPartitioning(A_USAGE_VECTOR, SubType.DEFAULT, NETWORK_RBH, NUM_PAYER_KEYS));
	}

	@Test
	void partitionsAreDifferentWithSubtype() {
		//expect
		assertNotEquals(
				A_USAGES_MATRIX,
				ESTIMATOR_UTILS.withDefaultTxnPartitioning(
						A_USAGE_VECTOR,
						SubType.TOKEN_FUNGIBLE_COMMON,
						NETWORK_RBH,
						NUM_PAYER_KEYS));
		//and
		assertNotEquals(
				A_USAGES_MATRIX,
				ESTIMATOR_UTILS.withDefaultTxnPartitioning(
						A_USAGE_VECTOR,
						SubType.TOKEN_NON_FUNGIBLE_UNIQUE,
						NETWORK_RBH,
						NUM_PAYER_KEYS));
	}

	@Test
	void partitionsQueriesAsExpected() {
		// expect:
		assertEquals(
				A_QUERY_USAGES_MATRIX,
				ESTIMATOR_UTILS.withDefaultQueryPartitioning(A_USAGE_VECTOR));
	}

	@Test
	void understandsStartTime() {
		// given:
		long now = Instant.now().getEpochSecond();
		long then = 4688462211L;
		var txnId = TransactionID.newBuilder()
				.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now));
		var txn = TransactionBody.newBuilder().setTransactionID(txnId).build();

		// when:
		long lifetime = ESTIMATOR_UTILS.relativeLifetime(txn, then);

		// then:
		assertEquals(then - now, lifetime);
	}

	@Test
	void getsBaseRecordBytesForNonTransfer() {
		// given:
		TransactionBody txn = TransactionBody.newBuilder()
				.setMemo(memo)
				.build();
		// and:
		int expected = FeeBuilder.BASIC_TX_RECORD_SIZE + memo.length();

		// when:
		int actual = ESTIMATOR_UTILS.baseRecordBytes(txn);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void getsBaseRecordBytesForTransfer() {
		// given:
		TransactionBody txn = TransactionBody.newBuilder()
				.setMemo(memo)
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transfers))
				.build();
		// and:
		int expected = FeeBuilder.BASIC_TX_RECORD_SIZE + memo.length()
				+ FeeBuilder.BASIC_ACCOUNT_AMT_SIZE * transfers.getAccountAmountsCount();

		// when:
		int actual = ESTIMATOR_UTILS.baseRecordBytes(txn);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void avoidsDegeneracy() {
		// expect:
		assertEquals(0, ESTIMATOR_UTILS.nonDegenerateDiv(0, 60));
		assertEquals(1, ESTIMATOR_UTILS.nonDegenerateDiv(1, 60));
		assertEquals(5, ESTIMATOR_UTILS.nonDegenerateDiv(301, 60));
	}
}
