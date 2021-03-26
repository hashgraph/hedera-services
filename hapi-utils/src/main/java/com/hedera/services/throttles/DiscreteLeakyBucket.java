package com.hedera.services.throttles;

/**
 * A bucket of discrete capacity.
 */
class DiscreteLeakyBucket {
	private long used;
	private final long capacity;

	DiscreteLeakyBucket(long capacity) {
		assertValid(capacity);
		used = 0L;
		this.capacity = capacity;
	}

	/* Used only for test setup */
	DiscreteLeakyBucket(long used, long capacity) {
		assertValid(used, capacity);
		this.used = used;
		this.capacity = capacity;
	}

	long capacityFree() {
		return capacity - used;
	}

	long capacityUsed() {
		return used;
	}

	long totalCapacity() {
		return capacity;
	}

	void useCapacity(long units) {
		assertValidUsage(units);
		used += units;
	}

	void leak(long units) {
		used -= Math.min(used, units);
	}

	void resetUsed(long amount) {
		assertValid(amount, capacity);
		this.used = amount;
	}

	private void assertValid(long candidateUsed, long candidateCapacity) {
		assertValid(candidateCapacity);
		if (candidateUsed < 0L || candidateUsed > candidateCapacity) {
			throw new IllegalArgumentException(
					"Cannot use " + candidateUsed + " units in a bucket of capacity" + candidateCapacity + "!");
		}
	}

	private void assertValid(long candidateCapacity) {
		if (candidateCapacity <= 0L) {
			throw new IllegalArgumentException(
					"Cannot construct a bucket with " + candidateCapacity + " units of capacity!");
		}
	}

	private void assertValidUsage(long units) {
		if (units < 0) {
			throw new IllegalArgumentException("Cannot use " + units + " units of capacity!");
		}
		if ((used + units) > capacity) {
			throw new IllegalArgumentException(
					"Adding " + units + " units to " + used + " already used would exceed capacity " + capacity + "!");
		}
	}
}
