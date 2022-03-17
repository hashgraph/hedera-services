package com.hedera.services.ledger;

import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.properties.TestAccountProperty;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityChangeSetTest {
	private static final TestAccount a = new TestAccount(666L, new Object(), false, 42L);

	private EntityChangeSet<Long, TestAccount, TestAccountProperty> subject = new EntityChangeSet<>();

	@Test
	void canAddChanges() {
		final Map<TestAccountProperty, Object> twoChanges = Map.of(TestAccountProperty.FLAG, false);
		subject.include(1L, null, oneChanges);
		subject.include(2L, a, twoChanges);

		assertEquals(2, subject.size());
		assertChangeAt(0, 1L, null, oneChanges);
		assertChangeAt(1, 2L, a, twoChanges);
	}

	@Test
	void canClearChanges() {
		subject.include(1L, null, oneChanges);

		subject.clear();

		assertTrue(subject.getIds().isEmpty());
		assertTrue(subject.getEntities().isEmpty());
		assertTrue(subject.getChanges().isEmpty());
	}

	private void assertChangeAt(
			final int i,
			final long k,
			final TestAccount a,
			final Map<TestAccountProperty, Object> p
	) {
		assertEquals(k, subject.ids(i));
		assertEquals(a, subject.entity(i));
		assertEquals(p, subject.changes(i));
	}
	private static final Map<TestAccountProperty, Object> oneChanges = Map.of(TestAccountProperty.FLAG, false);
}