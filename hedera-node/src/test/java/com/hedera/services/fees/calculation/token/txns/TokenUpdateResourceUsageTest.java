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
import com.hedera.services.usage.token.TokenUpdateUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class TokenUpdateResourceUsageTest {
	private TokenUpdateResourceUsage subject;

	private TransactionBody nonTokenUpdateTxn;
	private TransactionBody tokenUpdateTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	TokenUpdateUsage usage;
	BiFunction<TransactionBody, SigUsage, TokenUpdateUsage> factory;

	long expiry = 1_234_567L;
	String symbol = "HEYMAOK";
	String name = "IsItReallyOk";
	TokenID target = IdUtils.asToken("0.0.123");
	TokenInfo info = TokenInfo.newBuilder()
			.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())
			.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey())
			.setWipeKey(TxnHandlingScenario.TOKEN_WIPE_KT.asKey())
			.setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey())
			.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey())
			.setSymbol(symbol)
			.setName(name)
			.setExpiry(Timestamp.newBuilder().setSeconds(expiry))
			.build();

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		tokenUpdateTxn = mock(TransactionBody.class);
		given(tokenUpdateTxn.hasTokenUpdate()).willReturn(true);
		given(tokenUpdateTxn.getTokenUpdate())
				.willReturn(TokenUpdateTransactionBody.newBuilder()
						.setToken(target)
						.build());

		nonTokenUpdateTxn = mock(TransactionBody.class);
		given(nonTokenUpdateTxn.hasTokenUpdate()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, TokenUpdateUsage>) mock(BiFunction.class);
		given(factory.apply(tokenUpdateTxn, sigUsage)).willReturn(usage);

		usage = mock(TokenUpdateUsage.class);
		given(usage.givenCurrentAdminKey(Optional.of(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey()))).willReturn(usage);
		given(usage.givenCurrentWipeKey(Optional.of(TxnHandlingScenario.TOKEN_WIPE_KT.asKey()))).willReturn(usage);
		given(usage.givenCurrentKycKey(Optional.of(TxnHandlingScenario.TOKEN_KYC_KT.asKey()))).willReturn(usage);
		given(usage.givenCurrentSupplyKey(Optional.of(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey()))).willReturn(usage);
		given(usage.givenCurrentFreezeKey(Optional.of(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey()))).willReturn(usage);
		given(usage.givenCurrentSymbol(symbol)).willReturn(usage);
		given(usage.givenCurrentName(name)).willReturn(usage);
		given(usage.givenCurrentExpiry(expiry)).willReturn(usage);
		given(usage.givenCurrentlyUsingAutoRenewAccount()).willReturn(usage);
		given(usage.get()).willReturn(MOCK_TOKEN_UPDATE_USAGE);

		given(view.infoForToken(target)).willReturn(Optional.of(info));

		TokenUpdateResourceUsage.factory = factory;
		given(factory.apply(tokenUpdateTxn, sigUsage)).willReturn(usage);

		subject = new TokenUpdateResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(tokenUpdateTxn));
		assertFalse(subject.applicableTo(nonTokenUpdateTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				MOCK_TOKEN_UPDATE_USAGE,
				subject.usageGiven(tokenUpdateTxn, obj, view));
	}

	@Test
	public void returnsDefaultIfInfoMissing() throws Exception {
		given(view.infoForToken(any())).willReturn(Optional.empty());

		// expect:
		assertEquals(
				FeeData.getDefaultInstance(),
				subject.usageGiven(tokenUpdateTxn, obj, view));
	}

	public static final FeeData MOCK_TOKEN_UPDATE_USAGE = UsageEstimatorUtils.defaultPartitioning(
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
