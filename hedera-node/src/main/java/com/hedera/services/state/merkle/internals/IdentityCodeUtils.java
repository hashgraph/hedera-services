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
 * Minimal helper class that "encodes" {@code int} values as positive
 * {@code long}s in the range 1 to 4,294,967,295.
 */
public class IdentityCodeUtils {
	private static final long MASK_AS_UNSIGNED_LONG = (1L << 32) - 1;

	public static final long MAX_NUM_ALLOWED = -1 & 0xFFFFFFFFL;

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
	 * Throws an exception if the given long is not a positive number in the allowed range.
	 *
	 * @param num the long to check
	 * @throws IllegalArgumentException if the argument is less than 1 or greater than 4,294,967,295
	 */
	public static void assertValid(long num) {
		if (num < 1 || num > MAX_NUM_ALLOWED) {
			throw new IllegalArgumentException("Serial number " + num + " out of range!");
		}
	}

	IdentityCodeUtils() {
		throw new IllegalStateException("Utility class");
	}
}
