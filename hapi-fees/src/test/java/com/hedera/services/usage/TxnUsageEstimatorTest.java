package com.hedera.services.usage;

/*-
 * ‌
 * Hedera Services Node
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

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.test.SigUtils.A_SIG_MAP;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.test.UsageUtils.A_USAGE_VECTOR;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

public class TxnUsageEstimatorTest {
	int numPayerKeys = 2;
	long networkRbs = 123;
	SigUsage sigUsage = new SigUsage(A_SIG_MAP.getSigPairCount(), A_SIG_MAP.getSerializedSize(), numPayerKeys);
	TransactionBody txn = TransactionBody.newBuilder().build();

	EstimatorUtils utils;
	TxnUsageEstimator subject;

	@BeforeEach
	public void setUp() throws Exception {
		utils = mock(EstimatorUtils.class);

		subject = new TxnUsageEstimator(sigUsage, txn, utils);
	}

	@Test
	public void plusHelpersWork() {
		given(utils.nonDegenerateDiv(anyLong(), anyInt())).willReturn(1L);
		given(utils.baseNetworkRbs()).willReturn(networkRbs);
		given(utils.baseEstimate(txn, sigUsage)).willReturn(baseEstimate());
		given(utils.withDefaultTxnPartitioning(
				expectedEstimate().build(),
				ESTIMATOR_UTILS.nonDegenerateDiv(2 * networkRbs, HRS_DIVISOR),
				sigUsage.numPayerKeys())).willReturn(A_USAGES_MATRIX);
		// and:
		subject.addBpt(A_USAGE_VECTOR.getBpt())
				.addVpt(A_USAGE_VECTOR.getVpt())
				.addRbs(A_USAGE_VECTOR.getRbh() * HRS_DIVISOR)
				.addSbs(A_USAGE_VECTOR.getSbh() * HRS_DIVISOR)
				.addGas(A_USAGE_VECTOR.getGas())
				.addTv(A_USAGE_VECTOR.getTv())
				.addNetworkRbs(networkRbs);

		// when:
		var actual = subject.get();

		// then:
		assertSame(A_USAGES_MATRIX, actual);
	}

	private UsageEstimate expectedEstimate() {
		var updatedUsageVector = A_USAGE_VECTOR.toBuilder()
				.setBpt(2 * A_USAGE_VECTOR.getBpt())
				.setVpt(2 * A_USAGE_VECTOR.getVpt())
				.setGas(2 * A_USAGE_VECTOR.getGas())
				.setTv(2 * A_USAGE_VECTOR.getTv());
		var base = new UsageEstimate(updatedUsageVector);
		base.addRbs(2 * A_USAGE_VECTOR.getRbh() * HRS_DIVISOR);
		base.addSbs(2 * A_USAGE_VECTOR.getSbh() * HRS_DIVISOR);
		return base;
	}

	private UsageEstimate baseEstimate() {
		var updatedUsageVector = A_USAGE_VECTOR.toBuilder()
				.setBpt(A_USAGE_VECTOR.getBpt())
				.setVpt(A_USAGE_VECTOR.getVpt())
				.setGas(A_USAGE_VECTOR.getGas())
				.setTv(A_USAGE_VECTOR.getTv());
		var base = new UsageEstimate(updatedUsageVector);
		base.addRbs(A_USAGE_VECTOR.getRbh() * HRS_DIVISOR);
		base.addSbs(A_USAGE_VECTOR.getSbh() * HRS_DIVISOR);
		return base;
	}

	@Test
	public void baseEstimateDelegatesAsExpected() {
		given(utils.nonDegenerateDiv(anyLong(), anyInt())).willReturn(1L);
		given(utils.baseNetworkRbs()).willReturn(networkRbs);
		given(utils.baseEstimate(txn, sigUsage)).willReturn(baseEstimate());
		given(utils.withDefaultTxnPartitioning(
				baseEstimate().build(),
				ESTIMATOR_UTILS.nonDegenerateDiv(networkRbs, HRS_DIVISOR),
				sigUsage.numPayerKeys())).willReturn(A_USAGES_MATRIX);

		// when:
		var actual = subject.get();

		// then:
		assertSame(A_USAGES_MATRIX, actual);
	}
}
