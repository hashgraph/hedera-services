/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.ledger.TransactionalLedger.activeLedgerWrapping;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.HashMapTestAccounts;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TestAccountProperty;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LedgerImplBackingStoreTest {
    private static final TestAccountProperty[] ALL_PROPS =
            TestAccountProperty.class.getEnumConstants();

    private final TestAccount aTestAccount = new TestAccount(1L, new Object(), true, 9L);
    private final TestAccount bTestAccount = new TestAccount(2L, new Object(), false, 8L);

    private BackingStore<Long, TestAccount> backingTestAccounts;
    private TransactionalLedger<Long, TestAccountProperty, TestAccount> firstOrder;

    private TransactionalLedger<Long, TestAccountProperty, TestAccount> subject;

    @BeforeEach
    void setUp() {
        backingTestAccounts = new HashMapTestAccounts();

        firstOrder =
                new TransactionalLedger<>(
                        TestAccountProperty.class,
                        TestAccount::new,
                        backingTestAccounts,
                        new ChangeSummaryManager<>());
    }

    @Test
    void containsDelegatesToExists() {
        givenMockSubject();

        given(subject.exists(1L)).willReturn(true);
        doCallRealMethod().when(subject).contains(1L);

        assertTrue(subject.contains(1L));
        verify(subject).exists(1L);
    }

    @Test
    void removeDelegatesToDestroy() {
        givenMockSubject();

        doCallRealMethod().when(subject).remove(1L);

        subject.remove(1L);

        verify(subject).destroy(1L);
    }

    @Test
    void putThrowsIfNotInTxn() {
        givenFirstOrderSubject();

        assertThrows(IllegalStateException.class, () -> subject.put(1L, aTestAccount));
    }

    @Test
    void putAccumulatesAllChangesFromReceivedEntityButDoesNotAffectStore() {
        givenFirstOrderSubject();
        subject.begin();

        subject.create(1L);
        subject.put(1L, aTestAccount);

        final var changeSet = subject.getChanges().get(1L);
        for (final var prop : ALL_PROPS) {
            assertEquals(prop.getter().apply(aTestAccount), changeSet.get(prop));
        }

        assertFalse(backingTestAccounts.contains(1L));
    }

    @Test
    void putCreatesMissingIdIfNeededToAccumulatesAllChangesFromReceivedEntity() {
        givenFirstOrderSubject();
        subject.begin();

        subject.put(1L, aTestAccount);

        final var changeSet = subject.getChanges().get(1L);
        for (final var prop : ALL_PROPS) {
            assertEquals(prop.getter().apply(aTestAccount), changeSet.get(prop));
        }
    }

    @Test
    void returnsNullForMissingEntity() {
        givenSecondOrderSubject();

        assertNull(subject.getRef(1L));
    }

    @Test
    void usesScopedPropertyGetterToGetEntityRefWithPendingChangeSetIfBackedByLedger() {
        givenSecondOrderSubject();

        backingTestAccounts.put(1L, aTestAccount);
        firstOrder.begin();
        firstOrder.set(1L, TestAccountProperty.LONG, 2L);

        subject.begin();
        subject.set(1L, TestAccountProperty.TOKEN_LONG, 8L);

        final var netEntity = subject.getRef(1L);
        assertNotSame(aTestAccount, netEntity);
        assertEquals(2L, netEntity.getValue());
        assertEquals(aTestAccount.isFlag(), netEntity.isFlag());
        assertSame(aTestAccount.getThing(), netEntity.getThing());
        assertEquals(8L, netEntity.getTokenThing());
    }

    @Test
    void usesScopedPropertyGetterToGetEntityRefWithPendingChangeSetIfBackedByStore() {
        givenFirstOrderSubject();

        backingTestAccounts.put(1L, aTestAccount);

        subject.begin();
        subject.set(1L, TestAccountProperty.LONG, 2L);
        subject.set(1L, TestAccountProperty.TOKEN_LONG, 8L);

        final var netEntity = subject.getRef(1L);
        assertNotSame(aTestAccount, netEntity);
        assertEquals(2L, netEntity.getValue());
        assertEquals(aTestAccount.isFlag(), netEntity.isFlag());
        assertSame(aTestAccount.getThing(), netEntity.getThing());
        assertEquals(8L, netEntity.getTokenThing());
    }

    @Test
    void usesScopedPropertyGetterToGetEntityRefWithNoPendingChangeSetIfBackedByStore() {
        givenFirstOrderSubject();

        backingTestAccounts.put(1L, aTestAccount);

        final var netEntity = subject.getRef(1L);
        assertNotSame(aTestAccount, netEntity);
        assertEquals(aTestAccount, netEntity);
    }

    @Test
    void usesScopedPropertyGetterToGetEntityRefWithoutPendingChangeSetIfBackedByLedger() {
        givenSecondOrderSubject();

        backingTestAccounts.put(1L, aTestAccount);
        firstOrder.begin();
        firstOrder.set(1L, TestAccountProperty.LONG, 2L);

        final var netEntity = subject.getRef(1L);
        assertNotSame(aTestAccount, netEntity);
        assertEquals(2L, netEntity.getValue());
        assertEquals(aTestAccount.isFlag(), netEntity.isFlag());
        assertSame(aTestAccount.getThing(), netEntity.getThing());
        assertEquals(aTestAccount.getTokenThing(), netEntity.getTokenThing());
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesScopedPropertyGetterToValidateExtantEntityIfBackedByLedger() {
        givenSecondOrderSubject();

        backingTestAccounts.put(1L, aTestAccount);
        firstOrder.begin();
        firstOrder.set(1L, TestAccountProperty.LONG, 2L);

        final var captor = ArgumentCaptor.forClass(Function.class);
        final var mockCheck =
                (LedgerCheck<TestAccount, TestAccountProperty>) mock(LedgerCheck.class);
        given(mockCheck.checkUsing(any(Function.class), any(Map.class)))
                .willReturn(INVALID_TOKEN_MINT_AMOUNT);

        subject.begin();
        subject.set(1L, TestAccountProperty.FLAG, false);
        final var actual = subject.validate(1L, mockCheck);

        assertEquals(INVALID_TOKEN_MINT_AMOUNT, actual);

        verify(mockCheck).checkUsing(captor.capture(), any(Map.class));
        final var extantProps = (Function<TestAccountProperty, Object>) captor.getValue();
        assertEquals(2L, extantProps.apply(TestAccountProperty.LONG));
        assertEquals(aTestAccount.isFlag(), extantProps.apply(TestAccountProperty.FLAG));
        assertSame(aTestAccount.getThing(), extantProps.apply(TestAccountProperty.OBJ));
        assertEquals(
                aTestAccount.getTokenThing(), extantProps.apply(TestAccountProperty.TOKEN_LONG));
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesDefaultPropertyGetterToValidateNewEntityIfBackedByLedger() {
        givenSecondOrderSubject();

        final var aDefaultAccount = new TestAccount();

        final var captor = ArgumentCaptor.forClass(Function.class);
        final var mockCheck =
                (LedgerCheck<TestAccount, TestAccountProperty>) mock(LedgerCheck.class);
        given(mockCheck.checkUsing(any(Function.class), any(Map.class)))
                .willReturn(INVALID_TOKEN_MINT_AMOUNT);

        subject.begin();
        subject.create(1L);
        subject.set(1L, TestAccountProperty.FLAG, false);
        final var actual = subject.validate(1L, mockCheck);

        assertEquals(INVALID_TOKEN_MINT_AMOUNT, actual);

        verify(mockCheck).checkUsing(captor.capture(), any(Map.class));
        final var extantProps = (Function<TestAccountProperty, Object>) captor.getValue();
        assertEquals(aDefaultAccount.getValue(), extantProps.apply(TestAccountProperty.LONG));
        assertEquals(aDefaultAccount.isFlag(), extantProps.apply(TestAccountProperty.FLAG));
        assertNull(extantProps.apply(TestAccountProperty.OBJ));
        assertEquals(
                aDefaultAccount.getTokenThing(), extantProps.apply(TestAccountProperty.TOKEN_LONG));
    }

    @Test
    void usesScopedPropertyGetterWithExtantEntityIfBackedByLedger() {
        final var expected = new Object();
        final var mockLedger = mockLedger();
        givenSecondOrderSubjectBackedBy(mockLedger);

        given(mockLedger.contains(1L)).willReturn(true);
        given(mockLedger.get(1L, TestAccountProperty.OBJ)).willReturn(expected);

        final var actual = subject.get(1L, TestAccountProperty.OBJ);

        assertSame(expected, actual);
    }

    @Test
    void usesDefaultPropertyWithNewlyCreatedEntityIfBackedByLedger() {
        givenSecondOrderSubject();

        subject.begin();
        subject.create(1L);

        final var actual = subject.get(1L, TestAccountProperty.TOKEN_LONG);

        assertEquals(TestAccount.DEFAULT_TOKEN_THING, actual);
    }

    @Test
    void createsWrappingLedgerAsExpected() {
        givenFirstOrderSubject();

        backingTestAccounts.put(1L, aTestAccount);
        subject.begin();
        subject.put(2L, bTestAccount);

        final var wrapper = activeLedgerWrapping(subject);
        assertTrue(wrapper.isInTransaction());
        wrapper.create(3L);

        assertEquals(aTestAccount, wrapper.getRef(1L));
        assertEquals(bTestAccount, wrapper.getRef(2L));
        assertSame(subject.getChangeManager(), wrapper.getChangeManager());
        assertSame(subject.getNewEntity(), wrapper.getNewEntity());
        assertFalse(subject.contains(3L));
    }

    /* --- Helpers --- */
    private void givenFirstOrderSubject() {
        subject = firstOrder;
    }

    private void givenSecondOrderSubject() {
        givenSecondOrderSubjectBackedBy(firstOrder);
    }

    private void givenSecondOrderSubjectBackedBy(
            TransactionalLedger<Long, TestAccountProperty, TestAccount> ledger) {
        subject =
                new TransactionalLedger<>(
                        TestAccountProperty.class,
                        TestAccount::new,
                        ledger,
                        new ChangeSummaryManager<>());
    }

    private void givenMockSubject() {
        subject = mockLedger();
    }

    @SuppressWarnings("unchecked")
    private TransactionalLedger<Long, TestAccountProperty, TestAccount> mockLedger() {
        return (TransactionalLedger<Long, TestAccountProperty, TestAccount>)
                mock(TransactionalLedger.class);
    }
}
