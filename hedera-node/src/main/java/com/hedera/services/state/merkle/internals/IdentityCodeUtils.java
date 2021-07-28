package com.hedera.services.state.merkle.internals;

public class IdentityCodeUtils {
	private static final long MASK_AS_UNSIGNED_LONG = (1L << 32) - 1;

	public static final long MAX_NUM_ALLOWED = -1 & 0xFFFFFFFFL;

	public static long numFromCode(int code) {
		return code & MASK_AS_UNSIGNED_LONG;
	}

	public static int codeFromNum(long num) {
		return (int) num;
	}

	IdentityCodeUtils() {
		throw new IllegalStateException("Utility class");
	}
}
