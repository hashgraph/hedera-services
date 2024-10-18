/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedState Tests")
class SignedStateTests {

    /**
     * Generate a signed state.
     */
    private SignedState generateSignedState(final Random random, final MerkleStateRoot state) {
        return new RandomSignedStateGenerator(random).setState(state).build();
    }

    /**
     * Build a mock state.
     *
     * @param reserveCallback this method is called when the State is reserved
     * @param releaseCallback this method is called when the State is released
     */
    private MerkleStateRoot buildMockState(final Runnable reserveCallback, final Runnable releaseCallback) {
        final MerkleStateRoot state = spy(new MerkleStateRoot(
                FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major())));

        final PlatformStateModifier platformState = new PlatformState();
        platformState.setAddressBook(mock(AddressBook.class));
        when(state.getWritablePlatformState()).thenReturn(platformState);
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

        final AtomicBoolean reserved = new AtomicBoolean(false);
        final AtomicBoolean released = new AtomicBoolean(false);

        final MerkleStateRoot state = buildMockState(
                () -> {
                    assertFalse(reserved.get(), "should only be reserved once");
                    reserved.set(true);
                },
                () -> {
                    assertFalse(released.get(), "should only be released once");
                    released.set(true);
                });

        final SignedState signedState = generateSignedState(random, state);

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

        final MerkleStateRoot state = buildMockState(
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
        final MerkleRoot state = spy(new MerkleStateRoot(
                FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major())));
        final PlatformStateModifier platformState = mock(PlatformStateModifier.class);
        state.initPlatformState();
        when(state.getReadablePlatformState()).thenReturn(platformState);
        when(platformState.getRound()).thenReturn(0L);
        final SignedState signedState = new SignedState(
                TestPlatformContextBuilder.create().build(),
                mock(SignatureVerifier.class),
                state,
                "test",
                false,
                false,
                false);

        assertFalse(state.isDestroyed(), "state should not yet be destroyed");

        signedState.reserve("test").close();

        assertTrue(state.isDestroyed(), "state should now be destroyed");
    }
}
