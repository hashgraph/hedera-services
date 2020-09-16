package com.hedera.services.usage;

import com.hedera.services.test.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.test.UsageUtils.A_USAGE_VECTOR;
import static com.hedera.services.test.UsageUtils.NETWORK_RBH;
import static com.hedera.services.test.UsageUtils.NUM_PAYER_KEYS;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECIEPT_STORAGE_TIME_SEC;
import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(JUnitPlatform.class)
class SingletonEstimatorUtilsTest {
	String memo = "abcdefgh";
	SigUsage sigUsage = new SigUsage(3, 256, 2);
	TransferList transfers = TxnUtils.withAdjustments(
			asAccount("0.0.2"), -2,
			asAccount("0.0.3"), 1,
			asAccount("0.0.4"), 1);

	@Test
	public void hasExpectedBaseNetworkRbs() {
		// expect:
		assertEquals( BASIC_RECEIPT_SIZE * RECIEPT_STORAGE_TIME_SEC, ESTIMATOR_UTILS.baseNetworkRbs());
	}

	@Test
	public void partitionsAsExpected() {
		// expect:
		assertEquals(
				A_USAGES_MATRIX,
				ESTIMATOR_UTILS.withDefaultPartitioning(A_USAGE_VECTOR, NETWORK_RBH, NUM_PAYER_KEYS));
	}

	@Test
	public void understandsStartTime() {
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
	public void getsBaseRecordBytesForNonTransfer() {
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
	public void getsBaseRecordBytesForTransfer() {
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
	public void avoidsDegeneracy() {
		// expect:
		assertEquals(0, ESTIMATOR_UTILS.nonDegenerateDiv(0, 60));
		assertEquals(1, ESTIMATOR_UTILS.nonDegenerateDiv(1, 60));
		assertEquals(5, ESTIMATOR_UTILS.nonDegenerateDiv(301, 60));
	}
}
