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
package com.hedera.services.ledger.backing;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.FcLong;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.merkle.utility.KeyedMerkleLong;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackingAccountsTest {
    private final AccountID a = asAccount("0.0.1");
    private final AccountID b = asAccount("0.0.2");
    private final EntityNum aKey = EntityNum.fromAccountId(a);
    private final EntityNum bKey = EntityNum.fromAccountId(b);
    private final MerkleAccount aValue = MerkleAccountFactory.newAccount().balance(123L).get();
    private final MerkleAccount bValue = MerkleAccountFactory.newAccount().balance(122L).get();

    private MerkleMap<EntityNum, MerkleAccount> delegate;
    private BackingAccounts subject;

    @BeforeEach
    void setup() {
        delegate = new MerkleMap<>();
        delegate.put(aKey, aValue);
        delegate.put(bKey, bValue);

        subject = new BackingAccounts(() -> delegate);

        subject.rebuildFromSources();
    }

    @Test
    void auxiliarySetIsRebuiltFromScratch() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
        final var idSet = subject.getExistingAccounts();

        subject.rebuildFromSources();

        assertTrue(idSet.contains(aKey.toGrpcAccountId()));
        assertTrue(idSet.contains(bKey.toGrpcAccountId()));

        delegate.remove(aKey);

        subject.rebuildFromSources();

        assertFalse(idSet.contains(aKey.toGrpcAccountId()));
        assertTrue(idSet.contains(bKey.toGrpcAccountId()));
    }

    @Test
    void idSetIsDedicatedAuxiliary() {
        final var firstIdSet = subject.idSet();
        final var secondIdSet = subject.idSet();

        assertSame(firstIdSet, secondIdSet);
    }

    @Test
    void containsDelegatesToKnownActive() {
        // expect:
        assertTrue(subject.contains(a));
        assertTrue(subject.contains(b));
    }

    @Test
    void putUpdatesKnownAccounts() {
        // when:
        subject.put(a, aValue);

        // then:
        assertTrue(subject.contains(a));
    }

    @Test
    void getRefIsReadThrough() {
        // expect:
        assertEquals(aValue, subject.getRef(a));
        assertEquals(bValue, subject.getRef(b));
        assertEquals(aValue, subject.getImmutableRef(a));
        assertEquals(bValue, subject.getImmutableRef(b));
    }

    @Test
    void removeUpdatesDelegate() {
        // when:
        subject.remove(b);

        // then:
        assertFalse(subject.contains(b));
    }

    @Test
    void returnsMutableRef() {
        final var mutable = subject.getRef(b);

        assertEquals(bValue, mutable);
        assertFalse(mutable.isImmutable());
    }

    @Test
    void getImmutableRefDelegatesToGet() {
        // when:
        final var immutable = subject.getImmutableRef(b);

        // then:
        assertEquals(bValue, immutable);
    }

    @Test
    void returnsExpectedIds() {
        // setup:
        final var s = Set.of(a, b);

        // expect:
        assertEquals(s, subject.idSet());
    }

    @Test
    void returnsExpectedSize() {
        // expect:
        assertEquals(2, subject.size());
    }

    @Test
    void twoPutsChangesG4M() throws ConstructableRegistryException {
        // setup:
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(KeyedMerkleLong.class, KeyedMerkleLong::new));
        final var oneGrandKey = new FcLong(1000L);
        final var twoGrandKey = new FcLong(2000L);
        final var evilKey = new FcLong(666L);
        final var nonEvilKey = new FcLong(667L);

        /* Case 1: g4m a leaf; then put ONE new leaf; then change the mutable leaf and re-get to verify new value */
        final MerkleMap<FcLong, KeyedMerkleLong<FcLong>> firstMm = new MerkleMap<>();
        final var oneGrandEntry = new KeyedMerkleLong<>(oneGrandKey, 1000L);
        firstMm.put(oneGrandKey, oneGrandEntry);
        final var mutableOne = firstMm.getForModify(oneGrandKey);
        /* Putting just one new leaf */
        final var evilEntry = new KeyedMerkleLong<>(evilKey, 666L);
        firstMm.put(evilKey, evilEntry);
        /* Then the mutable value is retained */
        assertSame(mutableOne, firstMm.get(oneGrandKey));

        /* Case 2: g4m a leaf; then put TWO new leaves; then change the mutable leaf and re-get to verify new value */
        final var secondFcm = new MerkleMap<FcLong, KeyedMerkleLong<FcLong>>();
        final var twoGrandEntry = new KeyedMerkleLong<>(twoGrandKey, 2000L);
        final var evilEntry2 = new KeyedMerkleLong<>(evilKey, 666L);
        final var nonEvilEntry2 = new KeyedMerkleLong<>(nonEvilKey, 667L);
        secondFcm.put(twoGrandKey, twoGrandEntry);
        final var mutableTwo = secondFcm.getForModify(twoGrandEntry.getKey());
        /* Putting two new leaves now */
        secondFcm.put(evilEntry2.getKey(), evilEntry2);
        secondFcm.put(nonEvilEntry2.getKey(), nonEvilEntry2);
        /* And now changing the once-mutable value throws MutabilityException */
        assertThrows(MutabilityException.class, mutableTwo::increment);
    }
}
