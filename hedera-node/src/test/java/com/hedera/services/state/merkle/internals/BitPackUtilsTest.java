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

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.services.state.merkle.internals.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.state.merkle.internals.BitPackUtils.nanosFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.merkle.internals.BitPackUtils.secondsFrom;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitPackUtilsTest {
	@Test
	void numFromCodeWorks() {
		// expect:
		assertEquals(MAX_NUM_ALLOWED, BitPackUtils.numFromCode((int) MAX_NUM_ALLOWED));
	}

	@Test
	void codeFromNumWorks() {
		// expect:
		assertEquals((int) MAX_NUM_ALLOWED, BitPackUtils.codeFromNum(MAX_NUM_ALLOWED));
	}

	@Test
	void codeFromNumThrowsWhenOutOfRange() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> BitPackUtils.codeFromNum(-1));
		assertThrows(IllegalArgumentException.class, () -> BitPackUtils.codeFromNum(MAX_NUM_ALLOWED + 1));
	}

	@Test
	void throwsWhenArgOutOfRange() {
		// expect:
		assertDoesNotThrow(() -> BitPackUtils.assertValid(MAX_NUM_ALLOWED));
		assertThrows(IllegalArgumentException.class, () -> BitPackUtils.assertValid(-1));
		assertThrows(IllegalArgumentException.class, () -> BitPackUtils.assertValid(MAX_NUM_ALLOWED + 1));
	}

	@Test
	void isUninstantiable() {
		assertThrows(IllegalStateException.class, BitPackUtils::new);
	}

	@Test
	void timePackingWorks() {
		// given:
		final var distantFuture = Instant.ofEpochSecond(MAX_NUM_ALLOWED, 999_999_999);

		// when:
		final var packed = packedTime(distantFuture.getEpochSecond(), distantFuture.getNano());
		// and:
		final var unpacked = Instant.ofEpochSecond(secondsFrom(packed), nanosFrom(packed));

		// then:
		assertEquals(distantFuture, unpacked);
	}

	@Test
	void cantPackTooFarFuture() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> packedTime(MAX_NUM_ALLOWED + 1, 0));
	}
}
