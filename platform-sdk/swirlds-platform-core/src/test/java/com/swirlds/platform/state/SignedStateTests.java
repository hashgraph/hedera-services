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

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedState Tests")
class SignedStateTests {
    /**
     * Generate a signed state.
     */
    private SignedState generateSignedState(final Random random, final State state) {
        return new RandomSignedStateGenerator(random).setState(state).build();
    }

    /**
     * Build a mock state.
     *
     * @param reserveCallback this method is called when the State is reserved
     * @param releaseCallback this method is called when the State is released
     */
    private State buildMockState(final Runnable reserveCallback, final Runnable releaseCallback) {

        final State state = spy(new State());
        final SwirldState swirldState = mock(SwirldState.class);

        final PlatformData platformData = new PlatformData();
        final PlatformState platformState = new PlatformState();
        platformState.setPlatformData(platformData);
        platformState.setAddressBook(mock(AddressBook.class));
        when(state.getPlatformState()).thenReturn(platformState);

        when(state.getSwirldState()).thenReturn(swirldState);

        if (reserveCallback != null) {
            doAnswer(invocation -> {
                        reserveCallback.run();
                        return null;
                    })
                    .when(state)
                    .reserve();
        }

        if (releaseCallback != null) {
            doAnswer(invocation -> {
                        releaseCallback.run();
                        return null;
                    })
                    .when(state)
                    .release();
        }

        return state;
    }

    @Test
    @DisplayName("Reservation Test")
    void reservationTest() throws InterruptedException {
        final Random random = new Random();
        final SignedStateGarbageCollector signedStateGarbageCollector =
                new SignedStateGarbageCollector(getStaticThreadManager(), null);
        signedStateGarbageCollector.start();

        final AtomicBoolean reserved = new AtomicBoolean(false);
        final AtomicBoolean released = new AtomicBoolean(false);

        final Thread mainThread = Thread.currentThread();

        final State state = buildMockState(
                () -> {
                    assertFalse(reserved.get(), "should only be reserved once");
                    reserved.set(true);
                },
                () -> {
                    assertFalse(released.get(), "should only be released once");
                    assertNotSame(mainThread, Thread.currentThread(), "release should happen on background thread");
                    released.set(true);
                });

        final SignedState signedState = generateSignedState(random, state);
        signedState.setGarbageCollector(signedStateGarbageCollector);

        final ReservedSignedState reservedSignedState;
        reservedSignedState = signedState.reserve("test");

        // Nothing should happen during this sleep, but give the background thread time to misbehave if it wants to
        MILLISECONDS.sleep(10);

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(released.get(), "state should not be deleted");

        // Taking reservations should have no impact as long as we don't delete all of them
        final List<ReservedSignedState> reservations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            reservations.add(signedState.reserve("test"));
        }
        for (int i = 0; i < 10; i++) {
            reservations.get(i).close();
        }

        // Nothing should happen during this sleep, but give the background thread time to misbehave if it wants to
        MILLISECONDS.sleep(10);

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(released.get(), "state should not be deleted");

        reservedSignedState.close();

        assertThrows(
                ReferenceCountException.class,
                () -> signedState.reserve("test"),
                "should not be able to reserve after full release");

        assertEventuallyTrue(released::get, Duration.ofSeconds(1), "state should eventually be released");

