package com.hedera.services.pricing;

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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

class TokenFeeSchedulesTest extends FeeSchedulesTestHelper {
	@Test
	void computesExpectedPriceForTokenCreateSubyptes() throws IOException {
		testExpectedPriceFor(TokenCreate, TOKEN_FUNGIBLE_COMMON);
		testExpectedPriceFor(TokenCreate, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES);
		testExpectedPriceFor(TokenCreate, TOKEN_NON_FUNGIBLE_UNIQUE);
		testExpectedPriceFor(TokenCreate, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES);
	}

	@Test
	void computesExpectedPriceForUniqueTokenMint() throws IOException {
		testExpectedPriceFor(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE);
	}

	@Test
	void computesExpectedPriceForUniqueTokenWipe() throws IOException {
		testExpectedPriceFor(TokenAccountWipe, TOKEN_NON_FUNGIBLE_UNIQUE);
	}

	@Test
	void computesExpectedPriceForUniqueTokenBurn() throws IOException {
		testExpectedPriceFor(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE);
	}

	@Test
	void computesExpectedPriceForFeeScheduleUpdate() throws IOException {
		testExpectedPriceFor(TokenFeeScheduleUpdate, DEFAULT);
	}
}
