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

package com.swirlds.platform.state;

import static com.swirlds.platform.state.signed.SignedStateMap.NO_STATE_ROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateMap Tests")
class SignedStateMapTests {

    @Test
    @DisplayName("get() Test")
    void getTest() {

        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(NO_STATE_ROUND, map.getLatestRound());
        assertNull(map.getLatestAndReserve("test").getNullable());

        final SignedState signedState = spy(SignedStateReferenceTests.buildSignedState());
        final long round = 1234;
        doReturn(round).when(signedState).getRound();

        map.put(signedState, "test");
        assertEquals(1, map.getSize(), "unexpected size");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState, wrapper.get());
        }
        assertEquals(signedState.getRound(), map.getLatestRound());

        assertEquals(1, signedState.getReservationCount(), "invalid reference count");

        ReservedSignedState wrapper;

        // Get a reference to a round that is not in the map
        wrapper = map.getAndReserve(0, "test");
        assertNull(wrapper.getNullable());
        wrapper.close();

        wrapper = map.getAndReserve(0, "test");
        assertNull(wrapper.getNullable());
        wrapper.close();

        wrapper = map.getAndReserve(round, "test");
        assertSame(signedState, wrapper.get(), "wrapper returned incorrect object");
        assertEquals(2, signedState.getReservationCount(), "invalid reference count");
        wrapper.close();
        assertEquals(1, signedState.getReservationCount(), "invalid reference count");

        assertEquals(1, map.getSize(), "unexpected size");
    }

    @Test
    @DisplayName("remove() Test")
    void removeTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(NO_STATE_ROUND, map.getLatestRound());
        assertNull(map.getLatestAndReserve("test").getNullable());

        final SignedState signedState = spy(SignedStateReferenceTests.buildSignedState());
        final long round = 1234;
        doReturn(round).when(signedState).getRound();

        map.put(signedState, "test");
        assertEquals(1, map.getSize(), "unexpected size");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState, wrapper.get());
        }

        assertEquals(1, signedState.getReservationCount(), "invalid reference count");

        // remove an element in the map
        map.remove(round);
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(-1, signedState.getReservationCount(), "invalid reference count");
        assertEquals(NO_STATE_ROUND, map.getLatestRound());
        assertNull(map.getLatestAndReserve("test").getNullable());

        // remove an element not in the map, should not throw
        map.remove(0);
    }

    @Test
    @DisplayName("replace() Test")
    void replaceTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final SignedState signedState1 = spy(SignedStateReferenceTests.buildSignedState());
        final long round = 1234;
        doReturn(round).when(signedState1).getRound();

        final SignedState signedState2 = spy(SignedStateReferenceTests.buildSignedState());
        doReturn(round).when(signedState2).getRound();

        map.put(signedState1, "test");
        assertEquals(1, map.getSize(), "unexpected size");
        assertEquals(1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(0, signedState2.getReservationCount(), "invalid reference count");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState1, wrapper.get());
        }
        assertEquals(round, map.getLatestRound());

        map.put(signedState2, "test");
        assertEquals(1, map.getSize(), "unexpected size");
        assertEquals(-1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState2.getReservationCount(), "invalid reference count");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState2, wrapper.get());
        }
        assertEquals(round, map.getLatestRound());
    }

    @Test
    @DisplayName("No Null Values Test")
    void noNullValuesTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        assertThrows(NullPointerException.class, () -> map.put(null, ""), "map should reject a null signed state");
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(NO_STATE_ROUND, map.getLatestRound());
        assertNull(map.getLatestAndReserve("test").getNullable());
    }

    @Test
    @DisplayName("clear() Test")
    void clearTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final SignedState signedState1 = spy(SignedStateReferenceTests.buildSignedState());
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final SignedState signedState2 = spy(SignedStateReferenceTests.buildSignedState());
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final SignedState signedState3 = spy(SignedStateReferenceTests.buildSignedState());
        final long round3 = 1236;
        doReturn(round3).when(signedState3).getRound();

        map.put(signedState1, "test");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState1, wrapper.get());
        }
        assertEquals(signedState1.getRound(), map.getLatestRound());
        map.put(signedState2, "test");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState2, wrapper.get());
        }
        assertEquals(signedState2.getRound(), map.getLatestRound());
        map.put(signedState3, "test");
        try (final ReservedSignedState wrapper = map.getLatestAndReserve("test")) {
            assertSame(signedState3, wrapper.get());
        }
        assertEquals(signedState3.getRound(), map.getLatestRound());

        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState2.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState3.getReservationCount(), "invalid reference count");

        map.clear();
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(-1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(-1, signedState2.getReservationCount(), "invalid reference count");
        assertEquals(-1, signedState3.getReservationCount(), "invalid reference count");
        assertEquals(NO_STATE_ROUND, map.getLatestRound());
        assertNull(map.getLatestAndReserve("test").getNullable());

        assertNull(map.getAndReserve(round1, "test").getNullable(), "state should not be in map");
        assertNull(map.getAndReserve(round2, "test").getNullable(), "state should not be in map");
        assertNull(map.getAndReserve(round3, "test").getNullable(), "state should not be in map");
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(-1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(-1, signedState2.getReservationCount(), "invalid reference count");
        assertEquals(-1, signedState3.getReservationCount(), "invalid reference count");
    }

    @Test
    @DisplayName("Iteration Test")
    void iterationTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final SignedState signedState1 = spy(SignedStateReferenceTests.buildSignedState());
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final SignedState signedState2 = spy(SignedStateReferenceTests.buildSignedState());
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final SignedState signedState3 = spy(SignedStateReferenceTests.buildSignedState());
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1, "test");
        map.put(signedState2, "test");
        map.put(signedState3, "test");
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState2.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState3.getReservationCount(), "invalid reference count");

        final AtomicBoolean state1Found = new AtomicBoolean();
        final AtomicBoolean state2Found = new AtomicBoolean();
        final AtomicBoolean state3Found = new AtomicBoolean();
        map.atomicIteration(iterator -> iterator.forEachRemaining(state -> {
            if (state == signedState1) {
                assertFalse(state1Found.get(), "should only encounter state once");
                state1Found.set(true);
            }
            if (state == signedState2) {
                assertFalse(state2Found.get(), "should only encounter state once");
                state2Found.set(true);
            }
            if (state == signedState3) {
                assertFalse(state3Found.get(), "should only encounter state once");
                state3Found.set(true);
            }
        }));
        assertTrue(state1Found.get(), "state not found");
        assertTrue(state2Found.get(), "state not found");
        assertTrue(state3Found.get(), "state not found");
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState2.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState3.getReservationCount(), "invalid reference count");

        map.atomicIteration(iterator -> iterator.forEachRemaining(state -> {
            if (state == signedState2) {
                iterator.remove();
            }
        }));
        assertEquals(2, map.getSize(), "unexpected size");
        assertEquals(1, signedState1.getReservationCount(), "invalid reference count");
        assertEquals(-1, signedState2.getReservationCount(), "invalid reference count");
        assertEquals(1, signedState3.getReservationCount(), "invalid reference count");
    }
}
