package com.hedera.services.usage.token;

import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreeze;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;

@RunWith(JUnitPlatform.class)
public class TokenFreezeUsageTest {
	long now = 1_234_567L;
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	String symbol = "ABCDEFGHIJKL";
	TokenID id = IdUtils.asToken("0.0.75231");

	TokenFreeze op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	TokenFreezeUsage subject;

	@BeforeEach
	public void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TokenFreezeUsage.estimatorFactory = factory;
	}

	@Test
	public void createsExpectedDeltaForSymbolRef() {
		givenSymbolRefOp();
		// and:
		subject = TokenFreezeUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(symbol.length());
		verify(base).addBpt(FeeBuilder.BASIC_ENTITY_ID_SIZE);
	}

	@Test
	public void createsExpectedDeltaForIdRef() {
		givenIdRefOp();
		// and:
		subject = TokenFreezeUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base, times(2)).addBpt(FeeBuilder.BASIC_ENTITY_ID_SIZE);
	}

	private void givenSymbolRefOp() {
		op = TokenFreeze.newBuilder()
				.setToken(TokenRef.newBuilder().setSymbol(symbol))
				.build();
		setTxn();
	}

	private void givenIdRefOp() {
		op = TokenFreeze.newBuilder()
				.setToken(TokenRef.newBuilder().setTokenId(id))
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenFreeze(op)
				.build();
	}
}
