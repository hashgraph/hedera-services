package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static com.hedera.services.store.contracts.DWUtil.asPackedInts;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DWUtilTest {
    @Test
    void asPackedIntsTest() {
		final UInt256 num1 = UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
		final UInt256 num2 = UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e564");
		final UInt256 num3 = UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e565");

        final var num1_arr = asPackedInts(num1.toArray());
		final var num2_arr = asPackedInts(num2.toArray());
		final var num3_arr = asPackedInts(num3.toArray());

        assertFalse(Arrays.equals(num1_arr, num2_arr));
		assertFalse(Arrays.equals(num3_arr, num2_arr));

		final var num4_arr = asPackedInts(UInt256.valueOf(100L).toArray());
		assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 100 }, num4_arr);

		assertThrows(IllegalArgumentException.class, () -> asPackedInts(null));
		
		final int not32 = 17;
		final var num5_arr = new byte[not32];
		assertThrows(IllegalArgumentException.class, () -> asPackedInts(num5_arr));
    }
}