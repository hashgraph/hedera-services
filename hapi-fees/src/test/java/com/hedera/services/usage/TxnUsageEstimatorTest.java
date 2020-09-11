package com.hedera.services.usage;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.test.SigUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.test.UsageUtils.A_USAGE_VECTOR;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class TxnUsageEstimatorTest {
	int numPayerKeys = 2;
	long networkRbh = 123;
	SignatureMap signatureMap = SigUtils.A_SIG_MAP;
	SigUsage sigUsage = new SigUsage(signatureMap.getSigPairCount(), signatureMap.getSerializedSize(), numPayerKeys);
	FeeComponents.Builder usage = FeeComponents.newBuilder();
	TransactionBody txn = TransactionBody.newBuilder()
			.build();

	EstimatorUtils utils;

	MockEstimator subject;

	@BeforeEach
	public void setUp() throws Exception {
		utils = mock(EstimatorUtils.class);

		subject = new MockEstimator(utils);
	}

	@Test
	public void plusHelpersWork() {
		// setup:
		var updatedUsageVector = A_USAGE_VECTOR.toBuilder()
				.setBpt(2 * A_USAGE_VECTOR.getBpt())
				.setVpt(2 * A_USAGE_VECTOR.getVpt())
				.setRbh(2 * A_USAGE_VECTOR.getRbh())
				.setSbh(2 * A_USAGE_VECTOR.getSbh())
				.setGas(2 * A_USAGE_VECTOR.getGas())
				.setTv(2 * A_USAGE_VECTOR.getTv())
				.build();

		givenSubjectWithAll();
		given(utils.baseNetworkRbh()).willReturn(networkRbh);
		given(utils.newBaseEstimate(txn, sigUsage)).willReturn(A_USAGE_VECTOR.toBuilder());
		given(utils.withDefaultPartitioning(
				updatedUsageVector,
				2 * networkRbh,
				numPayerKeys)).willReturn(A_USAGES_MATRIX);
		// and:
		subject.plusBpt(A_USAGE_VECTOR.getBpt())
				.plusVpt(A_USAGE_VECTOR.getVpt())
				.plusRbh(A_USAGE_VECTOR.getRbh())
				.plusSbh(A_USAGE_VECTOR.getSbh())
				.plusGas(A_USAGE_VECTOR.getGas())
				.plusTv(A_USAGE_VECTOR.getTv())
				.plusNetworkRbh(networkRbh);

		// when:
		var actual = subject.get();

		// then:
		assertSame(A_USAGES_MATRIX, actual);
	}

	@Test
	public void baseEstimateDelegatesAsExpected() {
		givenSubjectWithAll();
		given(utils.baseNetworkRbh()).willReturn(networkRbh);
		given(utils.newBaseEstimate(txn, sigUsage)).willReturn(A_USAGE_VECTOR.toBuilder());
		given(utils.withDefaultPartitioning(A_USAGE_VECTOR, networkRbh, numPayerKeys)).willReturn(A_USAGES_MATRIX);

		// when:
		var actual = subject.get();

		// then:
		assertSame(A_USAGES_MATRIX, actual);
	}

	@Test
	public void requiresAllSet() {
		given(utils.baseNetworkRbh()).willReturn(networkRbh);
		given(utils.newBaseEstimate(txn, sigUsage)).willReturn(A_USAGE_VECTOR.toBuilder());
		given(utils.withDefaultPartitioning(A_USAGE_VECTOR, networkRbh, numPayerKeys)).willReturn(A_USAGES_MATRIX);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.get());

		// and given:
		subject.withTxn(txn);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.get());

		// and given:
		subject.withSigMap(signatureMap);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.get());

		// and given:
		subject.withNumPayerKeys(numPayerKeys);

		// then:
		assertEquals(A_USAGES_MATRIX, subject.get());
	}

	private void givenSubjectWithAll() {
		subject.withNumPayerKeys(numPayerKeys)
				.withSigMap(signatureMap)
				.withTxn(txn);
	}

	class MockEstimator extends TxnUsageEstimator<MockEstimator> {
		public MockEstimator(EstimatorUtils utils) {
			super(utils);
		}

		@Override
		protected MockEstimator self() {
			return this;
		}
	}
}