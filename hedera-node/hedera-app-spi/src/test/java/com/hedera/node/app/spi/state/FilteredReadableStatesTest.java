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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilteredReadableStatesTest {
    @Nested
    @DisplayName("FilteredReadableStates over an empty delegate ReadableStates")
    class EmptyDelegate extends StateTestBase {
        private FilteredReadableStates states;

        @BeforeEach
        void setUp() {
            final var delegate = MapReadableStates.builder().build();
            states = new FilteredReadableStates(delegate, Collections.emptySet());
        }

        @Test
        @DisplayName("Size is zero")
        void size() {
            assertThat(states.size()).isZero();
        }

        @Test
        @DisplayName("Is Empty")
        void empty() {
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Contains")
        void contains() {
            assertThat(states.contains(FRUIT_STATE_KEY)).isFalse();
        }

        @Test
        @DisplayName("Throws NPE if the key is null")
        void nullKey() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> states.get(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Throws IAE for any non-null key")
        void nonNullKey() {
            assertThatThrownBy(() -> states.get(UNKNOWN_KEY)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("FilteredReadableStates with no state keys specified")
    class NoStateKeys extends StateTestBase {
        private FilteredReadableStates states;

        @BeforeEach
        void setUp() {
            states = new FilteredReadableStates(allReadableStates(), Collections.emptySet());
        }

        @Test
        @DisplayName("Size is zero")
        void size() {
            assertThat(states.size()).isZero();
        }

        @Test
        @DisplayName("Is Empty")
        void empty() {
            assertThat(states.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Contains")
        void contains() {
            assertThat(states.contains(FRUIT_STATE_KEY)).isFalse();
        }

        @Test
        @DisplayName("Throws NPE if the key is null")
        void nullKey() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> states.get(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Throws IAE for any non-null key")
        void nonNullKey() {
            assertThatThrownBy(() -> states.get(UNKNOWN_KEY)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("FilteredReadableStates with a subset of state keys available in the delegate")
    class Subset extends StateTestBase {
        private FilteredReadableStates states;

        @BeforeEach
        void setUp() {
            final var delegate = MapReadableStates.builder()
                    .state(readableFruitState())
                    .state(readableCountryState())
                    .state(readableAnimalState())
                    .state(readableSTEAMState())
                    .build();
            states = new FilteredReadableStates(delegate, Set.of(ANIMAL_STATE_KEY, STEAM_STATE_KEY));
        }

        @Test
        @DisplayName("Exactly 2 states were included")
        void size() {
            assertThat(states.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("Is Not Empty")
        void empty() {
            assertThat(states.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Contains")
        void contains() {
            assertThat(states.contains(FRUIT_STATE_KEY)).isFalse();
            assertThat(states.contains(COUNTRY_STATE_KEY)).isFalse();
            assertThat(states.contains(ANIMAL_STATE_KEY)).isTrue();
            assertThat(states.contains(STEAM_STATE_KEY)).isTrue();
        }

        @Test
        @DisplayName("Can read the 2 states")
        void acceptedStates() {
            assertThat(states.get(ANIMAL_STATE_KEY)).isNotNull();
            assertThat(states.get(STEAM_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Throws IAE for other than the two specified states")
        void filteredStates() {
            assertThatThrownBy(() -> states.get(FRUIT_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> states.get(COUNTRY_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("FilteredReadableStates allows more state keys than are in the delegate")
    class Superset extends StateTestBase {
        private FilteredReadableStates states;

        @BeforeEach
        void setUp() {
            final var delegate =
                    MapReadableStates.builder().state(readableFruitState()).build();
            states = new FilteredReadableStates(delegate, Set.of(FRUIT_STATE_KEY, SPACE_STATE_KEY));
        }

        @Test
        @DisplayName("Exactly 1 states was included because only one of two filtered states were in the" + " delegate")
        void size() {
            assertThat(states.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Is Not Empty")
        void empty() {
            assertThat(states.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Contains")
        void contains() {
            assertThat(states.contains(FRUIT_STATE_KEY)).isTrue();
            assertThat(states.contains(SPACE_STATE_KEY)).isFalse();
        }

        @Test
        @DisplayName("Can read FRUIT because it is in the acceptable set and in the delegate")
        void acceptedStates() {
            assertThat(states.get(FRUIT_STATE_KEY)).isNotNull();
        }

        @Test
        @DisplayName("Cannot read STEM because it is not in the delegate")
        void missingState() {
            assertThatThrownBy(() -> states.get(STEAM_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("StateKeys Tests")
    class StateKeysTest extends StateTestBase {
        private ReadableStates delegate;

        @BeforeEach
        void setUp() {
            delegate = MapReadableStates.builder()
                    .state(readableFruitState())
                    .state(readableAnimalState())
                    .state(readableSpaceState())
                    .build();
        }

        @Test
        @DisplayName("The filtered `stateKeys` contains all states that are in the filter and in the" + " delegate")
        void filteredStateKeys() {
            // Given a delegate with multiple k/v states and a set of state keys that are
            // a subset of keys in the delegate AND contain some keys not in the delegate
            final var stateKeys = Set.of(SPACE_STATE_KEY, STEAM_STATE_KEY);
            final var filtered = new FilteredReadableStates(delegate, stateKeys);

            // When we look at the contents of the filtered `stateKeys`
            final var filteredStateKeys = filtered.stateKeys();

            // Then we find only those states that are both in the state keys passed to
            // the FilteredReadableStates, and in the delegate.
            assertThat(filteredStateKeys).containsExactlyInAnyOrder(SPACE_STATE_KEY);
        }

        @Test
        @DisplayName("A modifiable `stateKeys` set provided to a constructor can be changed without"
                + " impacting the FilteredReadableStates")
        void modifiableStateKeys() {
            // Given a delegate with multiple k/v states and a modifiable set of state keys,
            final var modifiableStateKeys = new HashSet<String>();
            modifiableStateKeys.add(SPACE_STATE_KEY);

            // When a FilteredReadableStates is created, and the Set of all state keys for
            // the filtered set is read and the modifiable state keys map is modified
            final var filtered = new FilteredReadableStates(delegate, modifiableStateKeys);
            final var filteredStateKeys = filtered.stateKeys();
            modifiableStateKeys.add(ANIMAL_STATE_KEY);
            modifiableStateKeys.remove(SPACE_STATE_KEY);

            // Then these changes are NOT found in the filtered state keys
            assertThat(filteredStateKeys).containsExactlyInAnyOrder(SPACE_STATE_KEY);
        }

        @Test
        @DisplayName("The set of filtered state keys is unmodifiable")
        void filteredStateKeysAreUnmodifiable() {
            // Given a FilteredReadableStates
            final var stateKeys = Set.of(SPACE_STATE_KEY, ANIMAL_STATE_KEY);
            final var filtered = new FilteredReadableStates(delegate, stateKeys);

            // When the filtered state keys is read and a modification attempted,
            // then an exception is thrown
            final var filteredStateKeys = filtered.stateKeys();
            assertThatThrownBy(() -> filteredStateKeys.add(FRUIT_STATE_KEY))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
