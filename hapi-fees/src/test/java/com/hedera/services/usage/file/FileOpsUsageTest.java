package com.hedera.services.usage.file;

import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
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
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileOpsUsageTest {
	long now = 1_234_567L;
	long expiry = 2_345_678L;
	long period = expiry - now;
	Key wacl = KeyUtils.A_KEY_LIST;
	String memo = "Verily, I say unto you";
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

	EstimatorFactory factory;
	TxnUsageEstimator base;

	FileCreateTransactionBody op;
	TransactionBody txn;

	FileOpsUsage subject = new FileOpsUsage();

	@BeforeEach
	void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		FileOpsUsage.estimateFactory = factory;
	}

	@AfterEach
	void cleanup() {
		FileOpsUsage.estimateFactory = TxnUsageEstimator::new;
	}

	@Test
	void estimatesAsExpected() {
		givenOp();
		// and given:
		long expectedBytes = baseSize();

		// when:
		var estimate = subject.fileCreateUsage(txn, sigUsage);

		// then:
		assertEquals(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs(expectedBytes * period);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	void hasExpectedBaseReprSize() {
		// given:
		int expected = FeeBuilder.BOOL_SIZE + FeeBuilder.LONG_SIZE;

		// expect:
		assertEquals(expected, FileOpsUsage.bytesInBaseRepr());
	}

	private long baseSize() {
		return FileOpsUsage.bytesInBaseRepr()
				+ memo.length()
				+ FeeBuilder.getAccountKeyStorageSize(wacl);
	}

	private void givenOp() {
		op = FileCreateTransactionBody.newBuilder()
				.setMemo(memo)
				.setKeys(wacl.getKeyList())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setFileCreate(op)
				.build();
	}
}