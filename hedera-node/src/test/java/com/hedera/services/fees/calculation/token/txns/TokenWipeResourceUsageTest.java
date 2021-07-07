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
import com.hedera.services.usage.token.TokenWipeUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
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

class TokenWipeResourceUsageTest {
	private TokenWipeResourceUsage subject;

	private TransactionBody nonTokenWipeTxn;
	private TransactionBody tokenWipeTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
	FeeData expected;

	TokenWipeUsage usage;
	BiFunction<TransactionBody, SigUsage, TokenWipeUsage> factory;
	TokenWipeAccountTransactionBody txBody;
	TokenID token;

	@BeforeEach
	private void setup() throws Throwable {
		expected = mock(FeeData.class);
		view = mock(StateView.class);
		token = mock(TokenID.class);

		tokenWipeTxn = mock(TransactionBody.class);
		given(tokenWipeTxn.hasTokenWipe()).willReturn(true);

		nonTokenWipeTxn = mock(TransactionBody.class);
		given(nonTokenWipeTxn.hasTokenWipe()).willReturn(false);

		txBody = mock(TokenWipeAccountTransactionBody.class);
		given(tokenWipeTxn.getTokenWipe()).willReturn(txBody);
		given(txBody.getToken()).willReturn(token);

		factory = (BiFunction<TransactionBody, SigUsage, TokenWipeUsage>)mock(BiFunction.class);
		given(factory.apply(tokenWipeTxn, sigUsage)).willReturn(usage);

		usage = mock(TokenWipeUsage.class);
		given(usage.get()).willReturn(expected);

		TokenWipeResourceUsage.factory = factory;
		given(factory.apply(tokenWipeTxn, sigUsage)).willReturn(usage);

		subject = new TokenWipeResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenWipeTxn));
		assertFalse(subject.applicableTo(nonTokenWipeTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		/// expect:
		given(view.tokenType(token)).willReturn(Optional.of(TokenType.FUNGIBLE_COMMON));
		given(factory.apply(any(), any())).willReturn(usage);
		given(usage.givenSubType(any())).willReturn(usage);

		assertEquals(
				expected,
				subject.usageGiven(tokenWipeTxn, obj, view));
		verify(usage).givenSubType(SubType.TOKEN_FUNGIBLE_COMMON);

		given(view.tokenType(token)).willReturn(Optional.of(TokenType.NON_FUNGIBLE_UNIQUE));
		assertEquals(
				expected,
				subject.usageGiven(tokenWipeTxn, obj, view));
		verify(usage).givenSubType(SubType.TOKEN_NON_FUNGIBLE_UNIQUE);
	}
}
