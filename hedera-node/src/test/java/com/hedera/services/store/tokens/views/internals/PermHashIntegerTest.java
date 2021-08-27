package com.hedera.services.store.tokens.views.internals;

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

import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermHashIntegerTest {
	@Test
	void overridesJavaLangImpl() {
		final var v = 1_234_567;

		final var subject = new PermHashInteger(v);

		assertNotEquals(v, subject.hashCode());
	}

	@Test
	void equalsWorks() {
		final var a = new PermHashInteger(1);
		final var b = new PermHashInteger(2);
		final var c = a;

		assertNotEquals(a, b);
		assertNotEquals(null, a);
		assertNotEquals(new Object(), a);
		assertEquals(a, c);
	}

	@Test
	void throwsOnUnusableNum() {
		// expect:
		Assertions.assertThrows(IllegalArgumentException.class, () -> PermHashInteger.fromLong(Long.MAX_VALUE));
	}

	@Test
	void factoriesWork() {
		// setup:
		final var expected = PermHashInteger.fromInt(123);

		// expect:
		assertEquals(expected, PermHashInteger.fromLong(123L));
		assertEquals(expected, PermHashInteger.fromAccountId(IdUtils.asAccount("0.0.123")));
		assertEquals(expected, PermHashInteger.fromTokenId(IdUtils.asToken("0.0.123")));
		assertEquals(expected, PermHashInteger.fromScheduleId(IdUtils.asSchedule("0.0.123")));
		assertEquals(expected, PermHashInteger.fromTopicId(IdUtils.asTopic("0.0.123")));
		assertEquals(expected, PermHashInteger.fromContractId(IdUtils.asContract("0.0.123")));
	}

	@Test
	void viewsWork() {
		// given:
		final var subject = PermHashInteger.fromInt(123);

		// expect:
		assertEquals(123, subject.toGrpcAccountId().getAccountNum());
		assertEquals(123, subject.toGrpcTokenId().getTokenNum());
		assertEquals(123, subject.toGrpcScheduleId().getScheduleNum());
		assertTrue(subject.toIdString().endsWith(".123"));
	}

	@Test
	void viewsWorkEvenForNegativeNumCodes() {
		// setup:
		final long realNum = (long)Integer.MAX_VALUE + 10;

		// given:
		final var subject = PermHashInteger.fromLong(realNum);

		// expect:
		assertEquals(realNum, subject.toGrpcAccountId().getAccountNum());
		assertEquals(realNum, subject.toGrpcTokenId().getTokenNum());
		assertEquals(realNum, subject.toGrpcScheduleId().getScheduleNum());
		assertTrue(subject.toIdString().endsWith("." + realNum));
	}

	@Test
	void canGetLongValue() {
		// setup:
		final long realNum = (long)Integer.MAX_VALUE + 10;

		// given:
		final var subject = PermHashInteger.fromLong(realNum);

		// expect:
		assertEquals(realNum, subject.longValue());
	}
}
