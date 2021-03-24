package com.hedera.services.throttling.real;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscreteLeakyBucketTest {
	private long totalCapacity = 64_000L;
	private long capacityUsed = totalCapacity / 4;
	private long capacityFree = totalCapacity - capacityUsed;

	@Test
	public void requiresPositiveCapacity() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(-1L));
		assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(0L));
		assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(0L, -1L));
		assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(0L, 0L));
	}

	@Test
	public void requiresUsageInBounds() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(-1L, totalCapacity));
		assertThrows(IllegalArgumentException.class, () -> new DiscreteLeakyBucket(totalCapacity + 1L, totalCapacity));
	}

	@Test
	void startsEmptyIfNoInitialUsageGiven() {
		// given:
		var subject = new DiscreteLeakyBucket(totalCapacity);

		// expect:
		assertEquals(0L, subject.capacityUsed());
		assertEquals(totalCapacity, subject.totalCapacity());
		assertEquals(totalCapacity, subject.capacityFree());
	}

	@Test
	void beginsWithInitialSpecifiedUsage() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// expect:
		assertEquals(capacityUsed, subject.capacityUsed());
		assertEquals(totalCapacity, subject.totalCapacity());
		assertEquals(capacityFree, subject.capacityFree());
	}

	@Test
	void leaksAsExpected() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// when:
		subject.leak(capacityUsed);

		// then:
		assertEquals(0, subject.capacityUsed());
		assertEquals(totalCapacity, subject.capacityFree());
	}

	@Test
	void leaksToEmptyButNeverMore() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// when:
		subject.leak(Long.MAX_VALUE);

		// then:
		assertEquals(0L, subject.capacityUsed());
	}

	@Test
	void prohibitsNegativeUse() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// when:
		assertThrows(IllegalArgumentException.class, () -> subject.useCapacity(-1));
	}

	@Test
	void prohibitsExcessUsage() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// when:
		assertThrows(IllegalArgumentException.class, () -> subject.useCapacity(1 + totalCapacity - capacityUsed));
	}

	@Test
	void permitsUse() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// when:
		subject.useCapacity(totalCapacity - capacityUsed);

		// then:
		assertEquals(totalCapacity, subject.capacityUsed());
	}

	@Test
	void permitsResettingUsedAmount() {
		// given:
		var subject = new DiscreteLeakyBucket(capacityUsed, totalCapacity);

		// when:
		subject.resetUsed(1L);

		// then:
		assertEquals(1L, subject.capacityUsed());
	}

	@Test
	void rejectsNonsenseUsage() {
		// given:
		var subject = new DiscreteLeakyBucket(totalCapacity);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.resetUsed(-1L));
		assertThrows(IllegalArgumentException.class, () -> subject.resetUsed(totalCapacity + 1L));
	}
}
