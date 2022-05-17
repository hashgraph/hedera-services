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

public final class ByteUtils {

	ByteUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static byte[] getBytes(long[] data) {
		if (data == null) return null;
		// ----------
		byte[] byts = new byte[data.length * Long.BYTES];
		for (int i = 0; i < data.length; i++)
			System.arraycopy(getBytes(data[i]), 0, byts, i * Long.BYTES, Long.BYTES);
		return byts;
	}

	private static byte[] getBytes(long data) {
		return new byte[]{
				(byte) ((data >> 56) & 0xff),
				(byte) ((data >> 48) & 0xff),
				(byte) ((data >> 40) & 0xff),
				(byte) ((data >> 32) & 0xff),
				(byte) ((data >> 24) & 0xff),
				(byte) ((data >> 16) & 0xff),
				(byte) ((data >> 8) & 0xff),
				(byte) ((data) & 0xff),
		};
	}
}
