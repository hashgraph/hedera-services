package com.hedera.services.usage.token;

import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class TokenMintUsageTest {
	long now = 1_234_567L;
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	String symbol = "ABCDEFGHIJ";
	TokenID id = IdUtils.asToken("0.0.75231");
	TokenRef token;

	TokenMintTransactionBody op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	TokenMintUsage subject;

	@BeforeEach
	public void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TokenMintUsage.estimatorFactory = factory;
	}

	@Test
	public void createsExpectedDeltaForSymbolRef() {
		givenSymbolRefOp();
		// and:
		subject = TokenMintUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(symbol.length());
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTransfers(1, 1) *
				USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForIdRef() {
		givenIdRefOp();
		// and:
		subject = TokenMintUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(FeeBuilder.BASIC_ENTITY_ID_SIZE);
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTransfers(1, 1) *
						USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private void givenSymbolRefOp() {
		op = TokenMintTransactionBody.newBuilder()
				.setToken(TokenRef.newBuilder().setSymbol(symbol))
				.build();
		setTxn();
	}

	private void givenIdRefOp() {
		op = TokenMintTransactionBody.newBuilder()
				.setToken(TokenRef.newBuilder().setTokenId(id))
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenMint(op)
				.build();
	}
}
