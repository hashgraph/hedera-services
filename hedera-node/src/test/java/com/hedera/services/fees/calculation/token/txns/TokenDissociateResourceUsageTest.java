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
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenDissociateUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import com.swirlds.fcmap.FCMap;
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
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class TokenDissociateResourceUsageTest {
	private TokenDissociateResourceUsage subject;

	AccountID target = IdUtils.asAccount("1.2.3");
	MerkleAccount account;
	FCMap<MerkleEntityId, MerkleAccount> accounts;

	private TransactionBody nonTokenDissociateTxn;
	private TransactionBody tokenDissociateTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	TokenDissociateUsage usage;
	BiFunction<TransactionBody, SigUsage, TokenDissociateUsage> factory;

	long expiry = 1_234_567L;
	TokenRef firstRef = TokenRef.newBuilder().setSymbol("TARGET").build();
	TokenRef secondRef = TokenRef.newBuilder().setSymbol("TEGRAT").build();

	@BeforeEach
	private void setup() throws Throwable {
		account = mock(MerkleAccount.class);
		given(account.getExpiry()).willReturn(expiry);
		accounts = mock(FCMap.class);
		given(accounts.get(MerkleEntityId.fromAccountId(target))).willReturn(account);
		view = mock(StateView.class);
		given(view.accounts()).willReturn(accounts);

		tokenDissociateTxn = mock(TransactionBody.class);
		given(tokenDissociateTxn.hasTokenDissociate()).willReturn(true);
		given(tokenDissociateTxn.getTokenDissociate())
				.willReturn(TokenDissociateTransactionBody.newBuilder()
						.setAccount(IdUtils.asAccount("1.2.3"))
						.addTokens(firstRef)
						.addTokens(secondRef)
						.build());

		nonTokenDissociateTxn = mock(TransactionBody.class);
		given(nonTokenDissociateTxn.hasTokenAssociate()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, TokenDissociateUsage>) mock(BiFunction.class);
		given(factory.apply(tokenDissociateTxn, sigUsage)).willReturn(usage);

		usage = mock(TokenDissociateUsage.class);
		given(usage.get()).willReturn(MOCK_TOKEN_DISSOCIATE_USAGE);

		TokenDissociateResourceUsage.factory = factory;
		given(factory.apply(tokenDissociateTxn, sigUsage)).willReturn(usage);

		subject = new TokenDissociateResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenDissociateTxn));
		assertFalse(subject.applicableTo(nonTokenDissociateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				MOCK_TOKEN_DISSOCIATE_USAGE,
				subject.usageGiven(tokenDissociateTxn, obj, view));
	}

	@Test
	public void returnsDefaultIfInfoMissing() throws Exception {
		given(accounts.get(MerkleEntityId.fromAccountId(target))).willReturn(null);

		// expect:
		assertEquals(
				FeeData.getDefaultInstance(),
				subject.usageGiven(tokenDissociateTxn, obj, view));
	}

	public static final FeeData MOCK_TOKEN_DISSOCIATE_USAGE = UsageEstimatorUtils.defaultPartitioning(
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
