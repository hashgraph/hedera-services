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
import com.hedera.services.usage.token.TokenFreezeUsage;
import com.hedera.services.usage.token.TokenMintUsage;
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

class TokenFreezeResourceUsageTest {
	private TokenFreezeResourceUsage subject;

	private TransactionBody nonTokenFreezeTxn;
	private TransactionBody tokenFreezeTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	TokenFreezeUsage usage;

	BiFunction<TransactionBody, SigUsage, TokenFreezeUsage> factory;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		tokenFreezeTxn = mock(TransactionBody.class);
		given(tokenFreezeTxn.hasTokenFreeze()).willReturn(true);

		nonTokenFreezeTxn = mock(TransactionBody.class);
		given(nonTokenFreezeTxn.hasTokenFreeze()).willReturn(false);

		usage = mock(TokenFreezeUsage.class);
		given(usage.get()).willReturn(MOCK_TOKEN_FREEZE_USAGE);

		factory = (BiFunction<TransactionBody, SigUsage, TokenFreezeUsage>)mock(BiFunction.class);
		given(factory.apply(tokenFreezeTxn, sigUsage)).willReturn(usage);

		TokenFreezeResourceUsage.factory = factory;

		subject = new TokenFreezeResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenFreezeTxn));
		assertFalse(subject.applicableTo(nonTokenFreezeTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				MOCK_TOKEN_FREEZE_USAGE,
				subject.usageGiven(tokenFreezeTxn, obj, view));
	}

	public static final FeeData MOCK_TOKEN_FREEZE_USAGE = UsageEstimatorUtils.defaultPartitioning(
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
