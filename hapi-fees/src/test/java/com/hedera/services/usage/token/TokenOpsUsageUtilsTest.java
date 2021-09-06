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

import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.SigUsage;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenOpsUsageUtilsTest {
	@Test
	void tokenCreateUsageFromGrpcWorks() {
		// setup:
		TransactionBody txn = givenAutoRenewBasedOp();

		// given:
		TokenCreateMeta tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		// then:
		assertEquals(1062, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
		assertEquals(TOKEN_FUNGIBLE_COMMON, tokenCreateMeta.getSubType());

	}

	private TransactionBody givenExpiryBasedOp(
			long newExpiry,
			TokenType type,
			boolean withCustomFeesKey,
			boolean withCustomFees
	) {
		var builder = TokenCreateTransactionBody.newBuilder()
				.setTokenType(type)
				.setExpiry(Timestamp.newBuilder().setSeconds(newExpiry))
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
					.setFeeCollectorAccountId(asAccount("0.0.1234"))
					.setFixedFee(FixedFee.newBuilder().setAmount(123)));
		}
		TokenCreateTransactionBody op = builder.build();
		TransactionBody txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenCreation(op)
				.build();
		return txn;
	}

	private TransactionBody givenAutoRenewBasedOp() {
		TokenCreateTransactionBody op = TokenCreateTransactionBody.newBuilder()
				.setAutoRenewAccount(autoRenewAccount)
				.setMemo(memo)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
				.setSymbol(symbol)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey)
				.setInitialSupply(1)
				.build();
		TransactionBody txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenCreation(op)
				.build();
		return txn;
	}


	private Key kycKey = KeyUtils.A_COMPLEX_KEY;
	private Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	private Key freezeKey = KeyUtils.A_KEY_LIST;
	private Key supplyKey = KeyUtils.B_COMPLEX_KEY;
	private Key wipeKey = KeyUtils.C_COMPLEX_KEY;
	private Key customFeeKey = KeyUtils.A_THRESHOLD_KEY;
	private long expiry = 2_345_678L;
	private long autoRenewPeriod = 1_234_567L;
	private String symbol = "DUMMYTOKEN";
	private String name = "DummyToken";
	private String memo = "A simple test token create";
	private int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	private SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	private AccountID autoRenewAccount = asAccount("0.0.75231");

	private final long now = 1_234_567L;
}
