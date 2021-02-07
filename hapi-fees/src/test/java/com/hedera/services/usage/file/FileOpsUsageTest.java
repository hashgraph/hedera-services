package com.hedera.services.usage.file;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
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
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileOpsUsageTest {
	byte[] contents = "Pineapple and eggplant and avocado too".getBytes();
	long now = 1_234_567L;
	long expiry = 2_345_678L;
	long period = expiry - now;
	Key wacl = KeyUtils.A_KEY_LIST;
	String memo = "Verily, I say unto you";
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

	EstimatorFactory factory;
	TxnUsageEstimator base;

	FileCreateTransactionBody creationOp;
	FileUpdateTransactionBody updateOp;
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
	void estimatesCreationAsExpected() {
		givenCreationOp();
		// and given:
		long sb = reprSize();
		long bytesUsed = reprSize() - FileOpsUsage.bytesInBaseRepr();

		// when:
		var estimate = subject.fileCreateUsage(txn, sigUsage);

		// then:
		assertEquals(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(bytesUsed);
		verify(base).addSbs(sb * period);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	void estimatesUpdateAsExpected() {
		// setup:
		long oldExpiry = expiry - 1_234L;
		byte[] oldContents = "Archiac".getBytes();
		KeyList oldWacl = KeyUtils.A_KEY_LIST.getKeyList();
		String oldMemo = "Lettuce";
		// and:
		long bytesUsed = reprSize() - FileOpsUsage.bytesInBaseRepr();
		// and:
		long oldSbs = (oldExpiry - now) *
				(oldContents.length
						+ oldMemo.length()
						+ getAccountKeyStorageSize(Key.newBuilder().setKeyList(oldWacl).build()));
		// and:
		long newSbs = (expiry - now) * bytesUsed;

		givenUpdateOp();
		// and:
		var ctx = FileUpdateContext.newBuilder()
				.setCurrentExpiry(oldExpiry)
				.setCurrentMemo(oldMemo)
				.setCurrentWacl(oldWacl)
				.setCurrentSize(oldContents.length)
				.build();

		// when:
		var estimate = subject.fileUpdateUsage(txn, sigUsage, ctx);

		// then:
		assertEquals(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(bytesUsed + BASIC_ENTITY_ID_SIZE);
		verify(base).addSbs(newSbs - oldSbs);
	}

	@Test
	void hasExpectedBaseReprSize() {
		// given:
		int expected = FeeBuilder.BOOL_SIZE + FeeBuilder.LONG_SIZE;

		// expect:
		assertEquals(expected, FileOpsUsage.bytesInBaseRepr());
	}

	private long reprSize() {
		return FileOpsUsage.bytesInBaseRepr()
				+ contents.length
				+ memo.length()
				+ FeeBuilder.getAccountKeyStorageSize(wacl);
	}

	private void givenUpdateOp() {
		updateOp = FileUpdateTransactionBody.newBuilder()
				.setContents(ByteString.copyFrom(contents))
				.setMemo(StringValue.newBuilder().setValue(memo))
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.setKeys(wacl.getKeyList())
				.build();
		setUpdateTxn();
	}

	private void givenCreationOp() {
		creationOp = FileCreateTransactionBody.newBuilder()
				.setContents(ByteString.copyFrom(contents))
				.setMemo(memo)
				.setKeys(wacl.getKeyList())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.build();
		setCreateTxn();
	}

	private void setCreateTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setFileCreate(creationOp)
				.build();
	}

	private void setUpdateTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setFileUpdate(updateOp)
				.build();
	}
}