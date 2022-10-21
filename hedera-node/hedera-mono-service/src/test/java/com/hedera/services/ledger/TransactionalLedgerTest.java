/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.ledger;

import static com.hedera.services.ledger.accounts.TestAccount.Allowance.INSUFFICIENT;
import static com.hedera.services.ledger.accounts.TestAccount.Allowance.MISSING;
import static com.hedera.services.ledger.properties.TestAccountProperty.FLAG;
import static com.hedera.services.ledger.properties.TestAccountProperty.HBAR_ALLOWANCES;
import static com.hedera.services.ledger.properties.TestAccountProperty.LONG;
import static com.hedera.services.ledger.properties.TestAccountProperty.OBJ;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.interceptors.AccountsCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TestAccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionalLedgerTest {
    private final Object[] things = {"a", "b", "c", "d"};
    private final TestAccount anAccount =
            new TestAccount(
                    1L,
                    things[1],
                    false,
                    667L,
                    TestAccount.Allowance.OK,
                    TestAccount.Allowance.OK,
                    TestAccount.Allowance.OK);
    private final TestAccount anotherAccount =
            new TestAccount(
                    2L,
                    things[2],
                    true,
                    668L,
                    TestAccount.Allowance.OK,
                    TestAccount.Allowance.OK,
                    TestAccount.Allowance.OK);
    private final ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager =
            new ChangeSummaryManager<>();

    @Mock private BackingStore<Long, TestAccount> backingTestAccounts;
    @Mock private BackingStore<AccountID, HederaAccount> backingAccounts;
    @Mock private PropertyChangeObserver<Long, TestAccountProperty> propertyChangeObserver;
    @Mock private CommitInterceptor<Long, TestAccount, TestAccountProperty> testInterceptor;
    private LedgerCheck<TestAccount, TestAccountProperty> scopedCheck;
    private TransactionalLedger<Long, TestAccountProperty, TestAccount> testLedger;
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;

    @Test
    void settingInterceptorAlsoInitializesPendingChangesAndPreviewAction() {
        setupTestLedger();

        assertNull(testLedger.getPendingChanges());

        testLedger.setCommitInterceptor(testInterceptor);

        assertNotNull(testLedger.getPreviewAction());
        assertNotNull(testLedger.getPendingChanges());
    }

    @Test
    void beginningClearsPendingChanges() {
        setupInterceptedTestLedger();
        final var statefulChanges = testLedger.getPendingChanges();

        statefulChanges.include(1L, anAccount, Map.of(FLAG, true));
        testLedger.begin();

        assertEquals(0, statefulChanges.size());
    }

    @Test
    void doesNotPutAnythingForDestroyedEntity() {
        setupInterceptedTestLedger();
        given(backingTestAccounts.getImmutableRef(2L)).willReturn(anotherAccount);

        testLedger.begin();
        testLedger.destroy(2L);
        testLedger.commit();

        verify(backingTestAccounts, never()).put(eq(2L), any());
        verify(testInterceptor).postCommit();
    }

    @Test
    void doesNotCallRemoveIfInterceptorCompletesRemovals() {
        setupInterceptedTestLedger();
        given(testInterceptor.completesPendingRemovals()).willReturn(true);
        given(backingTestAccounts.getImmutableRef(2L)).willReturn(anotherAccount);

        testLedger.begin();
        testLedger.destroy(2L);
        testLedger.commit();

        verify(backingTestAccounts, never()).remove(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void committingIncludesOnlyNonZombieCreations() {
        final ArgumentCaptor<EntityChangeSet<Long, TestAccount, TestAccountProperty>> captor =
                forClass(EntityChangeSet.class);
        setupInterceptedTestLedger();

        final var expectedCommit = new TestAccount(0L, things, true);

        testLedger.begin();
        testLedger.create(1L);
        testLedger.set(1L, OBJ, things);
        testLedger.set(1L, FLAG, true);
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things);
        testLedger.destroy(2L);
        testLedger.commit();

        verify(testInterceptor).preview(captor.capture());
        final var changes = captor.getValue();
        assertEquals(1, changes.size());
        assertEquals(1L, changes.id(0));
        assertNull(changes.entity(0));
        assertEquals(Map.of(OBJ, things, FLAG, true), changes.changes(0));
        verify(backingTestAccounts).put(1L, expectedCommit);
        assertTrue(testLedger.getCreatedKeys().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void committingUsesProvidedFinisherIfPresent() {
        setupInterceptedTestLedger();

        testLedger.begin();
        testLedger.create(1L);
        testLedger.set(1L, OBJ, things);
        testLedger.set(1L, FLAG, true);
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things);
        testLedger.commit();

        verify(testInterceptor).finish(eq(0), any(TestAccount.class));
        verify(testInterceptor).finish(eq(1), any(TestAccount.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void committingIncludesOnlyNonTransientDestructions() {
        final ArgumentCaptor<EntityChangeSet<Long, TestAccount, TestAccountProperty>> captor =
                forClass(EntityChangeSet.class);
        setupInterceptedTestLedger();

        given(backingTestAccounts.getImmutableRef(2L)).willReturn(anotherAccount);
        testLedger.begin();
        testLedger.destroy(2L);
        testLedger.destroy(2L);
        testLedger.destroy(3L);
        testLedger.commit();

        verify(testInterceptor).preview(captor.capture());
        final var changes = captor.getValue();
        assertEquals(1, changes.size());
        assertEquals(2L, changes.id(0));
        assertEquals(anotherAccount, changes.entity(0));
        assertNull(changes.changes(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void committingIncludesOnlyUndestroyedUpdates() {
        final ArgumentCaptor<EntityChangeSet<Long, TestAccount, TestAccountProperty>> captor =
                forClass(EntityChangeSet.class);
        setupInterceptedTestLedger();

        given(backingTestAccounts.contains(1L)).willReturn(true);
        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.getImmutableRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(2L)).willReturn(true);
        final var expectedCommit =
                new TestAccount(
                        anAccount.getValue(),
                        things,
                        true,
                        anAccount.getTokenThing(),
                        TestAccount.Allowance.OK,
                        TestAccount.Allowance.OK,
                        TestAccount.Allowance.OK);

        testLedger.begin();
        testLedger.set(1L, OBJ, things);
        testLedger.set(1L, FLAG, true);
        testLedger.set(2L, OBJ, things);
        testLedger.destroy(2L);
        testLedger.commit();

        verify(testInterceptor).preview(captor.capture());
        final var changes = captor.getValue();
        assertEquals(1, changes.size());
        assertEquals(1L, changes.id(0));
        assertSame(anAccount, changes.entity(0));
        assertEquals(Map.of(OBJ, things, FLAG, true), changes.changes(0));
        verify(backingTestAccounts).put(1L, expectedCommit);
        assertTrue(testLedger.getChangedKeys().isEmpty());
    }

    @Test
    void commitObserverWorks() {
        setupTestLedger();
        testLedger.setPropertyChangeObserver(propertyChangeObserver);

        testLedger.begin();
        testLedger.create(1L);
        testLedger.set(1L, OBJ, things);
        testLedger.set(1L, FLAG, true);
        testLedger.commit();

        verify(propertyChangeObserver).newProperty(1L, OBJ, things);
        verify(propertyChangeObserver).newProperty(1L, FLAG, true);
        verifyNoMoreInteractions(propertyChangeObserver);
    }

    @Test
    void canUndoCreations() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(2L);
        testLedger.undoCreations();
        testLedger.commit();

        verify(backingTestAccounts, never()).put(any(), any());
    }

    @Test
    void canUndoCreationsOnlyInTxn() {
        setupTestLedger();

        assertThrows(IllegalStateException.class, testLedger::undoCreations);
    }

    @Test
    void rollbackClearsChanges() {
        setupTestLedger();

        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();
        testLedger.set(1L, OBJ, new Object());
        testLedger.rollback();

        assertTrue(testLedger.getChanges().isEmpty());
    }

    @Test
    void getUsesMutableRefIfPendingChanges() {
        setupTestLedger();
        given(backingTestAccounts.contains(1L)).willReturn(true);
        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);

        final var newAccount1 =
                new TestAccount(
                        anAccount.value,
                        anAccount.thing,
                        !anAccount.flag,
                        anAccount.tokenThing,
                        anAccount.validHbarAllowances,
                        anAccount.validFungibleAllowances,
                        anAccount.validNftAllowances);
        testLedger.begin();
        testLedger.set(1L, FLAG, !anAccount.flag);
        var account = testLedger.getFinalized(1L);

        assertEquals(newAccount1, account);
        verify(backingTestAccounts).getRef(1L);
    }

    @Test
    void zombieIsResurrectedIfPutAgain() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(1L);
        testLedger.create(2L);
        testLedger.destroy(1L);
        testLedger.destroy(2L);
        testLedger.put(1L, anAccount);
        testLedger.commit();

        verify(backingTestAccounts).put(1L, anAccount);
        verify(backingTestAccounts, never()).remove(1L);
    }

    @Test
    void putsInOrderOfChanges() {
        setupTestLedger();

        final int M = 2, N = 100;
        final var inOrder = inOrder(backingTestAccounts);
        final List<Long> ids = LongStream.range(M, N).boxed().toList();

        testLedger.begin();
        ids.forEach(id -> testLedger.create(id));
        testLedger.commit();

        LongStream.range(M, N)
                .boxed()
                .forEach(id -> inOrder.verify(backingTestAccounts).put(argThat(id::equals), any()));
    }

    @Test
    void destroysInOrder() {
        setupTestLedger();

        final int M = 2, N = 100;
        final var inOrder = inOrder(backingTestAccounts);
        final List<Long> ids = LongStream.range(M, N).boxed().toList();

        testLedger.begin();
        ids.forEach(id -> testLedger.create(id));
        testLedger.commit();
        testLedger.begin();
        ids.forEach(id -> testLedger.destroy(id));
        testLedger.commit();

        LongStream.range(M, N)
                .boxed()
                .forEach(id -> inOrder.verify(backingTestAccounts).put(argThat(id::equals), any()));
        LongStream.range(M, N)
                .boxed()
                .forEach(id -> inOrder.verify(backingTestAccounts).remove(id));
    }

    @Test
    void requiresManualRollbackIfCommitFails() {
        setupTestLedger();
        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        willThrow(IllegalStateException.class).given(backingTestAccounts).put(any(), any());

        testLedger.begin();
        testLedger.set(1L, OBJ, things[0]);
        testLedger.create(2L);

        assertThrows(IllegalStateException.class, () -> testLedger.commit());
        assertTrue(testLedger.isInTransaction());
    }

    @Test
    void recognizesPendingCreates() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(2L);

        assertTrue(testLedger.existsPending(2L));
        assertFalse(testLedger.existsPending(1L));
    }

    @Test
    void reportsDeadAccountsIndividually() {
        setupTestLedger();

        testLedger.begin();
        testLedger.destroy(1L);
        testLedger.destroy(2L);

        assertEquals("{*DEAD* 1, *DEAD* 2}", testLedger.changeSetSoFar());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoversFromChangeSetDescriptionProblem() {
        final CommitInterceptor<Long, TestAccount, TestAccountProperty> unhappy =
                mock(CommitInterceptor.class);
        willThrow(IllegalStateException.class).given(unhappy).preview(any());

        setupTestLedger();
        testLedger.setKeyToString(
                i -> {
                    throw new IllegalStateException();
                });
        testLedger.setCommitInterceptor(unhappy);

        testLedger.begin();
        testLedger.create(1L);
        testLedger.create(2L);
        testLedger.destroy(2L);

        assertThrows(IllegalStateException.class, testLedger::commit);
    }

    @Test
    void existsIfNotMissingAndNotDestroyed() {
        setupTestLedger();

        given(backingTestAccounts.contains(1L)).willReturn(true);
        given(backingTestAccounts.contains(2L)).willReturn(false);
        given(backingTestAccounts.contains(3L)).willReturn(false);

        testLedger.begin();
        testLedger.create(2L);
        testLedger.create(3L);
        testLedger.destroy(3L);

        boolean has1 = testLedger.exists(1L);
        boolean has2 = testLedger.exists(2L);
        boolean has3 = testLedger.exists(3L);

        assertTrue(has1);
        assertTrue(has2);
        assertFalse(has3);
    }

    @Test
    void delegatesDestroyToRemove() {
        setupTestLedger();

        testLedger.begin();

        testLedger.destroy(1L);
        testLedger.commit();

        verify(backingTestAccounts).remove(1L);
    }

    @Test
    void throwsIfNotInTransaction() {
        setupTestLedger();

        assertThrows(IllegalStateException.class, () -> testLedger.set(1L, OBJ, things[0]));
        assertThrows(IllegalStateException.class, () -> testLedger.create(2L));
        assertThrows(IllegalStateException.class, () -> testLedger.destroy(1L));
    }

    @Test
    void throwsOnMutationToMissingAccount() {
        setupTestLedger();

        testLedger.begin();

        assertThrows(MissingEntityException.class, () -> testLedger.set(0L, OBJ, things[0]));
    }

    @Test
    void throwsOnCreationWithExistingAccountId() {
        setupTestLedger();
        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();

        assertThrows(IllegalArgumentException.class, () -> testLedger.create(1L));
    }

    @Test
    void throwsOnGettingPropOfMissingAccount() {
        setupTestLedger();

        assertThrows(IllegalArgumentException.class, () -> testLedger.get(2L, OBJ));
    }

    @Test
    void returnsPendingChangePropertiesOfExistingAccounts() {
        setupTestLedger();
        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();
        testLedger.set(1L, LONG, 3L);

        final long value = (long) testLedger.get(1L, LONG);

        verify(backingTestAccounts, times(2)).contains(1L);
        assertEquals(3L, value);
    }

    @Test
    void incorporatesMutationToPendingNewAccount() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things[2]);

        assertEquals(new TestAccount(0L, things[2], false), testLedger.getFinalized(2L));
    }

    @Test
    void returnsSetPropertiesOfPendingNewAccounts() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things[2]);

        Object thing = testLedger.get(2L, OBJ);

        assertEquals(things[2], thing);
    }

    @Test
    void returnsDefaultForUnsetPropertiesOfPendingNewAccounts() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things[2]);

        final var flag = (boolean) testLedger.get(2L, FLAG);

        assertFalse(flag);
    }

    @Test
    void reflectsChangeToExistingAccountIfInTransaction() {
        setupTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        final var expected =
                new TestAccount(
                        anAccount.value,
                        things[0],
                        anAccount.flag,
                        667L,
                        anAccount.validHbarAllowances,
                        anAccount.validFungibleAllowances,
                        anAccount.validNftAllowances);

        testLedger.begin();
        testLedger.set(1L, OBJ, things[0]);

        assertEquals(expected, testLedger.getFinalized(1L));
    }

    @Test
    void canUndoSpecificChange() {
        setupTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);
        ArgumentCaptor<TestAccount> captor = forClass(TestAccount.class);
        final var changesToUndo = List.of(FLAG);

        assertThrows(
                IllegalStateException.class, () -> testLedger.undoChangesOfType(changesToUndo));

        testLedger.begin();
        testLedger.set(1L, OBJ, things[0]);
        testLedger.set(1L, FLAG, true);
        testLedger.undoChangesOfType(List.of(FLAG));
        testLedger.commit();

        verify(backingTestAccounts).put(longThat(l -> l == 1L), captor.capture());
        final var committed = captor.getValue();
        assertSame(things[0], committed.getThing());
        assertFalse(committed.isFlag());
    }

    @Test
    void throwsIfTxnAlreadyBegun() {
        setupTestLedger();

        testLedger.begin();
        testLedger.create(1L);
        testLedger.set(1L, OBJ, things[0]);
        assertFalse(testLedger.getChanges().isEmpty());
        testLedger.begin();
        assertTrue(testLedger.getChanges().isEmpty());
    }

    @Test
    void throwsOnRollbackWithoutActiveTxn() {
        setupTestLedger();

        assertThrows(IllegalStateException.class, () -> testLedger.rollback());
    }

    @Test
    void throwsOnCommitWithoutActiveTxn() {
        setupTestLedger();

        assertThrows(IllegalStateException.class, () -> testLedger.commit());
    }

    @Test
    void dropsPendingChangesAfterRollback() {
        setupTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();
        testLedger.set(1L, OBJ, things[0]);
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things[2]);
        testLedger.rollback();

        assertFalse(testLedger.isInTransaction());
        assertEquals(anAccount, testLedger.getFinalized(1L));
        assertFalse(testLedger.exists(2L));
    }

    @Test
    void persistsPendingChangesAndDestroysDeadAccountsAfterCommit() {
        setupTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        final var expected2 = new TestAccount(2L, things[2], false);

        testLedger.begin();
        testLedger.set(1L, OBJ, things[0]);
        testLedger.create(2L);
        testLedger.set(2L, OBJ, things[2]);
        testLedger.set(2L, LONG, 2L);
        testLedger.create(3L);
        testLedger.set(3L, OBJ, things[3]);
        testLedger.destroy(3L);
        testLedger.commit();

        assertFalse(testLedger.isInTransaction());
        assertEquals("{}", testLedger.changeSetSoFar());
        verify(backingTestAccounts).put(2L, expected2);
        verify(backingTestAccounts)
                .put(
                        1L,
                        new TestAccount(
                                1L,
                                things[0],
                                false,
                                667L,
                                TestAccount.Allowance.OK,
                                TestAccount.Allowance.OK,
                                TestAccount.Allowance.OK));
        verify(backingTestAccounts, never()).put(3L, new TestAccount(0L, things[3], false));
        verify(backingTestAccounts).remove(3L);
    }

    @Test
    void reflectsUnchangedAccountIfNoChanges() {
        setupTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);
        assertEquals(anAccount, testLedger.getFinalized(1L));
    }

    @Test
    void validateHappyPath() {
        setupCheckableTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.getImmutableRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();
        testLedger.set(1L, LONG, 123L);
        testLedger.set(1L, FLAG, false);
        testLedger.set(1L, OBJ, "DEFAULT");
        testLedger.commit();

        assertEquals(ResponseCodeEnum.OK, testLedger.validate(1L, scopedCheck));
    }

    @Test
    void validationFailsForMissingAccount() {
        setupCheckableTestLedger();

        assertEquals(INVALID_ACCOUNT_ID, testLedger.validate(2L, scopedCheck));
    }

    @Test
    void validationFailsAsExpected() {
        setupCheckableTestLedger();

        TestAccount account2 = new TestAccount(321L, things[1], false, 667L);
        TestAccount account3 = new TestAccount(123L, things[1], true, 667L);
        TestAccount account4 = new TestAccount(123L, "RANDOM", false, 667L);

        when(backingTestAccounts.contains(2L)).thenReturn(true);
        when(backingTestAccounts.getImmutableRef(2L)).thenReturn(account2);
        when(backingTestAccounts.contains(3L)).thenReturn(true);
        when(backingTestAccounts.getImmutableRef(3L)).thenReturn(account3);
        when(backingTestAccounts.contains(4L)).thenReturn(true);
        when(backingTestAccounts.getImmutableRef(4L)).thenReturn(account4);

        assertEquals(ACCOUNT_IS_NOT_GENESIS_ACCOUNT, testLedger.validate(2L, scopedCheck));
        assertEquals(ACCOUNT_IS_TREASURY, testLedger.validate(3L, scopedCheck));
        assertEquals(ACCOUNT_STILL_OWNS_NFTS, testLedger.validate(4L, scopedCheck));
    }

    @Test
    void validationFailsWithMissingHbarAllowance() {
        setupCheckableTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.getImmutableRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();
        testLedger.set(1L, LONG, 123L);
        testLedger.set(1L, FLAG, false);
        testLedger.set(1L, OBJ, "DEFAULT");
        testLedger.set(1L, HBAR_ALLOWANCES, MISSING);
        testLedger.commit();

        assertEquals(SPENDER_DOES_NOT_HAVE_ALLOWANCE, testLedger.validate(1L, scopedCheck));
    }

    @Test
    void validationFailsWithInsufficientHbarAllowance() {
        setupCheckableTestLedger();

        given(backingTestAccounts.getRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.getImmutableRef(1L)).willReturn(anAccount);
        given(backingTestAccounts.contains(1L)).willReturn(true);

        testLedger.begin();
        testLedger.set(1L, LONG, 123L);
        testLedger.set(1L, FLAG, false);
        testLedger.set(1L, OBJ, "DEFAULT");
        testLedger.set(1L, HBAR_ALLOWANCES, INSUFFICIENT);
        testLedger.commit();

        assertEquals(AMOUNT_EXCEEDS_ALLOWANCE, testLedger.validate(1L, scopedCheck));
    }

    @Test
    void idSetPropagatesCallToEntities() {
        setupTestLedger();

        final Set<Long> idSet = Set.of(1L, 2L, 3L);
        given(backingTestAccounts.idSet()).willReturn(idSet);

        assertEquals(idSet, testLedger.idSet());
        verify(backingTestAccounts).idSet();
    }

    @Test
    void sizePropagatesCallToEntities() {
        setupTestLedger();

        final var size = 23L;
        given(backingTestAccounts.size()).willReturn(size);

        assertEquals(size, testLedger.size());
        verify(backingTestAccounts).size();
    }

    @Test
    void throwsOnCommittingInconsistentAdjustments() {
        setupInterceptedAccountsLedger();

        when(backingAccounts.contains(rand)).thenReturn(true);
        when(backingAccounts.getImmutableRef(rand)).thenReturn(randMerkleAccount);
        when(backingAccounts.contains(aliasAccountId)).thenReturn(true);
        when(backingAccounts.getImmutableRef(aliasAccountId)).thenReturn(aliasMerkleAccount);

        accountsLedger.begin();
        accountsLedger.set(rand, AccountProperty.BALANCE, 4L);
        accountsLedger.set(aliasAccountId, AccountProperty.BALANCE, 8L);

        assertThrows(IllegalStateException.class, () -> accountsLedger.commit());
    }

    private void setupAccountsLedger() {
        accountsLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
    }

    private void setupInterceptedAccountsLedger() {
        setupAccountsLedger();

        final var liveIntercepter = new AccountsCommitInterceptor(new SideEffectsTracker());
        accountsLedger.setCommitInterceptor(liveIntercepter);
    }

    private void setupTestLedger() {
        testLedger =
                new TransactionalLedger<>(
                        TestAccountProperty.class,
                        TestAccount::new,
                        backingTestAccounts,
                        changeManager);
    }

    private void setupInterceptedTestLedger() {
        testLedger =
                new TransactionalLedger<>(
                        TestAccountProperty.class,
                        TestAccount::new,
                        backingTestAccounts,
                        changeManager);
        testLedger.setCommitInterceptor(testInterceptor);
    }

    private void setupCheckableTestLedger() {
        setupTestLedger();
        scopedCheck = new TestAccountScopedCheck();
    }

    private static final ByteString alias =
            ByteString.copyFromUtf8("These aren't the droids you're looking for");
    private static final AccountID rand = AccountID.newBuilder().setAccountNum(2_345).build();
    private static final AccountID aliasAccountId = AccountID.newBuilder().setAlias(alias).build();
    private static final MerkleAccount randMerkleAccount = new MerkleAccount();
    private static final MerkleAccount aliasMerkleAccount = new MerkleAccount();
}
