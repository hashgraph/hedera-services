package com.hedera.services.usage.token;

import com.hederahashgraph.api.proto.java.TokenTransferList;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public enum TokenEntitySizes {
	TOKEN_ENTITY_SIZES;

	/* { deleted, accountsFrozenByDefault } */
	static int NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION = 2;
	/* { divisibility } */
	static int NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 1;
	/* { expiry, tokenFloat, autoRenewPeriod } */
	static int NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 3;
	/* { treasury } */
	static int NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 1;

	public int baseBytesUsed(String symbol) {
		return NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION * 1
				+ NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 4
				+ NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 8
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_TOKEN_REPRESENTATION * BASIC_ENTITY_ID_SIZE
				+ symbol.length();
	}

	public int bytesUsedToRecordTransfers(int numTokens, int numTransfers) {
		return numTokens * BASIC_ENTITY_ID_SIZE + numTransfers * (BASIC_ENTITY_ID_SIZE + 8);
	}

	public int bytesUsedByTransfers(TokenTransferList transfers) {
		throw new AssertionError("Not implemented");
	}

	public int bytesUsedPerAccountRelationship() {
		return 3 * 8;
	}
}
