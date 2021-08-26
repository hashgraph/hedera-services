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

import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.buildAutomaticAssociationMetaData;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.nanosFrom;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.packedTime;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.secondsFrom;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.setMaxAutomaticAssociationsTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityCodeUtilsTest {
	final int maxAutoAssociations = 123;
	final int alreadyUsedAutomaticAssociations = 12;
	int metadata = buildAutomaticAssociationMetaData(maxAutoAssociations, alreadyUsedAutomaticAssociations);

	@Test
	void numFromCodeWorks() {
		// expect:
		assertEquals(MAX_NUM_ALLOWED, IdentityCodeUtils.numFromCode((int) MAX_NUM_ALLOWED));
	}

	@Test
	void codeFromNumWorks() {
		// expect:
		assertEquals((int) MAX_NUM_ALLOWED, IdentityCodeUtils.codeFromNum(MAX_NUM_ALLOWED));
	}

	@Test
	void codeFromNumThrowsWhenOutOfRange() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> IdentityCodeUtils.codeFromNum(-1));
		assertThrows(IllegalArgumentException.class, () -> IdentityCodeUtils.codeFromNum(MAX_NUM_ALLOWED + 1));
	}

	@Test
	void throwsWhenArgOutOfRange() {
		// expect:
		assertDoesNotThrow(() -> IdentityCodeUtils.assertValid(MAX_NUM_ALLOWED));
		assertThrows(IllegalArgumentException.class, () -> IdentityCodeUtils.assertValid(-1));
		assertThrows(IllegalArgumentException.class, () -> IdentityCodeUtils.assertValid(MAX_NUM_ALLOWED + 1));
	}

	@Test
	void isUninstantiable() {
		assertThrows(UnsupportedOperationException.class, IdentityCodeUtils::new);
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

	@Test
	void automaticAssociationsMetaWorks() {
		assertEquals(maxAutoAssociations, getMaxAutomaticAssociationsFrom(metadata));
		assertEquals(alreadyUsedAutomaticAssociations, getAlreadyUsedAutomaticAssociationsFrom(metadata));
	}

	@Test
	void automaticAssociationSettersWork() {
		int newMax = maxAutoAssociations + alreadyUsedAutomaticAssociations;

		metadata = setMaxAutomaticAssociationsTo(metadata, newMax);

		assertEquals(newMax, getMaxAutomaticAssociationsFrom(metadata));
		assertEquals(alreadyUsedAutomaticAssociations, getAlreadyUsedAutomaticAssociationsFrom(metadata));

		int newAlreadyAutoAssociations = alreadyUsedAutomaticAssociations + alreadyUsedAutomaticAssociations;

		metadata = setAlreadyUsedAutomaticAssociationsTo(metadata, newAlreadyAutoAssociations);

		assertEquals(newMax, getMaxAutomaticAssociationsFrom(metadata));
		assertEquals(newAlreadyAutoAssociations, getAlreadyUsedAutomaticAssociationsFrom(metadata));
	}
}
