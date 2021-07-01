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
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenMintUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.verify;

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
	TokenMintTransactionBody transactionBody;
	TokenID token;

	@BeforeEach
	private void setup() throws Throwable {
		expected = mock(FeeData.class);
		view = mock(StateView.class);
		transactionBody = mock(TokenMintTransactionBody.class);
		token = mock(TokenID.class);

		tokenMintTxn = mock(TransactionBody.class);
		given(tokenMintTxn.hasTokenMint()).willReturn(true);
		given(tokenMintTxn.getTokenMint()).willReturn(transactionBody);
		given(transactionBody.getToken()).willReturn(token);

		nonTokenMintTxn = mock(TransactionBody.class);
		given(nonTokenMintTxn.hasTokenMint()).willReturn(false);

		usage = mock(TokenMintUsage.class);
		given(usage.givenSubType(SubType.DEFAULT)).willReturn(usage);
		given(usage.get()).willReturn(expected);

		factory = (BiFunction<TransactionBody, SigUsage, TokenMintUsage>) mock(BiFunction.class);
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
		given(view.tokenType(token)).willReturn(Optional.of(TokenType.FUNGIBLE_COMMON));
		given(factory.apply(any(), any())).willReturn(usage);
		given(usage.givenSubType(any())).willReturn(usage);

		assertEquals(
				expected,
				subject.usageGiven(tokenMintTxn, obj, view));
		verify(usage).givenSubType(SubType.TOKEN_FUNGIBLE_COMMON);

		given(view.tokenType(token)).willReturn(Optional.of(TokenType.NON_FUNGIBLE_UNIQUE));
		assertEquals(
				expected,
				subject.usageGiven(tokenMintTxn, obj, view));
		verify(usage).givenSubType(SubType.TOKEN_NON_FUNGIBLE_UNIQUE);

	}
}
