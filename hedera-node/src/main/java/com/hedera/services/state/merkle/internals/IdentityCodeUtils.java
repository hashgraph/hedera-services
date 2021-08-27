package com.hedera.services.state.merkle.internals;

/*-
 * ‌
 * Hedera Services Node
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

/**
 * Minimal helper class that "encodes" {@code int} values as non-negative
 * {@code long}s in the range 0 to 4,294,967,295.
 */
public final class IdentityCodeUtils {
	private static final long MASK_AS_UNSIGNED_LONG = (1L << 32) - 1;
	private static final long EPOCH_SECONDS_MASK = MASK_AS_UNSIGNED_LONG << 32;

	public static final long MAX_NUM_ALLOWED = -1 & 0xFFFFFFFFL;

	/**
	 * Returns a {@code long} whose high-order 32-bits "encode" an unsigned
	 * integer value, and whose low-order 32-bits are a signed integer. For
	 * use with the {@link IdentityCodeUtils#secondsFrom(long)} and
	 * {@link IdentityCodeUtils#nanosFrom(long)} helpers below. This format
	 * can represent timestamps through January 2106.
	 *
	 * @param seconds some number of seconds since the epoch
	 * @param nanos some number of nanos after the above second
	 * @return a "packed" version of
	 */
	public static long packedTime(long seconds, int nanos) {
		assertValid(seconds);
		return seconds << 32 | (nanos & MASK_AS_UNSIGNED_LONG);
	}

	/**
	 * Returns the high-order 32-bits of the given {@code long}, interpreted
	 * as an unsigned integer.
	 *
	 * @param packedTime any long
	 * @return the high-order 32-bits as an unsigned integer
	 */
	public static long secondsFrom(long packedTime) {
		return (packedTime & EPOCH_SECONDS_MASK) >>> 32;
	}

	/**
	 * Returns the low-order 32-bits of the given {@code long} as a signed integer.
	 *
	 * @param packedTime any long
	 * @return the low-order 32-bits as a signed integer
	 */
	public static int nanosFrom(long packedTime) {
		return (int)(packedTime & MASK_AS_UNSIGNED_LONG);
	}

	/**
	 * Returns the positive long represented by the given integer code.
	 *
	 * @param code an int to interpret as unsigned
	 * @return the corresponding positive long
	 */
	public static long numFromCode(int code) {
		return code & MASK_AS_UNSIGNED_LONG;
	}

	/**
	 * Returns the int representing the given positive long.
	 *
	 * @param num a positive long
	 * @return the corresponding integer code
	 */
	public static int codeFromNum(long num) {
		assertValid(num);
		return (int) num;
	}

	/**
	 * Throws an exception if the given long is not a number in the allowed range.
	 *
	 * @param num the long to check
	 * @throws IllegalArgumentException if the argument is less than 0 or greater than 4,294,967,295
	 */
	public static void assertValid(long num) {
		if (num < 0 || num > MAX_NUM_ALLOWED) {
			throw new IllegalArgumentException("Serial number " + num + " out of range!");
		}
	}

	private IdentityCodeUtils() {
		throw new UnsupportedOperationException("Utility class");
	}
}
