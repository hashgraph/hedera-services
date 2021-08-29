package com.hedera.services.pricing;

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

public class TokenFeeSchedulesTest extends FeeSchedulesTestHelper {
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
