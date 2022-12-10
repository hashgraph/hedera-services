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
package com.hedera.node.app.spi.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.spi.fixtures.state.MapReadableState;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the base class for all states. Most of the methods in this class are final, and can be
 * tested in isolation of specific subclasses. Those that cannot be (such as {@link
 * ReadableStateBase#reset()}) will be covered by other tests in addition to this one.
 */
class ReadableStateBaseTest extends StateTestBase {
    private ReadableStateBase<String, String> state;
    protected Map<String, String> backingMap;

    @BeforeEach
    void setUp() {
        this.backingMap = createBackingMap();
        this.state = createFruitState(this.backingMap);
    }

    protected Map<String, String> createBackingMap() {
        final var map = new HashMap<String, String>();
        map.put(A_KEY, APPLE);
        map.put(B_KEY, BANANA);
        map.put(C_KEY, CHERRY);
        map.put(D_KEY, DATE);
        map.put(E_KEY, EGGPLANT);
        map.put(F_KEY, FIG);
        map.put(G_KEY, GRAPE);
        return map;
    }

    protected ReadableStateBase<String, String> createFruitState(Map<String, String> backingMap) {
        return new MapReadableState<>(FRUIT_STATE_KEY, backingMap);
    }

    /** Make sure the constructor is holding onto the state key properly */
    @Test
    @DisplayName("The state key must match what was provided in the constructor")
    void testStateKey() {
        assertEquals(FRUIT_STATE_KEY, state.getStateKey());
    }

    /**
     * When we are asked to get an unknown item (something not in the backing store), the {@link
     * ReadableStateBase} still needs to remember this key and include it in the set of read keys,
     * because the fact that the key was missing might be an important piece of information when
     * working in pre-handle. So we keep track in readKeys of ALL keys read, not just those that had
     * values.
     */
    @Test
    @DisplayName("`get` of an unknown item returns an empty optional")
    void testNonExistingGet() {
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
        assertTrue(state.readKeys().isEmpty());
        assertTrue(state.get(A_KEY).isPresent());
        assertEquals(APPLE, state.get(A_KEY).get());
        assertTrue(state.readKeys().contains(A_KEY));
    }

    /** Similar to get, but for "contains". We must record this in "readKeys". */
    @Test
    @DisplayName("`contains` of an unknown item returns false")
    void testNonExistingContains() {
        assertTrue(state.readKeys().isEmpty());
        assertFalse(state.contains(UNKNOWN_KEY));
        assertTrue(state.readKeys().contains(UNKNOWN_KEY));
    }

    /** Similar to get, but for contains. */
    @Test
    @DisplayName("`contains` of a known item returns true")
    void testExistingContains() {
        assertTrue(state.readKeys().isEmpty());
        assertTrue(state.contains(B_KEY));
        assertTrue(state.readKeys().contains(B_KEY));
    }

    /**
     * Read some states, which will populate the "readKeys". Then clear the state, which must clear
     * the "readKeys".
     */
    @Test
    @DisplayName("`reset` clears the readKeys cache")
    void testReset() {
        // Populate "readKeys" by reading values
        assertNotNull(state.get(A_KEY));
        assertTrue(state.contains(B_KEY));
        assertEquals(2, state.readKeys().size());

        // Reset and verify the cache is empty
        state.reset();
        assertTrue(state.readKeys().isEmpty());

        // Repopulate the cache by reading again, proving it is still working after reset
        assertNotNull(state.get(A_KEY));
        assertTrue(state.contains(B_KEY));
        assertEquals(2, state.readKeys().size());
    }

    @Test
    @DisplayName("Can iterate over all fruit")
    void testIteration() {
        assertThat(state.keys())
                .toIterable()
                .containsExactlyInAnyOrder(backingMap.keySet().toArray(new String[0]));
    }
}
