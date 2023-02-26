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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateMap Tests")
class SignedStateMapTests {

    @Test
    @DisplayName("get() Test")
    void getTest() {

        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger references = new AtomicInteger();

        final AtomicInteger referencesHeldByMap = references;

        final SignedState signedState = SignedStateReferenceTests.buildSignedState(references);
        final long round = 1234;
        doReturn(round).when(signedState).getRound();

        map.put(signedState);
        assertEquals(1, map.getSize(), "unexpected size");

        assertEquals(1, referencesHeldByMap.get(), "invalid reference count");

        // Subtract away the reference held by map, makes logic below simpler
        referencesHeldByMap.getAndDecrement();

        AutoCloseableWrapper<SignedState> wrapper;

        // Get a reference to a round that is not in the map
        wrapper = map.get(0);
        assertNull(wrapper.get());
        wrapper.close();

        wrapper = map.get(0);
        assertNull(wrapper.get());
        wrapper.close();

        wrapper = map.get(round);
        assertSame(signedState, wrapper.get(), "wrapper returned incorrect object");
        assertEquals(1, references.get(), "invalid reference count");
        wrapper.close();
        assertEquals(0, references.get(), "invalid reference count");

        assertEquals(1, map.getSize(), "unexpected size");
    }

    @Test
    @DisplayName("remove() Test")
    void removeTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger references = new AtomicInteger();

        final AtomicInteger referencesHeldByMap = references;

        final SignedState signedState = SignedStateReferenceTests.buildSignedState(references);
        final long round = 1234;
        doReturn(round).when(signedState).getRound();

        map.put(signedState);
        assertEquals(1, map.getSize(), "unexpected size");

        assertEquals(1, referencesHeldByMap.get(), "invalid reference count");

        // remove an element in the map
        map.remove(round);
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap.get(), "invalid reference count");

        // remove an element not in the map, should not throw
        map.remove(0);
    }

    @Test
    @DisplayName("replace() Test")
    void replaceTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger references1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = references1;

        final SignedState signedState1 = SignedStateReferenceTests.buildSignedState(references1);
        final long round = 1234;
        doReturn(round).when(signedState1).getRound();

        final AtomicInteger references2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = references2;

        final SignedState signedState2 = SignedStateReferenceTests.buildSignedState(references2);
        doReturn(round).when(signedState2).getRound();

        map.put(signedState1);
        assertEquals(1, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");

        map.put(signedState2);
        assertEquals(1, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
    }

    @Test
    @DisplayName("No Null Values Test")
    void noNullValuesTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        assertThrows(IllegalArgumentException.class, () -> map.put(null), "map should reject a null signed state");
        assertEquals(0, map.getSize(), "unexpected size");
    }

    @Test
    @DisplayName("clear() Test")
    void clearTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger references1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = references1;

        final SignedState signedState1 = SignedStateReferenceTests.buildSignedState(references1);
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final AtomicInteger references2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = references2;

        final SignedState signedState2 = SignedStateReferenceTests.buildSignedState(references2);
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final AtomicInteger references3 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap3 = references3;

        final SignedState signedState3 = SignedStateReferenceTests.buildSignedState(references3);
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1);
        map.put(signedState2);
        map.put(signedState3);
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");

        map.clear();
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap3.get(), "invalid reference count");

        assertNull(map.get(round1).get(), "state should not be in map");
        assertNull(map.get(round2).get(), "state should not be in map");
        assertNull(map.get(round3).get(), "state should not be in map");
        assertEquals(0, map.getSize(), "unexpected size");
        assertEquals(0, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap3.get(), "invalid reference count");
    }

    @Test
    @DisplayName("Iteration Test")
    void iterationTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger references1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = references1;

        final SignedState signedState1 = SignedStateReferenceTests.buildSignedState(references1);
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final AtomicInteger references2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = references2;

        final SignedState signedState2 = SignedStateReferenceTests.buildSignedState(references2);
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final AtomicInteger references3 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap3 = references3;

        final SignedState signedState3 = SignedStateReferenceTests.buildSignedState(references3);
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1);
        map.put(signedState2);
        map.put(signedState3);
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");

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
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");

        map.atomicIteration(iterator -> iterator.forEachRemaining(state -> {
            if (state == signedState2) {
                iterator.remove();
            }
        }));
        assertEquals(2, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(0, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");
    }

    @Test
    @DisplayName("find() Test")
    void findTest() {
        final SignedStateMap map = new SignedStateMap();
        assertEquals(0, map.getSize(), "unexpected size");

        final AtomicInteger references1 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap1 = references1;

        final SignedState signedState1 = SignedStateReferenceTests.buildSignedState(references1);
        final long round1 = 1234;
        doReturn(round1).when(signedState1).getRound();

        final AtomicInteger references2 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap2 = references2;

        final SignedState signedState2 = SignedStateReferenceTests.buildSignedState(references2);
        final long round2 = 1235;
        doReturn(round2).when(signedState2).getRound();

        final AtomicInteger references3 = new AtomicInteger();

        final AtomicInteger referencesHeldByMap3 = references3;

        final SignedState signedState3 = SignedStateReferenceTests.buildSignedState(references3);
        final long round3 = 1236;
        doReturn(round3).when(signedState2).getRound();

        map.put(signedState1);
        map.put(signedState2);
        map.put(signedState3);
        assertEquals(3, map.getSize(), "unexpected size");
        assertEquals(1, referencesHeldByMap1.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap2.get(), "invalid reference count");
        assertEquals(1, referencesHeldByMap3.get(), "invalid reference count");

        try (final AutoCloseableWrapper<SignedState> foundState1 = map.find(ss -> ss == signedState1)) {
            assertEquals(signedState1, foundState1.get(), "incorrect state found");
            assertEquals(2, references1.get(), "invalid reference count");
        }

        try (final AutoCloseableWrapper<SignedState> foundState2 = map.find(ss -> ss == signedState2)) {
            assertEquals(signedState2, foundState2.get(), "incorrect state found");
            assertEquals(2, references2.get(), "invalid reference count");
        }

        try (final AutoCloseableWrapper<SignedState> foundState3 = map.find(ss -> ss == signedState3)) {
            assertEquals(signedState3, foundState3.get(), "incorrect state found");
            assertEquals(2, references3.get(), "invalid reference count");
        }
    }
}
