package com.hedera.services.store.tokens.views.utils;

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

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static com.hedera.services.store.tokens.views.utils.MultiSourceRange.EMPTY_RANGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSourceRangeTest {
	@Test
	void threeRangeExampleAsExpected() {
		// given:
		final var subject = new MultiSourceRange(1, 9, 2);

		// when:
		final var firstRange = subject.rangeForCurrentSource();
		subject.moveToNewSource(3);
		final var secondRange = subject.rangeForCurrentSource();
		subject.moveToNewSource(5);
		final var thirdRange = subject.rangeForCurrentSource();

		// then:
		assertTrue(subject.isRequestedRangeExhausted());
		// and:
		assertEquals(Pair.of(1, 2), firstRange);
		assertEquals(Pair.of(0, 3), secondRange);
		assertEquals(Pair.of(0, 4), thirdRange);
	}

	@Test
	void satisfiesWithFirstRangeIfPossible() {
		// given:
		final var subject = new MultiSourceRange(2, 5, 10);

		// when:
		final var firstRange = subject.rangeForCurrentSource();

		// then:
		assertEquals(Pair.of(2, 5), firstRange);
		// and:
		assertTrue(subject.isRequestedRangeExhausted());
		assertThrows(IllegalStateException.class, () -> subject.moveToNewSource(10));
	}

	@Test
	void canSatisfyUsingSingleElements() {
		// setup:
		final var zeroOneRange = Pair.of(0, 1);

		// given:
		final var subject = new MultiSourceRange(3, 6, 1);

		// then:
		assertSame(EMPTY_RANGE, subject.rangeForCurrentSource());
		subject.moveToNewSource(1);
		assertSame(EMPTY_RANGE, subject.rangeForCurrentSource());
		subject.moveToNewSource(1);
		assertSame(EMPTY_RANGE, subject.rangeForCurrentSource());
		subject.moveToNewSource(1);
		assertEquals(zeroOneRange, subject.rangeForCurrentSource());
		subject.moveToNewSource(1);
		assertEquals(zeroOneRange, subject.rangeForCurrentSource());
		subject.moveToNewSource(1);
		assertEquals(zeroOneRange, subject.rangeForCurrentSource());
		// and:
		assertTrue(subject.isRequestedRangeExhausted());
	}

	@Test
	void recognizesWhenStartIsPastCurrentRange() {
		// given:
		final var subject = new MultiSourceRange(2, 21, 2);

		// when:
		final var firstRange = subject.rangeForCurrentSource();

		// then:
		assertSame(EMPTY_RANGE, firstRange);
		// and:
		assertThrows(IllegalStateException.class, subject::rangeForCurrentSource);
	}

	@Test
	void throwsIseOnRepeatedRangeRequest() {
		// given:
		final var subject = new MultiSourceRange(2, 21, 8);

		// when:
		subject.rangeForCurrentSource();

		// then:
		assertThrows(IllegalStateException.class, subject::rangeForCurrentSource);
	}

	@Test
	void satisfiesWithInitialRangesWhenPossible() {
		// given: 19 requested elements, starting at position 2
		final var subject = new MultiSourceRange(2, 21, 8);

		// when: Three sources are provided
		final var firstRange = subject.rangeForCurrentSource();
		assertFalse(subject.isRequestedRangeExhausted());
		// and:
		subject.moveToNewSource(3);
		final var secondRange = subject.rangeForCurrentSource();
		assertFalse(subject.isRequestedRangeExhausted());
		// and:
		subject.moveToNewSource(30);
		final var thirdRange = subject.rangeForCurrentSource();
		assertTrue(subject.isRequestedRangeExhausted());

		// then: Six elements are used from the first range
		assertEquals(Pair.of(2, 8), firstRange);
		// and: All three elements are used from the second range
		assertEquals(Pair.of(0, 3), secondRange);
		// and: The remaining 10 requested elements are used from the third range
		assertEquals(Pair.of(0, 10), thirdRange);
		// and:
		assertThrows(IllegalStateException.class, subject::rangeForCurrentSource);
		assertThrows(IllegalStateException.class, () -> subject.moveToNewSource(10));
	}
}
