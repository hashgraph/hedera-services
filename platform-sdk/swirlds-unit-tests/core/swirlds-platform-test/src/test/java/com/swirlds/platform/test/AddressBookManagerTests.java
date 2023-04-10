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

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.completeBeforeTimeout;
import static com.swirlds.common.test.AssertionUtils.throwBeforeTimeout;
import static com.swirlds.common.test.RandomAddressBookGenerator.HashStrategy.REAL_HASH;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static com.swirlds.platform.test.AddressBookStoreTests.setUpdateBehavior;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.address.AddressBookManager;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.platform.state.address.AddressBookManagerImpl;
import com.swirlds.platform.state.address.AddressBookStore;
import com.swirlds.platform.state.address.MutableAddressBookManager;
import com.swirlds.platform.state.address.SequentialAddressBookStore;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("AddressBookManager Tests")
class AddressBookManagerTests {

    @BeforeAll
    static void beforeAll() {
        new TestConfigBuilder().getOrCreateConfig();
    }

    /**
     * Create an address book store that can be used to initialize an address book manager.
     */
    private AddressBookStore buildInitialStore(
            final RandomAddressBookGenerator generator, final long firstRound, final int size) {

        final AddressBookStore addressBookStore = new SequentialAddressBookStore();

        for (long round = firstRound; round < firstRound + size; round++) {
            final AddressBook addressBook = generator.build().setRound(round);
            addressBookStore.add(addressBook);
        }

        return addressBookStore;
    }

