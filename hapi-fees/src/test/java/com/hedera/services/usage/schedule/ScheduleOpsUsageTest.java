package com.hedera.services.usage.schedule;

import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.KEY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduleOpsUsageTest {
	int numSigs = 3, sigSize = 144, numPayerKeys = 1;
	int scheduledTxnIdSize = BASIC_TX_ID_SIZE + BOOL_SIZE;
	long now = 1_234_567L;
	long lifetimeSecs = 1800L;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

	Key adminKey = KeyUtils.A_COMPLEX_KEY;
	ScheduleID id = IdUtils.asSchedule("0.0.1");
	String memo = "This is just a memo?";
	AccountID payer = IdUtils.asAccount("0.0.2");
	SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(1_234_567L)
			.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
					.setDeleteAccountID(payer))
			.build();

	EstimatorFactory factory;
	TxnUsageEstimator base;
	Function<ResponseType, QueryUsage> queryEstimatorFactory;
	QueryUsage queryBase;

	ScheduleOpsUsage subject = new ScheduleOpsUsage();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);
		queryBase = mock(QueryUsage.class);
		given(queryBase.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);
		queryEstimatorFactory = mock(Function.class);
		given(queryEstimatorFactory.apply(ANSWER_STATE_PROOF)).willReturn(queryBase);

		ScheduleOpsUsage.txnEstimateFactory = factory;
		ScheduleOpsUsage.queryEstimateFactory = queryEstimatorFactory;
	}

	@Test
	public void estimatesSignAsExpected() {
		// setup:
		long lifetimeSecs = 1800L;

		// when:
		var estimate = subject.scheduleSignUsage(signingTxn(), sigUsage, now + lifetimeSecs);

		// then:
		assertSame(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(BASIC_ENTITY_ID_SIZE);
		verify(base).addRbs(2 * KEY_SIZE * lifetimeSecs);
		verify(base).addNetworkRbs(
				scheduledTxnIdSize * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void estimatesDeleteExpected() {
		// setup:
		long lifetimeSecs = 1800L;

		// when:
		var estimate = subject.scheduleDeleteUsage(deletionTxn(), sigUsage, now + lifetimeSecs);

		// then:
		assertSame(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(BASIC_ENTITY_ID_SIZE);
		verify(base).addRbs(BASIC_RICH_INSTANT_SIZE * lifetimeSecs);
	}

	@Test
	public void estimatesCreateAsExpected() {
		// given:
		var createdCtx = ExtantScheduleContext.newBuilder()
				.setAdminKey(adminKey)
				.setMemo(memo)
				.setScheduledTxn(scheduledTxn)
				.setNumSigners(SCHEDULE_ENTITY_SIZES.estimatedScheduleSigs(sigUsage))
				.setResolved(false)
				.build();
		var expectedRamBytes = createdCtx.nonBaseRb();
		// and:
		var expectedTxBytes = scheduledTxn.getSerializedSize()
				+ getAccountKeyStorageSize(adminKey)
				+ memo.length()
				+ BASIC_ENTITY_ID_SIZE;

		// when:
		var estimate = subject.scheduleCreateUsage(creationTxn(), sigUsage, lifetimeSecs);

		// then:
		assertSame(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * lifetimeSecs);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void estimatesGetInfoAsExpected() {
		// given:
		var ctx = ExtantScheduleContext.newBuilder()
				.setAdminKey(adminKey)
				.setMemo(memo)
				.setNumSigners(2)
				.setResolved(true)
				.setScheduledTxn(scheduledTxn)
				.build();

		// when:
		var estimate = subject.scheduleInfoUsage(scheduleQuery(), ctx);

		// then:
		assertSame(A_USAGES_MATRIX, estimate);
		// and:
		verify(queryBase).updateTb(BASIC_ENTITY_ID_SIZE);
		verify(queryBase).updateRb(ctx.nonBaseRb());
	}

	private Query scheduleQuery() {
		var op = ScheduleGetInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder()
						.setResponseType(ANSWER_STATE_PROOF)
						.build())
				.setScheduleID(id)
				.build();
		return Query.newBuilder().setScheduleGetInfo(op).build();
	}

	private TransactionBody creationTxn() {
		return baseTxn().setScheduleCreate(creationOp()).build();
	}

	private TransactionBody deletionTxn() {
		return baseTxn().setScheduleDelete(deletionOp()).build();
	}

	private TransactionBody signingTxn() {
		return baseTxn().setScheduleSign(signingOp()).build();
	}

	private TransactionBody.Builder baseTxn() {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now))
						.build());
	}

	private ScheduleCreateTransactionBody creationOp() {
		return ScheduleCreateTransactionBody.newBuilder()
				.setMemo(memo)
				.setAdminKey(adminKey)
				.setPayerAccountID(payer)
				.setScheduledTransactionBody(scheduledTxn)
				.build();
	}

	private ScheduleDeleteTransactionBody deletionOp() {
		return ScheduleDeleteTransactionBody.newBuilder()
				.setScheduleID(id)
				.build();
	}

	private ScheduleSignTransactionBody signingOp() {
		return ScheduleSignTransactionBody.newBuilder()
				.setScheduleID(id)
				.build();
	}
}