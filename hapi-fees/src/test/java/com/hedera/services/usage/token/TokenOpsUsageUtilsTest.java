package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenOpsUsageUtilsTest {
	@Test
	void tokenCreateWithAutoRenewAccountWorks() {
		final var txn = givenTokenCreateWith(
				FUNGIBLE_COMMON, false, false, true);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1062, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_FUNGIBLE_COMMON, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithCustomFeesAndKeyWork() {
		final var txn = givenTokenCreateWith(
				FUNGIBLE_COMMON, true, true, false);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1138, tokenCreateMeta.getBaseSize());
		assertEquals(1_111_111L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(32, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithAutoRenewAcctNoCustomFeeKeyNoCustomFeesWorks() {
		final var txn = givenTokenCreateWith(NON_FUNGIBLE_UNIQUE,
				false, false, true);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1062, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithOutAutoRenewAcctAndCustomFeeKeyNoCustomFeesWorks() {
		final var txn = givenTokenCreateWith(NON_FUNGIBLE_UNIQUE,
				true, false, true);

		TokenCreateMeta tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1162, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithAutoRenewAcctAndCustomFeesAndKeyWorks() {
		final var txn = givenTokenCreateWith(NON_FUNGIBLE_UNIQUE,
				true, true, true);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1162, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(32, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	private TransactionBody givenTokenCreateWith(
			final TokenType type,
			final boolean withCustomFeesKey,
			final boolean withCustomFees,
			final boolean withAutoRenewAccount
	) {
		final var builder = TokenCreateTransactionBody.newBuilder()
				.setTokenType(type)
				.setExpiry(Timestamp.newBuilder().setSeconds(expiry))
				.setSymbol(symbol)
				.setMemo(memo)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey);
		if (withCustomFeesKey) {
			builder.setFeeScheduleKey(customFeeKey);
		}
		if (withCustomFees) {
			builder.addCustomFees(CustomFee.newBuilder()
					.setFeeCollectorAccountId(IdUtils.asAccount("0.0.1234"))
					.setFixedFee(FixedFee.newBuilder().setAmount(123)));
		}
		if (withAutoRenewAccount) {
			builder.setAutoRenewAccount(autoRenewAccount)
					.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod));
		}
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenCreation(builder)
				.build();
		return txn;
	}

	private static final Key kycKey = KeyUtils.A_COMPLEX_KEY;
	private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	private static final Key freezeKey = KeyUtils.A_KEY_LIST;
	private static final Key supplyKey = KeyUtils.B_COMPLEX_KEY;
	private static final Key wipeKey = KeyUtils.C_COMPLEX_KEY;
	private static final Key customFeeKey = KeyUtils.A_THRESHOLD_KEY;
	private static final long expiry = 2_345_678L;
	private static final long autoRenewPeriod = 1_234_567L;
	private static final String symbol = "DUMMYTOKEN";
	private static final String name = "DummyToken";
	private static final String memo = "A simple test token create";
	private static final AccountID autoRenewAccount = asAccount("0.0.75231");

	private static final long now = 1_234_567L;
}
