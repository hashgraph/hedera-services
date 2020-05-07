package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TestAccountProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.ledger.properties.TestAccountProperty.*;
import com.hedera.services.exceptions.MissingAccountException;
import org.mockito.InOrder;

@RunWith(JUnitPlatform.class)
public class TransactionalLedgerTest {
	Supplier<TestAccount> newAccountFactory;
	BackingAccounts<Long, TestAccount> backingAccounts;
	ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager = new ChangeSummaryManager<>();
	TransactionalLedger<Long, TestAccountProperty, TestAccount> subject;

	Object[] things = { "a", "b", "c", "d" };
	TestAccount account1 = new TestAccount(1L, things[1], false);

	@BeforeEach
	private void setup() {
		backingAccounts = mock(BackingAccounts.class);
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.getCopy(1L)).willReturn(new TestAccount(account1.value, account1.thing, account1.flag));
		given(backingAccounts.contains(1L)).willReturn(true);

		newAccountFactory = () -> new TestAccount();

		subject = new TransactionalLedger<>(TestAccountProperty.class, newAccountFactory, backingAccounts, changeManager);
	}

	@Test
	public void usesGivenComparatorToOrderCommits() {
		// setup:
		int M = 2, N = 100;
		InOrder inOrder = inOrder(backingAccounts);
		Comparator<Long> backward = Comparator.comparingLong(Long::longValue).reversed();
		List<Long> ids = LongStream.range(M, N).boxed().collect(toList());

		// given:
		subject.setKeyComparator(backward);

		// when:
		subject.begin();
		ids.forEach(id -> subject.create(id));
		subject.commit();

		// then:
		LongStream.range(M, N).map(id -> N - 1 - (id - M)).boxed().forEach(id -> {
			inOrder.verify(backingAccounts).replace(argThat(id::equals), any());
		});
	}

	@Test
	public void usesGivenComparatorToOrderDestroys() {
		// setup:
		int M = 2, N = 100;
		InOrder inOrder = inOrder(backingAccounts);
		Comparator<Long> backward = Comparator.comparingLong(Long::longValue).reversed();
		List<Long> ids = LongStream.range(M, N).boxed().collect(toList());

		// given:
		subject.setKeyComparator(backward);

		// when:
		subject.begin();
		ids.forEach(id -> subject.create(id));
		subject.commit();
		// and:
		subject.begin();
		ids.forEach(id -> subject.destroy(id));
		subject.commit();

		// then:
		LongStream.range(M, N).map(id -> N - 1 - (id - M)).boxed().forEach(id -> {
			inOrder.verify(backingAccounts).replace(argThat(id::equals), any());
		});
		// and:
		LongStream.range(M, N).map(id -> N - 1 - (id - M)).boxed().forEach(id -> {
			inOrder.verify(backingAccounts).remove(id);
		});
	}

	@Test
	public void requiresManualRollbackIfCommitFails() {
		given(backingAccounts.getCopy(any())).willThrow(IllegalStateException.class);

		// when:
		subject.begin();
		subject.set(1L, OBJ, things[0]);

		// then:
		assertThrows(IllegalStateException.class, () -> subject.commit());
		assertTrue(subject.isInTransaction());
	}

	@Test
	public void recognizesPendingCreates() {
		// when:
		subject.begin();
		subject.create(2L);

		// then:
		assertTrue(subject.existsPending(2L));
		assertFalse(subject.existsPending(1L));
	}

	@Test
	public void reportsDeadAccountsIndividually() {
		// when:
		subject.begin();
		subject.destroy(1L);

		// then:
		assertEquals("{*DEAD* 1}", subject.changeSetSoFar());
	}

	@Test
	public void existsIfNotMissingAndNotDestroyed() {
		// given:
		subject.begin();
		subject.create(2L);
		subject.create(3L);
		subject.destroy(3L);

		// when:
		boolean has1 = subject.exists(1L);
		boolean has2 = subject.exists(2L);
		boolean has3 = subject.exists(3L);

		// then:
		assertTrue(has1);
		assertTrue(has2);
		assertFalse(has3);
	}

	@Test
	public void delegatesDestroyToRemove() {
		// given:
		subject.begin();

		// when:
		subject.destroy(1L);
		// and:
		subject.commit();

		// then:
		verify(backingAccounts).remove(1L);
	}

	@Test
	public void throwsIfNotInTransaction() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.set(1L, OBJ, things[0]));
		assertThrows(IllegalStateException.class, () -> subject.create(2L));
		assertThrows(IllegalStateException.class, () -> subject.destroy(1L));
	}

	@Test
	public void throwsOnMutationToMissingAccount() {
		// given:
		subject.begin();

		// expect:
		assertThrows(MissingAccountException.class, () -> subject.set(0L, OBJ, things[0]));
	}

	@Test
	public void throwsOnGettingMissingAccount() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.get(2L));
	}

	@Test
	public void throwsOnCreationWithExistingAccountId() {
		// given:
		subject.begin();

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.create(1L));
	}

	@Test
	public void throwsOnGettingPropOfMissingAccount() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.get(2L, OBJ));
	}

	@Test
	public void usesGetRefWhenAccessingPropertiesFromExistingAccount() {
		// when:
		Object thing = subject.get(1L, OBJ);

		// then:
		assertEquals(things[1], thing);
		verify(backingAccounts, never()).getCopy(1L);
		verify(backingAccounts).getRef(1L);
	}

	@Test
	public void usesGetRefWhenAccessingUnchangedPropertiesFromExistingAccounts() {
		// given:
		subject.begin();
		subject.set(1L, FLAG, false);

		// when:
		Object thing = subject.get(1L, OBJ);

		// then:
		assertEquals(things[1], thing);
		verify(backingAccounts, never()).getCopy(1L);
		verify(backingAccounts).getRef(1L);
	}

	@Test
	public void returnsPendingChangePropertiesOfExistingAccounts() {
		// given:
		subject.begin();
		subject.set(1L, LONG, 3L);

		// when:
		long value = (long)subject.get(1L, LONG);

		// then:
		verify(backingAccounts, times(2)).contains(1L);
		verifyNoMoreInteractions(backingAccounts);
		assertEquals(3L, value);
	}

	@Test
	public void incorporatesMutationToPendingNewAccount() {
		// given:
		subject.begin();
		subject.create(2L);

		// when:
		subject.set(2L, OBJ, things[2]);

		// then:
		assertEquals(new TestAccount(0L, things[2], false), subject.get(2L));
	}

	@Test
	public void throwsOnGettingSavedPropertyOfZombie() {
		// given:
		subject.begin();
		subject.destroy(1L);

		// expect:
		assertThrows(MissingAccountException.class, () -> subject.getSaved(1L, OBJ));
	}

	@Test
	public void throwsOnGettingSavedPropertyOfPendingAccount() {
		// given:
		subject.begin();
		subject.create(2L);

		// expect:
		assertThrows(MissingAccountException.class, () -> subject.getSaved(2L, OBJ));
	}

	@Test
	public void returnsSetPropertiesOfPendingNewAccounts() {
		// given:
		subject.begin();
		subject.create(2L);
		subject.set(2L, OBJ, things[2]);

		// when:
		Object thing = subject.get(2L, OBJ);

		// then:
		assertEquals(things[2], thing);
	}

	@Test
	public void returnsDefaultForUnsetPropertiesOfPendingNewAccounts() {
		// given:
		subject.begin();
		subject.create(2L);
		subject.set(2L, OBJ, things[2]);

		// when:
		boolean flag = (boolean)subject.get(2L, FLAG);

		// then:
		assertFalse(flag);
	}

	@Test
	public void reflectsChangeToExistingAccountIfInTransaction() {
		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, things[0]);

		// expect:
		assertEquals(new TestAccount(account1.value, things[0], account1.flag), subject.get(1L));
	}

	@Test
	public void ignoresChangeToExistingAccountInGetSaved() {
		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, things[0]);

		// expect:
		assertEquals(things[1], subject.getSaved(1L, OBJ));
	}

	@Test
	public void throwsIfTxnAlreadyBegun() {
		// given:
		subject.begin();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.begin());
	}

	@Test
	public void throwsOnRollbackWithoutActiveTxn() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.rollback());
	}

	@Test
	public void throwsOnCommitWithoutAciveTxn() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.commit());
	}

	@Test
	public void dropsPendingChangesAfterRollback() {
		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, things[0]);
		subject.create(2L);
		subject.set(2L, OBJ, things[2]);
		// and:
		subject.rollback();

		// expect:
		assertFalse(subject.isInTransaction());
		assertEquals(account1, subject.get(1L));
		assertThrows(IllegalArgumentException.class, () -> subject.get(2L));
	}

	@Test
	public void persistsPendingChangesAndDestroysDeadAccountsAfterCommit() {
		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, things[0]);
		subject.create(2L);
		subject.set(2L, OBJ, things[2]);
		subject.set(2L, LONG, 2L);
		subject.create(3L);
		subject.set(3L, OBJ, things[3]);
		subject.destroy(3L);
		// and:
		subject.commit();

		// expect:
		assertFalse(subject.isInTransaction());
		assertEquals("{}", subject.changeSetSoFar());
		// and:
		verify(backingAccounts).replace(1L, new TestAccount(account1.value, things[0], account1.flag));
		verify(backingAccounts).replace(2L, new TestAccount(2L, things[2], false));
		verify(backingAccounts, never()).replace(3L, new TestAccount(0L, things[3], false));
		verify(backingAccounts).remove(3L);
	}

	@Test
	public void describesChangesAsExpected() {
		// given:
		subject.begin();
		subject.set(1L, OBJ, things[0]);
		subject.create(2L);
		subject.set(2L, OBJ, things[2]);
		subject.set(2L, LONG, 2L);
		// and:
		String expectedDescription = "{1: [OBJ -> a], *NEW* 2: [LONG -> 2, OBJ -> c]}";

		// expect:
		assertEquals(expectedDescription, subject.changeSetSoFar());
	}

	@Test
	public void reflectsUnchangedAccountIfNoChanges() {
		// expect:
		assertEquals(account1, subject.get(1L));
	}
}
