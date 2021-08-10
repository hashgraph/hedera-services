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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermHashIntegerTest {
	@Test
	void overridesJavaLangImpl() {
		// setup:
		final var v = 1_234_567;

		// given:
		final var subject = new PermHashInteger(v);

		// expect:
		assertNotEquals(v, subject.hashCode());
	}

	@Test
	void equalsWorks() {
		// setup:
		final var a = new PermHashInteger(1);
		final var b = new PermHashInteger(2);
		final var c = a;

		// then:
		assertNotEquals(a, b);
		assertNotEquals(a, null);
		assertNotEquals(a, new Object());
		assertEquals(a, c);
	}
}
