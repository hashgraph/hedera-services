package com.hedera.services.ledger;

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

import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TestAccountProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static com.hedera.services.ledger.properties.TestAccountProperty.FLAG;
import static com.hedera.services.ledger.properties.TestAccountProperty.LONG;
import static com.hedera.services.ledger.properties.TestAccountProperty.OBJ;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoMoreInteractions;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalLedgerTest {
	private final Object[] things = { "a", "b", "c", "d" };
	private final TestAccount account1 = new TestAccount(1L, things[1], false, 667L);
	private final ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager = new ChangeSummaryManager<>();

	@Mock
	private BackingStore<Long, TestAccount> backingAccounts;
	@Mock
	private PropertyChangeObserver<Long, TestAccountProperty> commitObserver;

	private LedgerCheck<TestAccount, TestAccountProperty> scopedCheck;
	private TransactionalLedger<Long, TestAccountProperty, TestAccount> subject;

	@BeforeEach
	private void setup() {
		scopedCheck = new TestAccountScopedCheck();

		subject = new TransactionalLedger<>(
				TestAccountProperty.class, TestAccount::new, backingAccounts, changeManager);
	}

	@Test
	void commitObserverWorks() {
		subject.setCommitInterceptor(commitObserver);

		subject.begin();
		subject.create(1L);
		subject.set(1L, OBJ, things);
		subject.set(1L, FLAG, true);
		subject.commit();

		verify(commitObserver).newProperty(1L, OBJ, things);
		verify(commitObserver).newProperty(1L, FLAG, true);
		verifyNoMoreInteractions(commitObserver);
	}

	@Test
	void canUndoCreations() {
		subject.begin();

		subject.create(2L);

		subject.undoCreations();

		subject.commit();

		verify(backingAccounts, never()).put(any(), any());
	}

	@Test
	void canUndoCreationsOnlyInTxn() {
		assertThrows(IllegalStateException.class, subject::undoCreations);
	}

	@Test
	void rollbackClearsChanges() {
		given(backingAccounts.contains(1L)).willReturn(true);

		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, new Object());
		// and:
		subject.rollback();

		// then:
		assertTrue(subject.getChanges().isEmpty());
	}

	@Test
	void getUsesMutableRefIfPendingChanges() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);

		// given:
		var newAccount1 = new TestAccount(account1.value, account1.thing, !account1.flag, account1.tokenThing);
		// and:
		subject.begin();
		subject.set(1L, FLAG, !account1.flag);

		// when:
		var account = subject.getFinalized(1L);

		// then:
		assertEquals(newAccount1, account);
		// and:
		verify(backingAccounts).getRef(1L);
	}

	@Test
	void zombieIsResurrectedIfPutAgain() {
		subject.begin();

		subject.create(1L);
		subject.destroy(1L);
		subject.put(1L, account1);

		subject.commit();
		verify(backingAccounts).put(1L, account1);
	}

	@Test
	void putsInOrderOfChanges() {
		// setup:
		int M = 2, N = 100;
		InOrder inOrder = inOrder(backingAccounts);
		List<Long> ids = LongStream.range(M, N).boxed().collect(toList());

		// when:
		subject.begin();
		ids.forEach(id -> subject.create(id));
		subject.commit();

		// then:
		LongStream.range(M, N).boxed().forEach(id -> {
			inOrder.verify(backingAccounts).put(argThat(id::equals), any());
		});
	}

	@Test
	void destroysInOrder() {
		// setup:
		int M = 2, N = 100;
		InOrder inOrder = inOrder(backingAccounts);
		List<Long> ids = LongStream.range(M, N).boxed().collect(toList());

		// when:
		subject.begin();
		ids.forEach(id -> subject.create(id));
		subject.commit();
		// and:
		subject.begin();
		ids.forEach(id -> subject.destroy(id));
		subject.commit();

		// then:
		LongStream.range(M, N).boxed().forEach(id -> {
			inOrder.verify(backingAccounts).put(argThat(id::equals), any());
		});
		// and:
		LongStream.range(M, N).boxed().forEach(id -> {
			inOrder.verify(backingAccounts).remove(id);
		});
	}

	@Test
	void requiresManualRollbackIfCommitFails() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);

		willThrow(IllegalStateException.class).given(backingAccounts).put(any(), any());

		// when:
		subject.begin();
		subject.set(1L, OBJ, things[0]);
		subject.create(2L);

		// then:
		assertThrows(IllegalStateException.class, () -> subject.commit());
		assertTrue(subject.isInTransaction());
	}

	@Test
	void recognizesPendingCreates() {
		// when:
		subject.begin();
		subject.create(2L);

		// then:
		assertTrue(subject.existsPending(2L));
		assertFalse(subject.existsPending(1L));
	}

	@Test
	void reportsDeadAccountsIndividually() {
		// when:
		subject.begin();
		subject.destroy(1L);

		// then:
		assertEquals("{*DEAD* 1}", subject.changeSetSoFar());
	}

	@Test
	void existsIfNotMissingAndNotDestroyed() {
		given(backingAccounts.contains(1L)).willReturn(true);
		given(backingAccounts.contains(2L)).willReturn(false);
		given(backingAccounts.contains(3L)).willReturn(false);

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
	void delegatesDestroyToRemove() {
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
	void throwsIfNotInTransaction() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.set(1L, OBJ, things[0]));
		assertThrows(IllegalStateException.class, () -> subject.create(2L));
		assertThrows(IllegalStateException.class, () -> subject.destroy(1L));
	}

	@Test
	void throwsOnMutationToMissingAccount() {
		// given:
		subject.begin();

		// expect:
		assertThrows(MissingAccountException.class, () -> subject.set(0L, OBJ, things[0]));
	}

	@Test
	void throwsOnGettingMissingAccount() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.getFinalized(2L));
	}

	@Test
	void throwsOnCreationWithExistingAccountId() {
		given(backingAccounts.contains(1L)).willReturn(true);

		// given:
		subject.begin();

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.create(1L));
	}

	@Test
	void throwsOnGettingPropOfMissingAccount() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.get(2L, OBJ));
	}

	@Test
	void returnsPendingChangePropertiesOfExistingAccounts() {
		given(backingAccounts.contains(1L)).willReturn(true);

		// given:
		subject.begin();
		subject.set(1L, LONG, 3L);

		// when:
		long value = (long) subject.get(1L, LONG);

		// then:
		verify(backingAccounts, times(2)).contains(1L);
		verifyNoMoreInteractions(backingAccounts);
		assertEquals(3L, value);
	}

	@Test
	void incorporatesMutationToPendingNewAccount() {
		// given:
		subject.begin();
		subject.create(2L);

		// when:
		subject.set(2L, OBJ, things[2]);

		// then:
		assertEquals(new TestAccount(0L, things[2], false), subject.getFinalized(2L));
	}

	@Test
	void returnsSetPropertiesOfPendingNewAccounts() {
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
	void returnsDefaultForUnsetPropertiesOfPendingNewAccounts() {
		// given:
		subject.begin();
		subject.create(2L);
		subject.set(2L, OBJ, things[2]);

		// when:
		boolean flag = (boolean) subject.get(2L, FLAG);

		// then:
		assertFalse(flag);
	}

	@Test
	void reflectsChangeToExistingAccountIfInTransaction() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);

		final var expected = new TestAccount(account1.value, things[0], account1.flag, 667L);

		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, things[0]);

		// expect:
		assertEquals(expected, subject.getFinalized(1L));
	}

	@Test
	void canUndoSpecificChange() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);
		// setup:
		ArgumentCaptor<TestAccount> captor = ArgumentCaptor.forClass(TestAccount.class);
		final var changesToUndo = List.of(FLAG);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.undoChangesOfType(changesToUndo));
		// given:
		subject.begin();

		// when:
		subject.set(1L, OBJ, things[0]);
		subject.set(1L, FLAG, true);
		// and:
		subject.undoChangesOfType(List.of(FLAG));
		// and:
		subject.commit();

		// expect:
		verify(backingAccounts).put(longThat(l -> l == 1L), captor.capture());
		// and:
		final var committed = captor.getValue();
		assertSame(things[0], committed.getThing());
		assertFalse(committed.isFlag());
	}

	@Test
	void throwsIfTxnAlreadyBegun() {
		// given:
		subject.begin();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.begin());
	}

	@Test
	void throwsOnRollbackWithoutActiveTxn() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.rollback());
	}

	@Test
	void throwsOnCommitWithoutActiveTxn() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.commit());
	}

	@Test
	void dropsPendingChangesAfterRollback() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);

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
		assertEquals(account1, subject.getFinalized(1L));
		assertThrows(IllegalArgumentException.class, () -> subject.getFinalized(2L));
	}

	@Test
	void persistsPendingChangesAndDestroysDeadAccountsAfterCommit() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);

		// setup:
		var expected2 = new TestAccount(2L, things[2], false);

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
		verify(backingAccounts).put(2L, expected2);
		verify(backingAccounts).put(1L, new TestAccount(1L, things[0], false, 667L));
		verify(backingAccounts, never()).put(3L, new TestAccount(0L, things[3], false));
		verify(backingAccounts).remove(3L);
	}

	@Test
	void reflectsUnchangedAccountIfNoChanges() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);
		assertEquals(account1, subject.getFinalized(1L));
	}

	@Test
	void validateHappyPath() {
		given(backingAccounts.getRef(1L)).willReturn(account1);
		given(backingAccounts.getImmutableRef(1L)).willReturn(account1);
		given(backingAccounts.contains(1L)).willReturn(true);

		subject.begin();
		subject.set(1L, LONG, 123L);
		subject.set(1L, FLAG, false);
		subject.set(1L, OBJ, "DEFAULT");
		subject.commit();

		assertEquals(OK, subject.validate(1L, scopedCheck));
	}

	@Test
	void validationFailsForMissingAccount() {
		assertEquals(INVALID_ACCOUNT_ID, subject.validate(2L, scopedCheck));
	}

	@Test
	void validationFailsAsExpected() {
		TestAccount account2 = new TestAccount(321L, things[1], false, 667L);
		TestAccount account3 = new TestAccount(123L, things[1], true, 667L);
		TestAccount account4 = new TestAccount(123L, "RANDOM", false, 667L);

		when(backingAccounts.contains(2L)).thenReturn(true);
		when(backingAccounts.getImmutableRef(2L)).thenReturn(account2);
		when(backingAccounts.contains(3L)).thenReturn(true);
		when(backingAccounts.getImmutableRef(3L)).thenReturn(account3);
		when(backingAccounts.contains(4L)).thenReturn(true);
		when(backingAccounts.getImmutableRef(4L)).thenReturn(account4);

		assertEquals(ACCOUNT_IS_NOT_GENESIS_ACCOUNT, subject.validate(2L, scopedCheck));
		assertEquals(ACCOUNT_IS_TREASURY, subject.validate(3L, scopedCheck));
		assertEquals(ACCOUNT_STILL_OWNS_NFTS, subject.validate(4L, scopedCheck));
	}

	@Test
	void idSetPropagatesCallToEntities() {
		Set<Long> idSet = Set.of(1L, 2L, 3L);
		given(backingAccounts.idSet()).willReturn(idSet);

		assertEquals(idSet, subject.idSet());
		verify(backingAccounts).idSet();
	}

	@Test
	void sizePropagatesCallToEntities() {
		var size = 23L;
		given(backingAccounts.size()).willReturn(size);

		assertEquals(size, subject.size());
		verify(backingAccounts).size();
	}
}
