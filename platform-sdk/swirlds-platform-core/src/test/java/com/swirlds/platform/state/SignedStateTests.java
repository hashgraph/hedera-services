/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import org.junit.jupiter.api.DisplayName;

@DisplayName("SignedState Tests")
class SignedStateTests {
    // TODO
    //    /**
    //     * Generate a signed state. FUTURE WORK: replace this with the utility added to 0.29.0
    //     */
    //    private SignedState generateSignedState(final Random random, final State state) {
    //        return new SignedState(state, random.nextBoolean());
    //    }
    //
    //    /**
    //     * Build a mock state.
    //     *
    //     * @param reserveCallback this method is called when the State is reserved
    //     * @param releaseCallback this method is called when the State is released
    //     */
    //    private State buildMockState(final Runnable reserveCallback, final Runnable releaseCallback) {
    //
    //        final State state = mock(State.class);
    //        final SwirldState swirldState = mock(SwirldState.class);
    //
    //        final PlatformData platformData = new PlatformData();
    //        final PlatformState platformState = new PlatformState();
    //        platformState.setPlatformData(platformData);
    //        platformState.setAddressBook(mock(AddressBook.class));
    //        when(state.getPlatformState()).thenReturn(platformState);
    //
    //        when(state.getSwirldState()).thenReturn(swirldState);
    //
    //        if (reserveCallback != null) {
    //            doAnswer(invocation -> {
    //                        reserveCallback.run();
    //                        return null;
    //                    })
    //                    .when(state)
    //                    .reserve();
    //        }
    //
    //        if (releaseCallback != null) {
    //            doAnswer(invocation -> {
    //                        releaseCallback.run();
    //                        return null;
    //                    })
    //                    .when(state)
    //                    .release();
    //        }
    //
    //        return state;
    //    }
    //
    //    @ParameterizedTest
    //    @ValueSource(booleans = {true, false})
    //    @DisplayName("Reservation Test")
    //    void reservationTest(final boolean explicit) throws InterruptedException {
    //        final Random random = new Random();
    //        final SignedStateGarbageCollector signedStateGarbageCollector =
    //                new SignedStateGarbageCollector(getStaticThreadManager(), null);
    //        signedStateGarbageCollector.start();
    //
    //        final AtomicBoolean reserved = new AtomicBoolean(false);
    //        final AtomicBoolean released = new AtomicBoolean(false);
    //
    //        final Thread mainThread = Thread.currentThread();
    //
    //        final State state = buildMockState(
    //                () -> {
    //                    assertFalse(reserved.get(), "should only be reserved once");
    //                    reserved.set(true);
    //                },
    //                () -> {
    //                    assertFalse(released.get(), "should only be released once");
    //                    assertNotSame(mainThread, Thread.currentThread(), "release should happen on background
    // thread");
    //                    released.set(true);
    //                });
    //
    //        final SignedState signedState = generateSignedState(random, state);
    //        signedState.setGarbageCollector(signedStateGarbageCollector);
    //
    //        if (explicit) {
    //            signedState.reserve();
    //        }
    //
    //        // Nothing should happen during this sleep, but give the background thread time to misbehave if it wants
    // to
    //        MILLISECONDS.sleep(10);
    //
    //        assertTrue(reserved.get(), "State should have been reserved");
    //        assertFalse(released.get(), "state should not be deleted");
    //
    //        if (explicit) {
    //            // Taking reservations should have no impact as long as we don't delete all of them
    //            for (int i = 0; i < 10; i++) {
    //                signedState.reserve();
    //            }
    //            for (int i = 0; i < 10; i++) {
    //                signedState.release();
    //            }
    //        }
    //
    //        // Nothing should happen during this sleep, but give the background thread time to misbehave if it wants
    // to
    //        MILLISECONDS.sleep(10);
    //
    //        assertTrue(reserved.get(), "State should have been reserved");
    //        assertFalse(released.get(), "state should not be deleted");
    //
    //        signedState.release();
    //
    //        assertThrows(
    //                ReferenceCountException.class,
    //                signedState::reserve,
    //                "should not be able to reserve after full release");
    //        assertThrows(ReferenceCountException.class, signedState::release, "should not be able to release again");
    //
    //        assertEventuallyTrue(released::get, Duration.ofSeconds(1), "state should eventually be released");
    //
    //        signedStateGarbageCollector.stop();
    //    }
    //
    //    @Test
    //    @DisplayName("Finite Deletion Queue Test")
    //    void finiteDeletionQueueTest() throws InterruptedException {
    //        final Random random = new Random();
    //        final SignedStateGarbageCollector signedStateGarbageCollector =
    //                new SignedStateGarbageCollector(getStaticThreadManager(), null);
    //        signedStateGarbageCollector.start();
    //
    //        // Deletion thread will hold one after it is removed from the queue, hence the +1
    //        final int capacity = SignedStateGarbageCollector.DELETION_QUEUE_CAPACITY + 1;
    //
    //        final AtomicInteger deletionCount = new AtomicInteger();
    //        final CountDownLatch deletionBlocker = new CountDownLatch(1);
    //
    //        final Thread mainThread = Thread.currentThread();
    //
    //        for (int i = 0; i < capacity; i++) {
    //            final State state = buildMockState(null, () -> {
    //                abortAndThrowIfInterrupted(deletionBlocker::await, "unexpected interruption");
    //                deletionCount.getAndIncrement();
    //            });
    //
    //            final SignedState signedState = generateSignedState(random, state);
    //            signedState.setGarbageCollector(signedStateGarbageCollector);
    //            signedState.release();
    //        }
    //
    //        // At this point in time, the signed state deletion queue should be entirely filled up.
    //        // Deleting one more signed state should cause the deletion to happen on the current thread.
    //
    //        final State state = buildMockState(null, () -> {
    //            assertSame(mainThread, Thread.currentThread(), "called on wrong thread");
    //            deletionCount.getAndIncrement();
    //        });
    //
    //        final SignedState signedState = generateSignedState(random, state);
    //        signedState.release();
    //
    //        assertEquals(1, deletionCount.get());
    //
    //        // Nothing should happen during this sleep, but give the background thread time to misbehave if it wants
    // to
    //        MILLISECONDS.sleep(10);
    //
    //        assertEquals(1, deletionCount.get());
    //
    //        deletionBlocker.countDown();
    //        assertEventuallyEquals(
    //                capacity + 1, deletionCount::get, Duration.ofSeconds(1), "all states should eventually be
    // deleted");
    //        signedStateGarbageCollector.stop();
    //    }
    //
    //    /**
    //     * Although this lifecycle is not expected in a real system, it's a nice for the sake of completeness to
    // ensure that
    //     * a signed state can clean itself up without having an associated garbage collection thread.
    //     */
    //    @Test
    //    @DisplayName("No Garbage Collector Test")
    //    void noGarbageCollectorTest() {
    //        final Random random = new Random();
    //
    //        final AtomicBoolean reserved = new AtomicBoolean(false);
    //        final AtomicBoolean archived = new AtomicBoolean(false);
    //        final AtomicBoolean released = new AtomicBoolean(false);
    //
    //        final Thread mainThread = Thread.currentThread();
    //
    //        final State state = buildMockState(
    //                () -> {
    //                    assertFalse(reserved.get(), "should only be reserved once");
    //                    reserved.set(true);
    //                },
    //                () -> {
    //                    assertFalse(released.get(), "should only be released once");
    //                    assertSame(mainThread, Thread.currentThread(), "release should happen on main thread");
    //                    released.set(true);
    //                });
    //
    //        final SignedState signedState = generateSignedState(random, state);
    //
    //        signedState.reserve();
    //
    //        assertTrue(reserved.get(), "State should have been reserved");
    //        assertFalse(archived.get(), "state should not be archived");
    //        assertFalse(released.get(), "state should not be deleted");
    //
    //        // Taking reservations should have no impact as long as we don't delete all of them
    //        for (int i = 0; i < 10; i++) {
    //            signedState.reserve();
    //        }
    //        for (int i = 0; i < 10; i++) {
    //            signedState.release();
    //        }
    //
    //        assertTrue(reserved.get(), "State should have been reserved");
    //        assertFalse(archived.get(), "state should not be archived");
    //        assertFalse(released.get(), "state should not be deleted");
    //
    //        signedState.release();
    //
    //        assertThrows(
    //                ReferenceCountException.class,
    //                signedState::reserve,
    //                "should not be able to reserve after full release");
    //        assertThrows(ReferenceCountException.class, signedState::release, "should not be able to release again");
    //
    //        assertEventuallyTrue(released::get, Duration.ofSeconds(1), "state should eventually be released");
    //        assertFalse(archived.get(), "state should not be archived");
    //    }
    //
    //    /**
    //     * There used to be a bug (now fixed) that would case this test to fail.
    //     */
    //    @Test
    //    @DisplayName("Alternate Constructor Reservations Test")
    //    void alternateConstructorReservationsTest() {
    //        final State state = new State();
    //        final SignedState signedState = new SignedState(state);
    //
    //        assertFalse(state.isDestroyed(), "state should not yet be destroyed");
    //
    //        signedState.reserve();
    //
    //        signedState.release();
    //
    //        assertTrue(state.isDestroyed(), "state should now be destroyed");
    //    }
}
