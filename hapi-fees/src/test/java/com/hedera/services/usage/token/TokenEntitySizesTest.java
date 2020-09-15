package com.hedera.services.usage.token;

import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.usage.token.TokenEntitySizes.*;

@RunWith(JUnitPlatform.class)
public class TokenEntitySizesTest {
	TokenEntitySizes subject = TokenEntitySizes.TOKEN_ENTITY_SIZES;

	@Test
	public void sizesAsExpected() {
		// setup:
		var symbol = "ABCDEFGH";
		long expected = NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION * 1
				+ NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 4
				+ NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 8
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION * BASIC_ENTITY_ID_SIZE
				+ symbol.getBytes().length;

		// given:
		long actual = subject.baseBytesUsed(symbol);

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	public void understandsRecordTransfersSize() {
		// setup:
		int numTokens = 3, numTransfers = 8;

		// given:
		var expected = 3 * BASIC_ENTITY_ID_SIZE + 8 * (8 + BASIC_ENTITY_ID_SIZE);

		// then:
		assertEquals(expected, subject.bytesUsedToRecordTransfers(numTokens, numTransfers));
	}

	@Test
	public void returnsRequiredBytesForRel() {
		// expect:
		assertEquals(3 * 8, subject.bytesUsedPerAccountRelationship());
	}
}