    /**
     * Verify behavior when an address book from a stale index is requested.
     */
    private void verifyStaleRound(final AddressBookManager manager, final long round) throws InterruptedException {

        throwBeforeTimeout(
                IllegalStateException.class,
                () -> {
                    try {
                        manager.get(round);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofSeconds(1),
                "expected round to be stale (round=" + round + ")");
    }

    /**
     * Verify behavior when an address book from a round that is currently available is accessed.
     */
    private void verifyAvailableRound(
            final AddressBookManager manager, final long round, final AddressBook expectedValue)
            throws InterruptedException {

        final AddressBook addressBook = completeBeforeTimeout(
                () -> {
                    try {
                        return manager.get(round);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofSeconds(1),
                String.format("expected address book to be ready (round=%s)", round));

        assertEquals(
                expectedValue.getHash(), addressBook.getHash(), String.format("unexpected value (round=%s)", round));
    }

    /**
     * Verify that an address book eventually becomes available, but only at the expected time.
     */
    private void verifyFutureAvailability(
            final AddressBookManager manager,
            final long round,
            final AddressBook expectedValue,
            final AtomicLong currentLatestRound,
            final AtomicInteger expectedOperations,
            final AtomicInteger operationsCompleted)
            throws InterruptedException {

        expectedOperations.getAndIncrement();

        final CountDownLatch latch = new CountDownLatch(1);

        getStaticThreadManager().newThreadConfiguration()
                .setComponent("address-book-manager-test")
                .setThreadName("verify-eventual-get-" + round)
                .setExceptionHandler((final Thread thread, final Throwable exception) -> {
                    System.out.println("problem while attempting to verify future completion of round " + round);
                    exception.printStackTrace();
                })
                .setRunnable(() -> {
                    final AddressBook value;
                    try {
                        latch.countDown();
                        value = manager.get(round);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    if (value.getHash() == null) {
                        CryptographyHolder.get().digestSync(value);
                    }

                    assertEquals(value.getHash(), expectedValue.getHash(), "values do not match (round=" + round + ")");
                    assertTrue(
                            currentLatestRound.get() >= round, "address book available too soon (index=" + round + ")");
                    operationsCompleted.getAndIncrement();
                })
                .build(true);

        latch.await();
        // At this point in time, we know that the thead has started, but it's still possible that the thread
        // is not yet blocking on manager.get().
    }

    /**
     * Verify that the address book for a round eventually becomes cancelled, but only at the expected time.
     */
    private void verifyFutureCancellation(
            final AddressBookManager manager,
            final long round,
            final AtomicLong currentLatestRound,
            final AtomicInteger expectedOperations,
            final AtomicInteger operationsCompleted)
            throws InterruptedException {

        expectedOperations.getAndAdd(1);

        final CountDownLatch latch = new CountDownLatch(1);

        getStaticThreadManager().newThreadConfiguration()
                .setComponent("address-book-manager-test")
                .setThreadName("verify-eventual-cancellation-" + round)
                .setExceptionHandler((final Thread thread, final Throwable exception) -> {
                    System.out.println("problem while attempting to verify future completion of round " + round);
                    exception.printStackTrace();
                })
                .setRunnable(() -> {
                    latch.countDown();
                    assertThrows(
                            CancellationException.class,
                            () -> manager.get(round),
                            "expected round to be cancelled (round=" + round + ")");

                    assertTrue(currentLatestRound.get() >= round, "round completed too soon (index=" + round + ")");
                    operationsCompleted.getAndIncrement();
                })
                .build(true);

        latch.await();
        // At this point in time, we know that the thead has started, but it's still possible that the thread
        // is not yet blocking on manager.get().
    }

    @Test
    @DisplayName("Standard Operation Test")
    @Tag(TIME_CONSUMING)
    void standardOperationTest() throws InterruptedException {
        setUpdateBehavior(true);
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator(random).setSize(8).setHashStrategy(REAL_HASH);

        final int addressBooksInState = 26;
        final int roundFarInThePast = -100;
        final int numberOfAddressBooksToInsert = 100;
        final int roundsToCancel = 50;
        final int roundAfterLastCancellation = numberOfAddressBooksToInsert + roundsToCancel;

        final AddressBookStore initialStore = buildInitialStore(generator, 0, addressBooksInState);
        final MutableAddressBookManager manager = new AddressBookManagerImpl(initialStore, addressBooksInState);

        assertSame(initialStore.getLatest(), manager.getLatest(), "latest should match latest in store");

        // Rounds that come before the initial store should not be present
        for (long round = roundFarInThePast; round < 0; round++) {
            verifyStaleRound(manager, round);
        }

        // Rounds in the store should be present
        for (long round = 0; round < addressBooksInState; round++) {
            verifyAvailableRound(manager, round, manager.get(round));
        }

        final AtomicLong currentLatestRound = new AtomicLong(addressBooksInState - 1);
        final AtomicInteger expectedOperations = new AtomicInteger();
        final AtomicInteger operationsCompleted = new AtomicInteger();

        // Create some address books that will be inserted at a future time
        final Map<Long, AddressBook> futureAddressBooks = new HashMap<>();
        final Set<Long> gapRounds = new HashSet<>();
        for (long round = addressBooksInState; round < numberOfAddressBooksToInsert; round++) {
            final AddressBook addressBook;
            if (round != addressBooksInState && round != numberOfAddressBooksToInsert - 1 && random.nextBoolean()) {
                // this is a gap, copy forward the previous address book
                addressBook = futureAddressBooks.get(round - 1).copy().setRound(round);
                CryptographyHolder.get().digestSync(addressBook);
                gapRounds.add(round);
            } else {
                // not a gap
                addressBook = generator.build().setRound(round);
            }
            futureAddressBooks.put(round, addressBook);
        }

        // Get some threads waiting on future address books
        for (long round = addressBooksInState; round < numberOfAddressBooksToInsert; round++) {
            verifyFutureAvailability(
                    manager,
                    round,
                    futureAddressBooks.get(round),
                    currentLatestRound,
                    expectedOperations,
                    operationsCompleted);
        }

        // Rounds 100-149 will be cancelled
        for (long round = numberOfAddressBooksToInsert; round < roundAfterLastCancellation; round++) {
            verifyFutureCancellation(manager, round, currentLatestRound, expectedOperations, operationsCompleted);
        }

        // This sleep is a necessary evil, as there is a small chance that one of the background threads
        // that is expecting to eventually find specific data has not yet called manager.get(). Adding
        // new address books very quickly before manager.get() is called may result in an address book being stale,
        // which is undesirable for this test (since deterministic behavior is a lot easier to validate).
        MILLISECONDS.sleep(10);

        // Make the future address books available
        for (long round = addressBooksInState; round < numberOfAddressBooksToInsert; round++) {
            if (!gapRounds.contains(round)) {
                currentLatestRound.set(round);
                manager.setMostRecentAddressBook(futureAddressBooks.get(round));
                assertSame(
                        futureAddressBooks.get(round),
                        manager.getLatest(),
                        "latest should reflect the address book that was just added");
            }
        }

        // Old rounds should no longer be present
        for (long round = 0; round < numberOfAddressBooksToInsert - addressBooksInState; round++) {
            verifyStaleRound(manager, round);
        }

        // Fast-forward to a store that starts at round 150
        currentLatestRound.set(roundAfterLastCancellation + addressBooksInState);
        final AddressBookStore fastForwardStore =
                buildInitialStore(generator, roundAfterLastCancellation, addressBooksInState);
        manager.fastForwardToAddressBook(fastForwardStore);

        // Old rounds should no longer be present
        for (long round = numberOfAddressBooksToInsert; round < roundAfterLastCancellation; round++) {
            verifyStaleRound(manager, round);
        }

        assertEventuallyEquals(
                expectedOperations.get(),
                operationsCompleted::get,
                Duration.ofSeconds(1),
                "all address books should have become available");
    }

    @Test
    @DisplayName("Initialization With No Runtime Update Test")
    void initializationWithNoRuntimeUpdateTest() throws InterruptedException {
        setUpdateBehavior(true);
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator(random).setSize(8).setHashStrategy(REAL_HASH);

        final int addressBooksInState = 26;
        final int roundFarInThePast = -100;

        final AddressBookStore initialStore = buildInitialStore(generator, 0, addressBooksInState);
        final MutableAddressBookManager manager = new AddressBookManagerImpl(initialStore, addressBooksInState);

        assertSame(initialStore.getLatest(), manager.getLatest(), "latest should match latest in store");
        assertSame(
                initialStore.getEarliest(),
                manager.getLatest(),
                "in this mode of operation, the store always returns the same address book");

        // Rounds that come before the initial store should not be present
        for (long round = roundFarInThePast; round < 0; round++) {
            verifyStaleRound(manager, round);
        }

        // Rounds in the store should be present
        for (long round = 0; round < addressBooksInState; round++) {
            verifyAvailableRound(manager, round, manager.get(round));
        }
    }

    @Test
    @DisplayName("Post Genesis Initialization Test")
    void postGenesisInitializationTest() throws InterruptedException {
        setUpdateBehavior(true);
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator(random).setSize(8).setHashStrategy(REAL_HASH);

        final int addressBooksInState = 26;
        final int firstRound = 100;

        final AddressBookStore initialStore = buildInitialStore(generator, firstRound, addressBooksInState);
        final MutableAddressBookManager manager = new AddressBookManagerImpl(initialStore, addressBooksInState);

        assertSame(initialStore.getLatest(), manager.getLatest(), "latest should match latest in store");

        // Rounds that come before the initial store should not be present
        for (long round = 0; round < firstRound; round++) {
            verifyStaleRound(manager, round);
        }

        // Rounds in the store should be present
        for (long round = firstRound; round < firstRound + addressBooksInState; round++) {
            verifyAvailableRound(manager, round, manager.get(round));
        }
    }

    @Test
    @DisplayName("Initialization With Large Store Test")
    void initializationWithLargeStoreTest() throws InterruptedException {
        setUpdateBehavior(true);
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator(random).setSize(8).setHashStrategy(REAL_HASH);

        final int addressBooksInState = 26;
        final int size = 100;
        final int roundFarInThePast = -100;

        final AddressBookStore initialStore = buildInitialStore(generator, 0, size);
        final MutableAddressBookManager manager = new AddressBookManagerImpl(initialStore, addressBooksInState);

        assertSame(initialStore.getLatest(), manager.getLatest(), "latest should match latest in store");

        // Old rounds should not be present. Some of those rounds may have been in the initial store.
        for (long round = roundFarInThePast; round < size - addressBooksInState; round++) {
            verifyStaleRound(manager, round);
        }

        // Rounds in the store should be present
        for (long round = size - addressBooksInState; round < size; round++) {
            verifyAvailableRound(manager, round, manager.get(round));
        }
    }

    @Test
    @DisplayName("Initialization With Small Store Test")
    void initializationWithSmallStoreTest() throws InterruptedException {
        final int addressBooksInState = 26;
        final int size = 10;
        final int firstRound = 0;

        setUpdateBehavior(true);
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator(random).setSize(8).setHashStrategy(REAL_HASH);

        final AddressBookStore initialStore = buildInitialStore(generator, firstRound, size);

        assertThrows(
                IllegalArgumentException.class,
                () -> new AddressBookManagerImpl(initialStore, addressBooksInState),
                "should not be able to instantiate a manager from a store if there are too few entries");
    }

    @Test
    @DisplayName("Fast Forward Small Store Test")
    void fastForwardSmallStoreTest() throws InterruptedException {
        final int addressBooksInState = 26;
        final int size = 10;
        final int firstRound = 100;

        setUpdateBehavior(true);
        final Random random = getRandomPrintSeed();

        final RandomAddressBookGenerator generator =
                new RandomAddressBookGenerator(random).setSize(8).setHashStrategy(REAL_HASH);

        final AddressBookStore initialStore = buildInitialStore(generator, 0, addressBooksInState);
        final MutableAddressBookManager manager = new AddressBookManagerImpl(initialStore, addressBooksInState);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.fastForwardToAddressBook(buildInitialStore(generator, firstRound, size)),
                "should not be able to fast forward to undersized store");
    }
}
