package com.hedera.services.store.contracts;

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

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.vm.DataWord;

/**
 * Util class for converting {@link UInt256} variables to {@link DataWord} and vice-versa. Temporary solution while the
 * Legacy Smart Contract State is used
 */
public final class DWUtil {

	private DWUtil() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Converts {@link UInt256} value to {@link DataWord}
	 *
	 * @param uInt256 the value to convert
	 * @return the converted {@link DataWord} value
	 */
	public static DataWord fromUInt256(UInt256 uInt256) {
		return DataWord.of(uInt256.toArray());
	}

	/**
	 * Converts {@link DataWord} value to {@link UInt256}
	 *
	 * @param dataWord the value to convert
	 * @return the converted {@link UInt256} value
	 */
	public static UInt256 fromDataWord(DataWord dataWord) {
		return UInt256.fromBytes(Bytes32.wrap(dataWord.getData()));
	}

	public static int[] asPackedInts(final byte[] data) {
		return new int[]{
				data[0] << 24 | data[1] << 16 | data[2] << 8 | data[3],
				data[4] << 24 | data[5] << 16 | data[6] << 8 | data[7],
				data[8] << 24 | data[9] << 16 | data[10] << 8 | data[11],
				data[12] << 24 | data[13] << 16 | data[14] << 8 | data[15],
				data[16] << 24 | data[17] << 16 | data[18] << 8 | data[19],
				data[20] << 24 | data[21] << 16 | data[22] << 8 | data[23],
				data[24] << 24 | data[25] << 16 | data[26] << 8 | data[27],
				data[28] << 24 | data[29] << 16 | data[30] << 8 | data[31],
		};
	}
}