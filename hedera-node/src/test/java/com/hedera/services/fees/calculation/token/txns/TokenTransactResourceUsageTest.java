package com.hedera.services.fees.calculation.token.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenRevokeKycUsage;
import com.hedera.services.usage.token.TokenTransactUsage;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class TokenTransactResourceUsageTest {
	private TokenTransactResourceUsage subject;

	private TransactionBody nonTokenTransactTxn;
	private TransactionBody tokenTransactTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	TokenTransactUsage usage;
	BiFunction<TransactionBody, SigUsage, TokenTransactUsage> factory;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		tokenTransactTxn = mock(TransactionBody.class);
		given(tokenTransactTxn.hasTokenTransfers()).willReturn(true);

		nonTokenTransactTxn = mock(TransactionBody.class);
		given(nonTokenTransactTxn.hasTokenTransfers()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, TokenTransactUsage>)mock(BiFunction.class);
		given(factory.apply(tokenTransactTxn, sigUsage)).willReturn(usage);

		usage = mock(TokenTransactUsage.class);
		given(usage.get()).willReturn(MOCK_TOKEN_TRANSACT_USAGE);

		TokenTransactResourceUsage.factory = factory;
		given(factory.apply(tokenTransactTxn, sigUsage)).willReturn(usage);

		subject = new TokenTransactResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenTransactTxn));
		assertFalse(subject.applicableTo(nonTokenTransactTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				MOCK_TOKEN_TRANSACT_USAGE,
				subject.usageGiven(tokenTransactTxn, obj, view));
	}

	public static final FeeData MOCK_TOKEN_TRANSACT_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(2)
					.setBpt(2)
					.setVpt(2)
					.setRbh(2)
					.setSbh(2)
					.setGas(2)
					.setTv(2)
					.setBpr(2)
					.setSbpr(2)
					.build(), 2);
}