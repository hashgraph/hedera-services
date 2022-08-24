package com.hedera.services.fees.calculation.utils;

public class OverflowCheckingCalc {
	public OverflowCheckingCalc(){
		/* no-op */
	}

	public static long clampedAdd(final long a, final long b) {
		try {
			return Math.addExact(a, b);
		} catch (final ArithmeticException ae) {
			return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}
	public static long clampedMultiply(final long a, final long b) {
		try {
			return Math.multiplyExact(a, b);
		} catch (final ArithmeticException ae) {
			return ((a ^ b) < 0) ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
	}

}
