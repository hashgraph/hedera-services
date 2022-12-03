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
package com.hedera.node.app.state;

import static org.junit.jupiter.api.Assertions.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the base class for all states. Most of the methods in this class are final, and can be
 * tested in isolation of specific subclasses. Those that cannot be (such as {@link
 * StateBase#reset()}) will be covered by other tests in addition to this one.
 */
class StateBaseTest {
    protected static final String STATE_KEY = "TEST_STATE_KEY";
    protected static final String UNKNOWN_KEY = "BOGUS";
    protected static final String KNOWN_KEY_1 = "A";
    protected static final String KNOWN_VALUE_1 = "Apple";
    protected static final String KNOWN_KEY_2 = "B";
    protected static final String KNOWN_VALUE_2 = "Banana";

    protected StateBase<String, String> createState() {
        final Map<String, String> backingStore = new HashMap<>();
        backingStore.put(KNOWN_KEY_1, KNOWN_VALUE_1);
        backingStore.put(KNOWN_KEY_2, KNOWN_VALUE_2);
        return new DummyState(STATE_KEY, backingStore);
    }

    /** Make sure the constructor is holding onto the state key properly */
    @Test
    @DisplayName("The state key must match what was provided in the constructor")
    void testStateKey() {
        // The STATE_KEY was used when `state` was created in #setUp
        final var state = createState();
        assertEquals(STATE_KEY, state.getStateKey());
    }

    /**
     * When we are asked to get an unknown item (something not in the backing store), the {@link
     * StateBase} still needs to remember this key and include it in the set of read keys, because
     * the fact that the key was missing might be an important piece of information when working in
     * pre-handle. So we keep track in readKeys of ALL keys read, not just those that had values.
     */
    @Test
    @DisplayName("`get` of an unknown item returns an empty optional")
    void testNonExistingGet() {
        final var state = createState();
        assertTrue(state.readKeys().isEmpty());
        assertTrue(state.get(UNKNOWN_KEY).isEmpty());
        assertTrue(state.readKeys().contains(UNKNOWN_KEY));
    }

    /**
     * When asked to get a known item, not only is it returned, but we also record this in the
     * "readKeys".
     */
    @Test
    @DisplayName("`get` of a known item returns that item")
    void testExistingGet() {
        final var state = createState();
        assertTrue(state.readKeys().isEmpty());
        assertTrue(state.get(KNOWN_KEY_1).isPresent());
        assertEquals(KNOWN_VALUE_1, state.get(KNOWN_KEY_1).get());
        assertTrue(state.readKeys().contains(KNOWN_KEY_1));
    }

    /** Similar to get, but for "contains". We must record this in "readKeys". */
    @Test
    @DisplayName("`contains` of an unknown item returns false")
    void testNonExistingContains() {
        final var state = createState();
        assertTrue(state.readKeys().isEmpty());
        assertFalse(state.contains(UNKNOWN_KEY));
        assertTrue(state.readKeys().contains(UNKNOWN_KEY));
    }

    /** Similar to get, but for contains. */
    @Test
    @DisplayName("`contains` of a known item returns true")
    void testExistingContains() {
        final var state = createState();
        assertTrue(state.readKeys().isEmpty());
        assertTrue(state.contains(KNOWN_KEY_2));
        assertTrue(state.readKeys().contains(KNOWN_KEY_2));
    }

    /**
     * Read some states, which will populate the "readKeys". Then clear the state, which must clear
     * the "readKeys".
     */
    @Test
    @DisplayName("`reset` clears the readKeys cache")
    void testReset() {
        // Populate "readKeys" by reading values
        final var state = createState();
        assertNotNull(state.get(KNOWN_KEY_1));
        assertTrue(state.contains(KNOWN_KEY_2));
        assertEquals(2, state.readKeys().size());

        // Reset and verify the cache is empty
        state.reset();
        assertTrue(state.readKeys().isEmpty());

        // Repopulate the cache by reading again, proving it is still working after reset
        assertNotNull(state.get(KNOWN_KEY_1));
        assertTrue(state.contains(KNOWN_KEY_2));
        assertEquals(2, state.readKeys().size());
    }

    /** A simple dummy implementation of {@link StateBase} backed by a simple map. */
    protected static final class DummyState extends StateBase<String, String> {
        private final Map<String, String> backingStore;

        DummyState(@NonNull String stateKey, @NonNull Map<String, String> backingStore) {
            super(stateKey);
            this.backingStore = backingStore;
        }

        @Override
        protected String readFromDataSource(@NonNull String key) {
            return backingStore.get(key);
        }
    }
}
