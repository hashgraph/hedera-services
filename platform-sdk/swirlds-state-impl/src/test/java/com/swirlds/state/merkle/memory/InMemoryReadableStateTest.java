// SPDX-License-Identifier: Apache-2.0
package com.swirlds.state.merkle.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.swirlds.state.test.fixtures.merkle.MerkleTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryReadableStateTest extends MerkleTestBase {

    @Nested
    @DisplayName("Constructor Tests")
    final class ConstructorTest {
        @BeforeEach
        void setUp() {
            setupFruitMerkleMap();
        }

        @Test
        @DisplayName("You must specify the metadata")
        void nullMetadataThrows() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new InMemoryReadableKVState<>(null, fruitMerkleMap))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("You must specify the merkle map")
        void nullMerkleMapThrows() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new InMemoryReadableKVState<>(FRUIT_STATE_KEY, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("The stateKey matches that supplied by the metadata")
        void stateKey() {
            final var state = new InMemoryReadableKVState<>(FRUIT_STATE_KEY, fruitMerkleMap);
            assertThat(state.getStateKey()).isEqualTo(FRUIT_STATE_KEY);
        }
    }

    private void add(String key, String value) {
        add(fruitMerkleMap, inMemoryValueClassId(FRUIT_STATE_KEY), STRING_CODEC, STRING_CODEC, key, value);
    }

    @Nested
    @DisplayName("Query Tests")
    final class QueryTest {
        private InMemoryReadableKVState<String, String> state;

        @BeforeEach
        void setUp() {
            setupFruitMerkleMap();
            state = new InMemoryReadableKVState<>(FRUIT_STATE_KEY, fruitMerkleMap);
            add(A_KEY, APPLE);
            add(B_KEY, BANANA);
            add(C_KEY, CHERRY);
        }

        @Test
        @DisplayName("Get keys from the merkle map")
        void get() {
            assertThat(state.get(A_KEY)).isEqualTo(APPLE);
            assertThat(state.get(B_KEY)).isEqualTo(BANANA);
            assertThat(state.get(C_KEY)).isEqualTo(CHERRY);
            assertThat(state.get(D_KEY)).isNull();
            assertThat(state.get(E_KEY)).isNull();
            assertThat(state.get(F_KEY)).isNull();
            assertThat(state.get(G_KEY)).isNull();
        }

        @Test
        @DisplayName("Iterate over keys in the merkle map")
        void iterate() {
            assertThat(state.keys()).toIterable().containsExactlyInAnyOrder(A_KEY, B_KEY, C_KEY);
        }
    }
}
