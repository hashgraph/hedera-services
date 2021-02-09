package com.hedera.services.fees.calculation.token.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenGrantKycUsage;
import com.hedera.services.usage.token.TokenWipeUsage;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class TokenGrantKycResourceUsageTest {
	private TokenGrantKycResourceUsage subject;

	private TransactionBody nonTokenGrantKycTxn;
	private TransactionBody tokenGrantKycTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	TokenGrantKycUsage usage;
	BiFunction<TransactionBody, SigUsage, TokenGrantKycUsage> factory;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		tokenGrantKycTxn = mock(TransactionBody.class);
		given(tokenGrantKycTxn.hasTokenGrantKyc()).willReturn(true);

		nonTokenGrantKycTxn = mock(TransactionBody.class);
		given(nonTokenGrantKycTxn.hasTokenGrantKyc()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, TokenGrantKycUsage>)mock(BiFunction.class);
		given(factory.apply(tokenGrantKycTxn, sigUsage)).willReturn(usage);

		usage = mock(TokenGrantKycUsage.class);
		given(usage.get()).willReturn(MOCK_TOKEN_GRANT_KYC_USAGE);

		TokenGrantKycResourceUsage.factory = factory;
		given(factory.apply(tokenGrantKycTxn, sigUsage)).willReturn(usage);

		subject = new TokenGrantKycResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenGrantKycTxn));
		assertFalse(subject.applicableTo(nonTokenGrantKycTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				MOCK_TOKEN_GRANT_KYC_USAGE,
				subject.usageGiven(tokenGrantKycTxn, obj, view));
	}

	public static final FeeData MOCK_TOKEN_GRANT_KYC_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(1)
					.setBpt(1)
					.setVpt(1)
					.setRbh(1)
					.setSbh(1)
					.setGas(1)
					.setTv(1)
					.setBpr(1)
					.setSbpr(1)
					.build(), 1);

}