        signedStateGarbageCollector.stop();
    }

    @Test
    @DisplayName("Finite Deletion Queue Test")
    void finiteDeletionQueueTest() throws InterruptedException {
        final Random random = new Random();
        final SignedStateGarbageCollector signedStateGarbageCollector =
                new SignedStateGarbageCollector(getStaticThreadManager(), null);
        signedStateGarbageCollector.start();

        // Deletion thread will hold one after it is removed from the queue, hence the +1
        final int capacity = SignedStateGarbageCollector.DELETION_QUEUE_CAPACITY + 1;

        final AtomicInteger deletionCount = new AtomicInteger();
        final CountDownLatch deletionBlocker = new CountDownLatch(1);

        final Thread mainThread = Thread.currentThread();

        for (int i = 0; i < capacity; i++) {
            final State state = buildMockState(null, () -> {
                abortAndThrowIfInterrupted(deletionBlocker::await, "unexpected interruption");
                deletionCount.getAndIncrement();
            });

            final SignedState signedState = generateSignedState(random, state);
            signedState.setGarbageCollector(signedStateGarbageCollector);
            signedState.reserve("test").close();
        }

        // At this point in time, the signed state deletion queue should be entirely filled up.
        // Deleting one more signed state should cause the deletion to happen on the current thread.

        final State state = buildMockState(null, () -> {
            assertSame(mainThread, Thread.currentThread(), "called on wrong thread");
            deletionCount.getAndIncrement();
        });

        final SignedState signedState = generateSignedState(random, state);
        signedState.reserve("test").close();

        assertEquals(1, deletionCount.get());

        // Nothing should happen during this sleep, but give the background thread time to misbehave if it wants to
        MILLISECONDS.sleep(10);

        assertEquals(1, deletionCount.get());

        deletionBlocker.countDown();
        assertEventuallyEquals(
                capacity + 1, deletionCount::get, Duration.ofSeconds(1), "all states should eventually be deleted");
        signedStateGarbageCollector.stop();
    }

    /**
     * Although this lifecycle is not expected in a real system, it's a nice for the sake of completeness to ensure that
     * a signed state can clean itself up without having an associated garbage collection thread.
     */
    @Test
    @DisplayName("No Garbage Collector Test")
    void noGarbageCollectorTest() {
        final Random random = new Random();

        final AtomicBoolean reserved = new AtomicBoolean(false);
        final AtomicBoolean archived = new AtomicBoolean(false);
        final AtomicBoolean released = new AtomicBoolean(false);

        final Thread mainThread = Thread.currentThread();

        final State state = buildMockState(
                () -> {
                    assertFalse(reserved.get(), "should only be reserved once");
                    reserved.set(true);
                },
                () -> {
                    assertFalse(released.get(), "should only be released once");
                    assertSame(mainThread, Thread.currentThread(), "release should happen on main thread");
                    released.set(true);
                });

        final SignedState signedState = generateSignedState(random, state);

        final ReservedSignedState reservedSignedState = signedState.reserve("test");

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(archived.get(), "state should not be archived");
        assertFalse(released.get(), "state should not be deleted");

        // Taking reservations should have no impact as long as we don't delete all of them
        final List<ReservedSignedState> reservations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            reservations.add(signedState.reserve("test"));
        }
        for (int i = 0; i < 10; i++) {
            reservations.get(i).close();
        }

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(archived.get(), "state should not be archived");
        assertFalse(released.get(), "state should not be deleted");

        reservedSignedState.close();

        assertThrows(
                ReferenceCountException.class,
                () -> signedState.reserve("test"),
                "should not be able to reserve after full release");

        assertEventuallyTrue(released::get, Duration.ofSeconds(1), "state should eventually be released");
        assertFalse(archived.get(), "state should not be archived");
    }

    /**
     * There used to be a bug (now fixed) that would case this test to fail.
     */
    @Test
    @DisplayName("Alternate Constructor Reservations Test")
    void alternateConstructorReservationsTest() {
        final State state = spy(new State());
        final PlatformState platformState = mock(PlatformState.class);
        when(state.getPlatformState()).thenReturn(platformState);
        final PlatformData platformData = mock(PlatformData.class);
        when(platformState.getPlatformData()).thenReturn(platformData);
        when(platformData.getRound()).thenReturn(0L);
        final SignedState signedState =
                new SignedState(TestPlatformContextBuilder.create().build(), state, "test");

        assertFalse(state.isDestroyed(), "state should not yet be destroyed");

        signedState.reserve("test").close();

        assertTrue(state.isDestroyed(), "state should now be destroyed");
    }
}
