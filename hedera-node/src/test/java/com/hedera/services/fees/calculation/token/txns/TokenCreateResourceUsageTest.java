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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenCreateUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TokenCreateResourceUsageTest {
	long now = 1_000_000L;
	TransactionBody nonTokenCreateTxn;
	TransactionBody tokenCreateTxn;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);
	AccountID treasury = IdUtils.asAccount("1.2.3");
	TransactionID txnId = TransactionID.newBuilder()
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now))
			.build();

	BiFunction<TransactionBody, SigUsage, TokenCreateUsage> factory;

	StateView view;
	TokenCreateUsage usage;

	TokenCreateResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		tokenCreateTxn = mock(TransactionBody.class);
		given(tokenCreateTxn.hasTokenCreation()).willReturn(true);
		var tokenCreation = TokenCreateTransactionBody.newBuilder().setTreasury(treasury).build();
		given(tokenCreateTxn.getTokenCreation()).willReturn(tokenCreation);
		given(tokenCreateTxn.getTransactionID()).willReturn(txnId);

		nonTokenCreateTxn = mock(TransactionBody.class);
		given(nonTokenCreateTxn.hasTokenCreation()).willReturn(false);

		usage = mock(TokenCreateUsage.class);
		given(usage.get()).willReturn(MOCK_TOKEN_CREATE_USAGE);

		factory = (BiFunction<TransactionBody, SigUsage, TokenCreateUsage>)mock(BiFunction.class);
		given(factory.apply(tokenCreateTxn, sigUsage)).willReturn(usage);

		TokenCreateResourceUsage.factory = factory;
		subject = new TokenCreateResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenCreateTxn));
		assertFalse(subject.applicableTo(nonTokenCreateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// when:
		var actual = subject.usageGiven(tokenCreateTxn, obj, view);

		// expect:
		assertSame(MOCK_TOKEN_CREATE_USAGE, actual);
	}

	public static final FeeData MOCK_TOKEN_CREATE_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(3)
					.setBpt(3)
					.setVpt(3)
					.setRbh(3)
					.setSbh(3)
					.setGas(3)
					.setTv(3)
					.setBpr(3)
					.setSbpr(3)
					.build(), 3);
}
