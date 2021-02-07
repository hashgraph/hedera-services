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
import com.hedera.services.usage.token.TokenMintUsage;
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

class TokenMintResourceUsageTest {
	private TransactionBody nonTokenMintTxn;
	private TransactionBody tokenMintTxn;

	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	BiFunction<TransactionBody, SigUsage, TokenMintUsage> factory;
	FeeData expected;

	StateView view;
	TokenMintUsage usage;
	TokenMintResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		expected = mock(FeeData.class);
		view = mock(StateView.class);

		tokenMintTxn = mock(TransactionBody.class);
		given(tokenMintTxn.hasTokenMint()).willReturn(true);

		nonTokenMintTxn = mock(TransactionBody.class);
		given(nonTokenMintTxn.hasTokenMint()).willReturn(false);

		usage = mock(TokenMintUsage.class);
		given(usage.get()).willReturn(expected);

		factory = (BiFunction<TransactionBody, SigUsage, TokenMintUsage>)mock(BiFunction.class);
		given(factory.apply(tokenMintTxn, sigUsage)).willReturn(usage);

		TokenMintResourceUsage.factory = factory;

		subject = new TokenMintResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenMintTxn));
		assertFalse(subject.applicableTo(nonTokenMintTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				expected,
				subject.usageGiven(tokenMintTxn, obj, view));
	}
}
