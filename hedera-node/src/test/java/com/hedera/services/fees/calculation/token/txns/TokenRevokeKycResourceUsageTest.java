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
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenRevokeKycUsage;
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

class TokenRevokeKycResourceUsageTest {
	private TokenRevokeKycResourceUsage subject;

	private TransactionBody nonTokenRevokeKycTxn;
	private TransactionBody tokenRevokeKycTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
	FeeData expected;

	TokenRevokeKycUsage usage;
	BiFunction<TransactionBody, SigUsage, TokenRevokeKycUsage> factory;

	@BeforeEach
	private void setup() throws Throwable {
		expected = mock(FeeData.class);
		view = mock(StateView.class);

		tokenRevokeKycTxn = mock(TransactionBody.class);
		given(tokenRevokeKycTxn.hasTokenRevokeKyc()).willReturn(true);

		nonTokenRevokeKycTxn = mock(TransactionBody.class);
		given(nonTokenRevokeKycTxn.hasTokenRevokeKyc()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, TokenRevokeKycUsage>)mock(BiFunction.class);
		given(factory.apply(tokenRevokeKycTxn, sigUsage)).willReturn(usage);

		usage = mock(TokenRevokeKycUsage.class);
		given(usage.get()).willReturn(expected);

		TokenRevokeKycResourceUsage.factory = factory;
		given(factory.apply(tokenRevokeKycTxn, sigUsage)).willReturn(usage);

		subject = new TokenRevokeKycResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenRevokeKycTxn));
		assertFalse(subject.applicableTo(nonTokenRevokeKycTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				expected,
				subject.usageGiven(tokenRevokeKycTxn, obj, view));
	}
}
