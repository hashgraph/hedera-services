package com.hedera.services.store.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdTest {
	@Test
	void hashCodeDiscriminates() {
		// given:
		final var aId = new Id(1, 2, 3);
		final var bId = new Id(0,2, 3);
		final var cId = new Id(1, 0, 3);
		final var dId = new Id(1, 2, 0);
		final var eId = new Id(1, 2, 3);

		// expect:
		assertNotEquals(bId.hashCode(), aId.hashCode());
		assertNotEquals(cId.hashCode(), aId.hashCode());
		assertNotEquals(dId.hashCode(), aId.hashCode());
		assertEquals(eId.hashCode(), aId.hashCode());
	}

	@Test
	void equalsDiscriminates() {
		// given:
		final var aId = new Id(1, 2, 3);
		final var bId = new Id(0,2, 3);
		final var cId = new Id(1, 0, 3);
		final var dId = new Id(1, 2, 0);
		final var eId = new Id(1, 2, 3);

		// expect:
		assertNotEquals(bId, aId);
		assertNotEquals(cId, aId);
		assertNotEquals(dId, aId);
		assertEquals(eId, aId);
		// and:
		assertNotEquals(aId, null);
		assertNotEquals(aId, new Object());
		assertEquals(aId, aId);
	}
}