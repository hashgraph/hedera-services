package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hederahashgraph.api.proto.java.Query;

import static org.junit.Assert.*;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;

import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

@RunWith(JUnitPlatform.class)
public class TokenGetInfoUsageTest {
	Optional<Key> aKey = Optional.of(KeyUtils.A_COMPLEX_KEY);
	String name = "WhyWhyWhyWHY";
	String symbol = "OKITSFINE";
	TokenID id = IdUtils.asToken("0.0.75231");

	TokenGetInfoUsage subject;

	@BeforeEach
	public void setup() {
		subject = TokenGetInfoUsage.newEstimate(tokenQuery());
	}

	@Test
	public void assessesEverything() {
		// given:
		subject.givenCurrentAdminKey(aKey)
				.givenCurrentFreezeKey(aKey)
				.givenCurrentWipeKey(aKey)
				.givenCurrentKycKey(aKey)
				.givenCurrentSupplyKey(aKey)
				.givenCurrentlyUsingAutoRenewAccount()
				.givenCurrentName(name)
				.givenCurrentSymbol(symbol);
		// and:
		var expectedKeyBytes = 5 * FeeBuilder.getAccountKeyStorageSize(aKey.get());
		var expectedBytes = expectedKeyBytes + TOKEN_ENTITY_SIZES.totalBytesInfTokenReprGiven(symbol, name) + BASIC_ENTITY_ID_SIZE;

		// when:
		var usage = subject.get();

		// then:
		var node = usage.getNodedata();
		assertEquals(FeeBuilder.BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE, node.getBpt());
		assertEquals(expectedBytes, node.getBpr());
	}

	private Query tokenQuery() {
		var op = TokenGetInfoQuery.newBuilder()
				.setToken(id)
				.build();
		return Query.newBuilder().setTokenGetInfo(op).build();
	}
}
