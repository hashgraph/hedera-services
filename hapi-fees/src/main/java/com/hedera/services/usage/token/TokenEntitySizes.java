package com.hedera.services.usage.token;

public enum TokenEntitySizes {
	TOKEN_ENTITY_SIZES;

	/* { deleted, accountsFrozenByDefault } */
	static int NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION = 2;
	/* { divisibility } */
	static int NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 1;
	/* { expiry, tokenFloat, autoRenewPeriod } */
	static int NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION = 3;

	public int baseBytesUsed(String symbol) {
		return NUM_FLAGS_IN_BASE_TOKEN_REPRESENTATION * 1
				+ NUM_INT_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 4
				+ NUM_LONG_FIELDS_IN_BASE_TOKEN_REPRESENTATION * 8
				+ symbol.length();
	}
}
