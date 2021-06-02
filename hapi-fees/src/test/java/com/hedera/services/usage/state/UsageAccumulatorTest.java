package com.hedera.services.usage.state;

import com.hedera.services.usage.SigUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCOUNT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static org.junit.jupiter.api.Assertions.*;

class UsageAccumulatorTest {
	final private int memoBytes = 100;
	final private int numTransfers = 2;
	final private SigUsage sigUsage = new SigUsage(2, 101, 1);

	final private long baseBpr = INT_SIZE;
	final private long baseVpt = sigUsage.numSigs();
	final private long baseBpt = BASIC_TX_BODY_SIZE + memoBytes + sigUsage.sigsSize();
	final private long baseRbs = RECEIPT_STORAGE_TIME_SEC *
			(BASIC_TX_RECORD_SIZE + memoBytes + BASIC_ACCOUNT_AMT_SIZE * numTransfers);
	final private long baseNetworkRbs = RECEIPT_STORAGE_TIME_SEC *
			BASIC_RECEIPT_SIZE;

	private UsageAccumulator subject = new UsageAccumulator();

	@BeforeEach
	void setUp() {
	}

	@Test
	void understandsNetworkPartitioning() {
		// when:
		subject.addBpt(1);
		subject.addVpt(4);
		subject.addNetworkRbs(8 * HRS_DIVISOR);

		// then:
		assertEquals(1, subject.getNetworkBpt());
		assertEquals(4, subject.getNetworkVpt());
		assertEquals(8, subject.getNetworkRbh());
	}

	@Test
	void understandsNodePartitioning() {
		// when:
		subject.addBpt(1);
		subject.addVpt(4);
		subject.addNetworkRbs(8 * HRS_DIVISOR);

		// then:
		assertEquals(1, subject.getNetworkBpt());
		assertEquals(4, subject.getNetworkVpt());
		assertEquals(8, subject.getNetworkRbh());
	}

	@Test
	void resetWorksForTxn() {
		// given:
		subject.addSbpr(3);
		subject.addGas(5);
		subject.addSbs(7);

		// when:
		subject.resetForTransaction(memoBytes, numTransfers, sigUsage);

		// then:
		assertEquals(baseBpr, subject.getBpr());
		assertEquals(baseVpt, subject.getVpt());
		assertEquals(baseBpt, subject.getBpt());
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
		// given:
		subject.addBpt(1);
		subject.addBpr(2);
		subject.addSbpr(3);
		subject.addVpt(4);
		subject.addGas(5);
		subject.addRbs(6);
		subject.addSbs(7);
		subject.addNetworkRbs(8);

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