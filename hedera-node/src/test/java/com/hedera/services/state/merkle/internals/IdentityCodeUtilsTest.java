package com.hedera.services.state.merkle.internals;

import org.junit.jupiter.api.Test;

import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.MAX_NUM_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityCodeUtilsTest {
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
		assertThrows(IllegalArgumentException.class, () -> IdentityCodeUtils.codeFromNum(MAX_NUM_ALLOWED + 1));
	}

	@Test
	void throwsWhenArgOutOfRange() {
		// expect:
		assertDoesNotThrow(() -> IdentityCodeUtils.assertValid(MAX_NUM_ALLOWED));
		assertThrows(IllegalArgumentException.class, () -> IdentityCodeUtils.assertValid(MAX_NUM_ALLOWED + 1));
	}

	@Test
	void isUninstantiable() {
		assertThrows(IllegalStateException.class, IdentityCodeUtils::new);
	}
}