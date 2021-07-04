package com.hedera.services.fees.calculation.utils;

import com.hederahashgraph.api.proto.java.TokenType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceUsageSubtypeHelperTest {
	private ResourceUsageSubtypeHelper subject = new ResourceUsageSubtypeHelper();

	@Test
	void emptyOptionalIsDefault() {
		// expect:
		assertEquals(DEFAULT, subject.determineTokenType(Optional.empty()));
	}

	@Test
	void presentValuesAreAsExpected() {
		// expect:
		assertEquals(DEFAULT, subject.determineTokenType(Optional.of(TokenType.UNRECOGNIZED)));
		assertEquals(TOKEN_FUNGIBLE_COMMON, subject.determineTokenType(Optional.of(TokenType.FUNGIBLE_COMMON)));
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.determineTokenType(Optional.of(TokenType.NON_FUNGIBLE_UNIQUE)));
	}
}