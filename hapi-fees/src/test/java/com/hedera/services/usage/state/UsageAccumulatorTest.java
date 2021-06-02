package com.hedera.services.usage.state;

import com.hedera.services.usage.SigUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCOUNT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static org.junit.jupiter.api.Assertions.*;

class UsageAccumulatorTest {
	final private int memoBytes = 100;
	final private int numTransfers = 2;
	final private SigUsage sigUsage = new SigUsage(2, 101, 1);

	private UsageAccumulator subject = new UsageAccumulator();

	@BeforeEach
	void setUp() {
		subject.addBpt(1);
		subject.addBpr(2);
		subject.addSbpr(3);
		subject.addVpt(4);
		subject.addGas(5);
		subject.addRbs(6);
		subject.addSbs(7);
		subject.addNetworkRbs(8);
	}

	@Test
	void understandsNetworkPartitioning() {

	}

	@Test
	void resetWorksForTxn() {
		// when:
		subject.resetForTransaction(memoBytes, numTransfers, sigUsage);

		// then:
		assertEquals(INT_SIZE, subject.getBpr());
		assertEquals(sigUsage.numSigs(), subject.getVpt());
		assertEquals((long)BASIC_TX_BODY_SIZE + memoBytes + sigUsage.sigsSize(), subject.getBpt());
		assertEquals(
				RECEIPT_STORAGE_TIME_SEC *
						(BASIC_TX_RECORD_SIZE + memoBytes + BASIC_ACCOUNT_AMT_SIZE * numTransfers),
				subject.getRbs());
		assertEquals(BASIC_RECEIPT_SIZE * RECEIPT_STORAGE_TIME_SEC, subject.getNetworkRbs());
		// and:
		assertEquals(0, subject.getSbpr());
		assertEquals(0, subject.getGas());
		assertEquals(0, subject.getSbs());
	}

	@Test
	void addersWork() {
		// expect:
		assertEquals(1, subject.getBpt());
		assertEquals(2, subject.getBpr());
		assertEquals(3, subject.getSbpr());
		assertEquals(4, subject.getVpt());
		assertEquals(5, subject.getGas());
		assertEquals(6, subject.getRbs());
		assertEquals(7, subject.getSbs());
		assertEquals(8, subject.getNetworkRbs());
	}
}