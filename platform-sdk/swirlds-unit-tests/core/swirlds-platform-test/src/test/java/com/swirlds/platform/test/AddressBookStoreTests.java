/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.address.AddressBookValidator;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.platform.state.address.AddressBookStore;
import com.swirlds.platform.state.address.SequentialAddressBookStore;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("AddressBookStore Tests")
class AddressBookStoreTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    public static void setUpdateBehavior(final boolean updateAddressBookOnlyAtUpgrade) {
        new TestConfigBuilder()
                .withValue("addressBook.updateAddressBookOnlyAtUpgrade", updateAddressBookOnlyAtUpgrade)
                .getOrCreateConfig();
    }

    @AfterAll
    static void afterAll() {
        // Revert to the default to avoid breaking other tests
        setUpdateBehavior(true);
    }

    /**
     * Describes how to build an implementation of {@link AddressBookStore AddressBookStore}.
     */
    private record AddressBookStoreImpl(
            String name, Supplier<AddressBookStore> constructor, boolean updateAddressBookOnlyAtUpgrade) {
        @Override
        public String toString() {
            return name + ", update only at upgrade " + (updateAddressBookOnlyAtUpgrade ? "on" : "off");
        }
    }

    static Stream<Arguments> addressBookStoreImpls() {
        return Stream.of(
                Arguments.of(new AddressBookStoreImpl(
                        SequentialAddressBookStore.class.getSimpleName(), SequentialAddressBookStore::new, true)),
                Arguments.of(new AddressBookStoreImpl(
                        SequentialAddressBookStore.class.getSimpleName(), SequentialAddressBookStore::new, false)));
    }

    /**
     * Do basic sanity checking on an address book store.
     */
    private void validateStoreConsistency(final AddressBookStore store, final boolean updateAddressBookOnlyAtUpgrade) {

        final long firstRound = store.getEarliestRound();
        final long lastRound = store.getLatestRound();

        if (store.getSize() > 0) {
            assertEquals(lastRound - firstRound + 1, store.getSize(), "invalid store size");
            assertEquals(store.getEarliest(), store.get(firstRound), "incorrect first round");
            assertEquals(store.getLatest(), store.get(lastRound), "incorrect latest round");
        }

        // Make sure there are no address books from early rounds
        for (long round = firstRound - 100; round < firstRound; round++) {
            assertFalse(store.contains(round), "should not contain round");
            final long finalRound = round;
            assertThrows(NoSuchElementException.class, () -> store.get(finalRound), "no round should be found");
        }

        // Make sure all the address books we expect to see are present
        for (long round = firstRound; round < firstRound + store.getSize(); round++) {
            // Java doesn't like lambdas to capture non-final variables
            final long finalRound = round;

            assertTrue(store.contains(finalRound), "round should be present");
            final AddressBook finalAddressBook = store.get(finalRound);
            assertNotNull(finalAddressBook, "address book should not be null");
            assertTrue(
                    finalAddressBook.isImmutable(),
                    "address books in an address book store should always be immutable");

            if (!updateAddressBookOnlyAtUpgrade) {
                // Make sure things work with runtime update on
                final AddressBook addressBook = store.get(finalRound);
                assertEquals(finalRound, addressBook.getRound(), "address book has incorrect round");
                if (finalRound > firstRound) {
                    final AddressBook previous = store.get(finalRound - 1);

                    assertTrue(
                            AddressBookValidator.isNextAddressBookValid(previous, addressBook),
                            "validator should not have prevented address book insertion");
                    assertNotSame(previous, addressBook, "address book should not be present multiple times in store");
                }
            } else {
                // Make sure things work with runtime update off
                assertSame(store.getEarliest(), store.get(finalRound), "all address books should be the same");
            }
        }

        // Make sure there are no address books "off the end"
        for (long round = lastRound + 1; round < lastRound + 100; round++) {
            assertFalse(store.contains(round), "should not contain round");
            final long finalRound = round;
            assertThrows(NoSuchElementException.class, () -> store.get(finalRound), "no round should be found");
        }
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("What Goes In Comes Out Test")
    void whatGoesInComesOutTest(final AddressBookStoreImpl addressBookStoreImpl) throws InterruptedException {
        final Random random = getRandomPrintSeed();

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        final int count = 100;
        final int addressBookSize = 20;
        final int storeSize = 26;

        final Map<Long, AddressBook> expectedAddressBooks = new HashMap<>();
        final AddressBookStore store = addressBookStoreImpl.constructor.get();

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        AddressBook initialAddressBook = null;

        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random).setSize(addressBookSize);

        for (long round = random.nextLong(100); round < count; round++) {

            // Add a new address book
            final AddressBook addressBook = generator.build();
            addressBook.setRound(round);

            if (initialAddressBook == null) {
                initialAddressBook = addressBook;
            }

            expectedAddressBooks.put(round, addressBook);
            store.add(addressBook);

            // Remove an old address book
            if (expectedAddressBooks.size() > storeSize) {
                final long oldestRoundInStore = round - storeSize;
                expectedAddressBooks.remove(oldestRoundInStore - 1);
                store.shiftWindow(oldestRoundInStore);
            }

            setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

            assertEquals(expectedAddressBooks.size(), store.getSize(), "unexpected size");

            if (!addressBookStoreImpl.updateAddressBookOnlyAtUpgrade) {
                // Check all address books currently in the store
                for (final long r : expectedAddressBooks.keySet()) {
                    assertSame(expectedAddressBooks.get(r), store.get(r), "unexpected address book discovered");
                }
            }

            if (addressBookStoreImpl.updateAddressBookOnlyAtUpgrade) {
                // When runtime update is off, only the initial overriding book should be returned
                for (final long r : expectedAddressBooks.keySet()) {
                    assertSame(initialAddressBook, store.get(r), "unexpected address book discovered");
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Release Test")
    void releaseTest(final AddressBookStoreImpl addressBookStoreImpl) {
        final Random random = getRandomPrintSeed();

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        final int count = 100;
        final int addressBookSize = 20;

        final Map<Long, AddressBook> addressBooks = new HashMap<>();
        final AddressBookStore store = addressBookStoreImpl.constructor().get();

        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random).setSize(addressBookSize);

        // Insert a bunch of address books.
        for (long round = 0; round < count; round++) {
            final AddressBook addressBook = generator.build();
            assertEquals(0, addressBook.getReservationCount(), "implicit reservation expected");
            addressBook.setRound(round);

            addressBooks.put(round, addressBook);
            store.add(addressBook);
            assertEquals(1 + (round == 0 ? 1 : 0), addressBook.getReservationCount(), "explicit reservation expected");
        }

        // Each inserted address book should have a reservation count of exactly 1 (except for overriding)
        for (final long round : addressBooks.keySet()) {
            assertEquals(
                    1 + (round == 0 ? 1 : 0),
                    addressBooks.get(round).getReservationCount(),
                    "unexpected reservation count");
        }

        // Purge some addresses
        final long earliestRoundAfterPurge = count / 2;
        store.shiftWindow(earliestRoundAfterPurge);

        // Purged addresses should be destroyed, all others should have a reservation count of 1 (except for overriding)
        for (final long round : addressBooks.keySet()) {
            if (round < earliestRoundAfterPurge) {
                if (round == 0) {
                    assertEquals(1, addressBooks.get(round).getReservationCount(), "unexpected reservation count");
                } else {
                    assertTrue(addressBooks.get(round).isDestroyed(), "address book should have been destroyed");
                }
            } else {
                assertEquals(1, addressBooks.get(round).getReservationCount(), "unexpected reservation count");
            }
        }

        // Make a copy. All remaining address books should now have a reservation count of 2. (except for overriding)
        final AddressBookStore copy = store.copy();
        for (final long round : addressBooks.keySet()) {
            if (round < earliestRoundAfterPurge) {
                if (round == 0) {
                    assertEquals(2, addressBooks.get(round).getReservationCount(), "unexpected reservation count");
                } else {
                    assertTrue(addressBooks.get(round).isDestroyed(), "address book should have been destroyed");
                }
            } else {
                assertEquals(2, addressBooks.get(round).getReservationCount(), "unexpected reservation count");
            }
        }

        // Release the original. All remaining address books should now have a reservation count of 1 again.
        // (except for overriding)
        store.release();
        for (final long round : addressBooks.keySet()) {
            if (round < earliestRoundAfterPurge) {
                if (round == 0) {
                    assertEquals(1, addressBooks.get(round).getReservationCount(), "unexpected reservation count");
                } else {
                    assertTrue(addressBooks.get(round).isDestroyed(), "address book should have been destroyed");
                }
            } else {
                assertEquals(1, addressBooks.get(round).getReservationCount(), "unexpected reservation count");
            }
        }

        // Release the copy. All address books should now be destroyed.
        copy.release();
        for (final long round : addressBooks.keySet()) {
            assertTrue(addressBooks.get(round).isDestroyed(), "address book should have been destroyed");
        }
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Hash Test")
    void hashTest(final AddressBookStoreImpl addressBookStoreImpl) {
        final Random random = getRandomPrintSeed();

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        final long seed1 = random.nextLong();
        final RandomAddressBookGenerator generator1 = new RandomAddressBookGenerator(seed1);
        final RandomAddressBookGenerator generator2 = new RandomAddressBookGenerator(seed1);

        final long seed3 = random.nextLong();
        final RandomAddressBookGenerator generator3 = new RandomAddressBookGenerator(seed3);

        final AddressBookStore store1 = addressBookStoreImpl.constructor.get();
        final AddressBookStore store2 = addressBookStoreImpl.constructor.get();
        final AddressBookStore store3 = addressBookStoreImpl.constructor.get();

        for (int i = 0; i < 100; i++) {
            store1.add(generator1.build().setRound(i));
            store2.add(generator2.build().setRound(i));
            store3.add(generator3.build().setRound(i));
        }

        MerkleCryptoFactory.getInstance().digestTreeSync(store1);
        MerkleCryptoFactory.getInstance().digestTreeSync(store2);
        MerkleCryptoFactory.getInstance().digestTreeSync(store2);

        assertEquals(store1.getHash(), store2.getHash(), "identical stores should have the same hash");
        assertNotEquals(store1.getHash(), store3.getHash(), "different stores should have different hashes");
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Serialization Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void serializationTest(final AddressBookStoreImpl addressBookStoreImpl)
            throws IOException, ConstructableRegistryException {

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");

        final Random random = getRandomPrintSeed();
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random);

        final AddressBookStore store = addressBookStoreImpl.constructor.get();

        for (int i = 0; i < 100; i++) {
            store.add(generator.build().setRound(i));
        }

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final MerkleDataOutputStream out = new MerkleDataOutputStream(byteOut);

        out.writeMerkleTree(testDirectory, store);

        final MerkleDataInputStream in = new MerkleDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final AddressBookStore deserialized = in.readMerkleTree(testDirectory, Integer.MAX_VALUE);
        assertTrue(deserialized.isMutable(), "deserialized store should be mutable");
        assertNotSame(store, deserialized, "deserialization should produce a new object");

        MerkleCryptoFactory.getInstance().digestTreeSync(store);
        MerkleCryptoFactory.getInstance().digestTreeSync(deserialized);

        assertEquals(store.getHash(), deserialized.getHash(), "hashes should match after deserialization");
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Copy Test")
    void copyTest(final AddressBookStoreImpl addressBookStoreImpl) {

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        final Random random = getRandomPrintSeed();
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random);

        final AddressBookStore store = addressBookStoreImpl.constructor.get();

        for (int i = 0; i < 100; i++) {
            store.add(generator.build().setRound(i));
        }

        final AddressBookStore copy = store.copy();

        assertTrue(copy.isMutable(), "copy should always be mutable");
        assertTrue(store.isImmutable(), "original should become immutable for sequential store");

        MerkleCryptoFactory.getInstance().digestTreeSync(store);
        MerkleCryptoFactory.getInstance().digestTreeSync(copy);

        assertEquals(store.getHash(), copy.getHash(), "hashes should match after deserialization");
    }

    @Test
    @DisplayName("Immutability Constraints")
    void immutabilityConstraints() {
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator();

        final AddressBookStore store = new SequentialAddressBookStore();

        // make the store immutable
        store.copy();

        assertTrue(store.isImmutable(), "store should be immutable");

        assertThrows(MutabilityException.class, store::copy, "operation should fail for mutable store");
        assertThrows(
                MutabilityException.class,
                () -> store.add(generator.build()),
                "operation should fail for mutable store");
        assertThrows(MutabilityException.class, () -> store.shiftWindow(0), "operation should fail for mutable store");
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Gap Handling Test")
    void gapHandlingTest(final AddressBookStoreImpl addressBookStoreImpl) throws InterruptedException {
        final Random random = getRandomPrintSeed();

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random);

        final AddressBookStore store = addressBookStoreImpl.constructor.get();
        final Map<Long, AddressBook> expectedAddressBooks = new HashMap<>();

        final int count = 100;

        // Create an address book store with gaps
        for (long round = 0; round < count; round++) {
            if (round != 0 && (round % 5 == 0 || round % 7 == 0)) {
                // gap!
                continue;
            }

            final AddressBook addressBook = generator.build().setRound(round);
            store.add(addressBook);
            expectedAddressBooks.put(round, addressBook);
        }

        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        // Now, do validation
        if (!addressBookStoreImpl.updateAddressBookOnlyAtUpgrade) {
            long nonGapRound = -1;
            for (long round = 0; round < count; round++) {
                final long finalNonGapRound = nonGapRound;
                final long finalRound = round;

                if (round != 0 && (round % 5 == 0 || round % 7 == 0)) {
                    // gap!
                    // Copy and override round to make the hashes equal, only difference should be the round
                    final AddressBook previousAddressBook =
                            expectedAddressBooks.get(finalNonGapRound).copy().setRound(finalRound);
                    final AddressBook currentAddressBook = store.get(finalRound);

                    MerkleCryptoFactory.getInstance().digestTreeSync(previousAddressBook);
                    MerkleCryptoFactory.getInstance().digestTreeSync(currentAddressBook);

                    assertEquals(previousAddressBook.getHash(), currentAddressBook.getHash(), "hashes should match");
                } else {
                    assertSame(
                            expectedAddressBooks.get(finalRound),
                            store.get(finalRound),
                            "unexpected address book discovered");
                    nonGapRound = round;
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Overriding Test")
    void overridingTest(final AddressBookStoreImpl addressBookStoreImpl) throws InterruptedException {
        if (!addressBookStoreImpl.updateAddressBookOnlyAtUpgrade) {
            // skip
            return;
        }

        setUpdateBehavior(true);

        final Random random = getRandomPrintSeed();
        final RandomAddressBookGenerator generator = new RandomAddressBookGenerator(random);

        final AddressBookStore store = addressBookStoreImpl.constructor.get();

        AddressBook expectedOverride = null;

        for (int i = 0; i < 100; i++) {
            final AddressBook addressBook = generator.build().setRound(i);
            store.add(addressBook);

            if (expectedOverride == null) {
                expectedOverride = addressBook;
            } else {
                for (long round = store.getEarliestRound(); round <= store.getLatestRound(); round++) {
                    assertSame(expectedOverride, store.get(round), "should observe overriding address book");
                }
            }

            if (i != 0 && i % 10 == 0) {
                store.updateOverridingAddressBook();
                expectedOverride = addressBook;
            }
        }
    }

    /**
     * This test probes the way that validation interacts with the address book store, and not the validity of the
     * validation code itself.
     */
    @ParameterizedTest
    @MethodSource("addressBookStoreImpls")
    @DisplayName("Validation Test")
    void validationTest(final AddressBookStoreImpl addressBookStoreImpl) throws InterruptedException {
        final Random random = getRandomPrintSeed();
        setUpdateBehavior(addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        final AddressBookStore store = addressBookStoreImpl.constructor.get();

        // Force all nodes to have zero weight
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setCustomWeightGenerator(nodeId -> 0)
                .build();

        assertThrows(
                IllegalStateException.class, () -> store.add(addressBook), "invalid genesis book should be rejected");

        // Add an address book that has a high next ID
        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator().setSize(100).setSequentialIds(true);
        final AddressBook firstAddressBook = generator.build().setRound(0);
        store.add(firstAddressBook);
        assertEquals(1, store.getSize(), "store is the wrong size");

        // Attempting to add an address book store with a low next ID should fail
        final RandomAddressBookGenerator invalidGenerator =
                new RandomAddressBookGenerator().setSize(10).setSequentialIds(true);
        final AddressBook invalidBook =
                invalidGenerator.setSequentialIds(true).build().setRound(1);
        store.add(invalidBook);
        assertEquals(2, store.getSize(), "store is the wrong size");

        if (!addressBookStoreImpl.updateAddressBookOnlyAtUpgrade) {
            // Because the invalid address book should have been rejected, we should expect to see the previous one
            // copied over
            final AddressBook equivalentFirstAddressBook =
                    firstAddressBook.copy().setRound(1);
            CryptographyHolder.get().digestSync(equivalentFirstAddressBook);
            CryptographyHolder.get().digestSync(invalidBook);

            final AddressBook round1AddressBook = store.get(1);
            CryptographyHolder.get().digestSync(round1AddressBook);

            assertEquals(
                    round1AddressBook.getHash(),
                    equivalentFirstAddressBook.getHash(),
                    "previous address book should have been copied");
            assertNotEquals(
                    round1AddressBook.getHash(),
                    invalidBook.getHash(),
                    "invalid address book should not have been used");
        }

        validateStoreConsistency(store, addressBookStoreImpl.updateAddressBookOnlyAtUpgrade);

        // Add an address book with a gap, then add an invalid address book with a gap. Handling of invalid address
        // books should still work in the presence of gaps.
        final AddressBook secondAddressBook = generator.build().setRound(10);
        store.add(secondAddressBook);
        assertEquals(11, store.getSize(), "store is the wrong size");

        final AddressBook secondInvalidAddressBook = invalidGenerator.build().setRound(20);
        store.add(secondInvalidAddressBook);
        assertEquals(21, store.getSize(), "store is the wrong size");

        if (!addressBookStoreImpl.updateAddressBookOnlyAtUpgrade) {
            final AddressBook equivalentSecondAddressBook =
                    secondAddressBook.copy().setRound(20);
            CryptographyHolder.get().digestSync(equivalentSecondAddressBook);
            CryptographyHolder.get().digestSync(secondAddressBook);

            final AddressBook round20AddressBook = store.get(20);
            CryptographyHolder.get().digestSync(round20AddressBook);

            assertEquals(
                    round20AddressBook.getHash(),
                    equivalentSecondAddressBook.getHash(),
                    "previous address book should have been copied");
            assertNotEquals(
                    round20AddressBook.getHash(),
                    secondAddressBook.getHash(),
                    "invalid address book should not have been used");
        }
    }
}
