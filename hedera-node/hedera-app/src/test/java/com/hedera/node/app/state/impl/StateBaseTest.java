/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

abstract class StateBaseTest<V> {
    protected static final String STATE_KEY = "TEST_STATE_KEY";

    protected abstract StateBase<Long, V> state();

    protected abstract V newValue(long key);

    @Test
    @DisplayName("The state key must match what was provided in the constructor")
    void testStateKey() {
        assertEquals(STATE_KEY, state().getStateKey());
    }

    @Test
    @DisplayName("`get` of an unknown item returns an empty optional")
    void testNonExistingGet() {
        assertTrue(state().readKeys().isEmpty());
        assertTrue(state().get(1000L).isEmpty());
        assertTrue(state().readKeys().contains(1000L));
    }

    @Test
    @DisplayName("`get` of a known item returns that item")
    void testExistingGet() {
        final var value = newValue(1);
        assertTrue(state().readKeys().isEmpty());
        assertTrue(state().get(1L).isPresent());
        assertEquals(value, state().get(1L).get());
        assertTrue(state().readKeys().contains(1L));
    }

    @Test
    @DisplayName("`contains` of an unknown item returns false")
    void testNonExistingContains() {
        assertTrue(state().readKeys().isEmpty());
        assertFalse(state().contains(1000L));
        assertTrue(state().readKeys().contains(1000L));
    }

    @Test
    @DisplayName("`contains` of a known item returns true")
    void testExistingContains() {
        newValue(2);
        assertTrue(state().readKeys().isEmpty());
        assertTrue(state().contains(2L));
        assertTrue(state().readKeys().contains(2L));
    }

    @Test
    @DisplayName("`reset` clears the readKeys cache")
    void testReset() {
        // Populate some state, and query it, to make sure we have populated the read cache
        final var value1 = newValue(1);
        final var value2 = newValue(2);
        final var s = state();
        assertNotNull(s.get(1L));
        assertTrue(s.get(1L).isPresent());
        assertEquals(value1, s.get(1L).get());
        assertTrue(s.contains(2L));
        assertEquals(2, s.readKeys().size());
        assertTrue(s.readKeys().contains(1L));
        assertTrue(s.readKeys().contains(2L));

        // Reset and verify the cache is empty
        s.reset();
        assertEquals(0, s.readKeys().size());
        assertTrue(s.readKeys().isEmpty());

        // Repopulate the cache by reading again
        assertTrue(s.contains(1L));
        assertNotNull(s.get(2L));
        assertTrue(s.get(2L).isPresent());
        assertEquals(value2, s.get(2L).get());
        assertEquals(2, s.readKeys().size());
        assertTrue(s.readKeys().contains(1L));
        assertTrue(s.readKeys().contains(2L));
    }
}
