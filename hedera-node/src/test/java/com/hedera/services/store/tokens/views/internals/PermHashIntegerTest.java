package com.hedera.services.store.tokens.views.internals;

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