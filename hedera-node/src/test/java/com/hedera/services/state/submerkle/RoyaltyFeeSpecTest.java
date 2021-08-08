package com.hedera.services.state.submerkle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.*;

class RoyaltyFeeSpecTest {
	@Test
	void sanityChecksEnforced() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> new RoyaltyFeeSpec(1, 0, null));
		assertThrows(IllegalArgumentException.class,
				() -> new RoyaltyFeeSpec(2, 1, null));
		assertThrows(IllegalArgumentException.class,
				() -> new RoyaltyFeeSpec(-1, 2, null));
	}

	@Test
	void gettersWork() {
		// given:
		final var fallback = new FixedFeeSpec(1, MISSING_ENTITY_ID);
		final var a = new RoyaltyFeeSpec(1, 10, fallback);

		// expect:
		assertEquals(1, a.getNumerator());
		assertEquals(10, a.getDenominator());
		assertSame(fallback, a.getFallbackFee());
	}

	@Test
	void objectContractMet() {
		// given:
		final var fallback = new FixedFeeSpec(1, MISSING_ENTITY_ID);
		final var a = new RoyaltyFeeSpec(1, 10, fallback);
		final var b = new RoyaltyFeeSpec(2, 10, fallback);
		final var c = new RoyaltyFeeSpec(1, 11, fallback);
		final var d = new RoyaltyFeeSpec(1, 10, null);
		final var e = new RoyaltyFeeSpec(1, 10, fallback);
		final var f = a;

		// expect:
		Assertions.assertEquals(a, e);
		Assertions.assertEquals(a, f);
		Assertions.assertNotEquals(a, b);
		Assertions.assertNotEquals(a, c);
		Assertions.assertNotEquals(a, d);
		Assertions.assertNotEquals(a, null);
		Assertions.assertNotEquals(a, new Object());
		// and:
		Assertions.assertEquals(a.hashCode(), e.hashCode());
	}
}