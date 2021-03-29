package com.hedera.services.fees.calculation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CongestionMultipliersTest {
	@Test
	void readsExpectedValues() {
		// setup:
		var prop = "90,10x,95,25x,99,100x";

		// when:
		var cm = CongestionMultipliers.from(prop);

		// expect:
		assertArrayEquals(new int[] { 90, 95, 99 }, cm.usagePercentTriggers());
		assertArrayEquals(new long[] { 10L, 25L, 100L }, cm.multipliers());
	}

	@Test
	void roundsDownFloats() {
		// setup:
		var prop = "90,10x,95,25x,99,100x";
		var floatProp = "90.1,10x,95.8,25x,99.5,100x";

		// when:
		var cm = CongestionMultipliers.from(prop);
		var cmFloat = CongestionMultipliers.from(floatProp);

		// expect:
		assertEquals(cm, cmFloat);
	}

	@Test
	void throwsOnNonPositiveMultiplier() {
		// setup:
		var prop = "90,10x,95,25x,99,-100";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnOmittedLastMultiplier() {
		// setup:
		var prop = "90,10x,95,25x,99";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnMissingMultiplier() {
		// setup:
		var prop = "90,x,95,25x,99,100x";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnNonsenseTrigger() {
		// setup:
		var prop = "90,10x,950,25x,99,100x";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnMissingTrigger() {
		// setup:
		var prop = "90,10x,95,25x,,100x";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnMalformedTrigger() {
		// setup:
		var prop = "90,10x,95,25x,99x,100x";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnUnparseableMultiplier() {
		// setup:
		var prop = "90,10y,95,25x,99,100x";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnNonincreasingTriggers() {
		// setup:
		var prop = "90,10,95,25,94,100";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void throwsOnNonincreasingMultipliers() {
		// setup:
		var prop = "90,10,95,25,99,24";

		// expect:
		assertThrows(IllegalArgumentException.class, () -> CongestionMultipliers.from(prop));
	}

	@Test
	void objectContractsMet() {
		var propA = "90,10x,95,25x,99,100x";
		var propB = "90,10x,95,25x,99,1000x";
		var propC = "90,10x,94,25x,99,100x";

		// given:
		var a = CongestionMultipliers.from(propA);

		// expect:
		assertEquals(a, a);
		assertEquals(a, CongestionMultipliers.from(propA));
		assertNotEquals(a, null);
		assertNotEquals(a, new Object());
		assertNotEquals(a, CongestionMultipliers.from(propB));
		assertNotEquals(a, CongestionMultipliers.from(propC));
		// and:
		assertEquals(a.hashCode(), a.hashCode());
		assertEquals(a.hashCode(), CongestionMultipliers.from(propA).hashCode());
		assertNotEquals(a.hashCode(), new Object().hashCode());
		assertNotEquals(a.hashCode(), CongestionMultipliers.from(propB).hashCode());
		assertNotEquals(a.hashCode(), CongestionMultipliers.from(propC).hashCode());
	}

	@Test
	void toStringWorks() {
		// given:
		var propA = "90,10x,95,25x,99,100x";
		var desired = "CongestionMultipliers{10x @ >=90%, 25x @ >=95%, 100x @ >=99%}";

		// expect:
		assertEquals(desired, CongestionMultipliers.from(propA).toString());
	}
}