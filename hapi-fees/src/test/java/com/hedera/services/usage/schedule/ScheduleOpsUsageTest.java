package com.hedera.services.usage.schedule;

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
import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
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
import static com.hederahashgraph.api.proto.java.SubType.SCHEDULE_CREATE_CONTRACT_CALL;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.KEY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduleOpsUsageTest {
	private int numSigs = 3, sigSize = 144, numPayerKeys = 1;
	private int scheduledTxnIdSize = BASIC_TX_ID_SIZE + BOOL_SIZE;
	private long now = 1_234_567L;
	private long lifetimeSecs = 1800L;
	private SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

	private Key adminKey = KeyUtils.A_COMPLEX_KEY;
	private ScheduleID id = IdUtils.asSchedule("0.0.1");
	private String memo = "This is just a memo?";
	private AccountID payer = IdUtils.asAccount("0.0.2");
	private SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(1_234_567L)
			.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
					.setDeleteAccountID(payer))
			.build();

	private SchedulableTransactionBody scheduledTxnWithContractCall = SchedulableTransactionBody.newBuilder()
			.setTransactionFee(1_234_567L)
			.setContractCall(ContractCallTransactionBody.newBuilder())
			.build();

	private EstimatorFactory factory;
	private TxnUsageEstimator base;
	private Function<ResponseType, QueryUsage> queryEstimatorFactory;
	private QueryUsage queryBase;

	private ScheduleOpsUsage subject = new ScheduleOpsUsage();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);
		given(base.get(SCHEDULE_CREATE_CONTRACT_CALL)).willReturn(A_USAGES_MATRIX);
		queryBase = mock(QueryUsage.class);
		given(queryBase.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);
		queryEstimatorFactory = mock(Function.class);
		given(queryEstimatorFactory.apply(ANSWER_STATE_PROOF)).willReturn(queryBase);

		subject.txnEstimateFactory = factory;
		subject.queryEstimateFactory = queryEstimatorFactory;
	}

	@Test
	void estimatesSignAsExpected() {
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
	void estimatesDeleteExpected() {
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
	void estimatesCreateAsExpected() {
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
		var estimate = subject.scheduleCreateUsage(creationTxn(scheduledTxn), sigUsage, lifetimeSecs);

		// then:
		assertSame(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * lifetimeSecs);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	void estimatesCreateWithContractCallAsExpected() {
		// given:
		var createdCtx = ExtantScheduleContext.newBuilder()
				.setAdminKey(adminKey)
				.setMemo(memo)
				.setScheduledTxn(scheduledTxnWithContractCall)
				.setNumSigners(SCHEDULE_ENTITY_SIZES.estimatedScheduleSigs(sigUsage))
				.setResolved(false)
				.build();
		var expectedRamBytes = createdCtx.nonBaseRb();
		// and:
		var expectedTxBytes = scheduledTxnWithContractCall.getSerializedSize()
				+ getAccountKeyStorageSize(adminKey)
				+ memo.length()
				+ BASIC_ENTITY_ID_SIZE;

		// when:
		var estimate = subject.scheduleCreateUsage(
				creationTxn(scheduledTxnWithContractCall), sigUsage, lifetimeSecs);

		// then:
		assertSame(A_USAGES_MATRIX, estimate);
		// and:
		verify(base).addBpt(expectedTxBytes);
		verify(base).addRbs(expectedRamBytes * lifetimeSecs);
		verify(base).addNetworkRbs(
				(BASIC_ENTITY_ID_SIZE + scheduledTxnIdSize) * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	void estimatesGetInfoAsExpected() {
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
		verify(queryBase).addTb(BASIC_ENTITY_ID_SIZE);
		verify(queryBase).addRb(ctx.nonBaseRb());
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

	private TransactionBody creationTxn(SchedulableTransactionBody body) {
		return baseTxn().setScheduleCreate(creationOp(body)).build();
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

	private ScheduleCreateTransactionBody creationOp(SchedulableTransactionBody body) {
		return ScheduleCreateTransactionBody.newBuilder()
				.setMemo(memo)
				.setAdminKey(adminKey)
				.setPayerAccountID(payer)
				.setScheduledTransactionBody(body)
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
