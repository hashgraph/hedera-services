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
package com.hedera.node.app.state.merkle.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.state.merkle.MerkleTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryReadableStateTest extends MerkleTestBase {

    @Nested
    @DisplayName("Constructor Tests")
    final class ConstructorTest {
        @Test
        @DisplayName("You must specify the metadata")
        void nullMetadataThrows() {
            assertThatThrownBy(() -> new InMemoryReadableState<>(null, fruitMerkleMap))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("You must specify the merkle map")
        void nullMerkleMapThrows() {
            assertThatThrownBy(() -> new InMemoryReadableState<>(fruitMetadata, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("The stateKey matches that supplied by the metadata")
        void stateKey() {
            final var state = new InMemoryReadableState<>(fruitMetadata, fruitMerkleMap);
            assertThat(state.getStateKey()).isEqualTo(FRUIT_STATE_KEY);
        }
    }

    @Nested
    @DisplayName("Query Tests")
    final class QueryTest {
        private InMemoryReadableState<String, String> state;

        @BeforeEach
        void setUp() {
            state = new InMemoryReadableState<>(fruitMetadata, fruitMerkleMap);
            add(fruitMerkleMap, fruitMetadata, A_KEY, APPLE);
            add(fruitMerkleMap, fruitMetadata, B_KEY, BANANA);
            add(fruitMerkleMap, fruitMetadata, C_KEY, CHERRY);
        }

        @Test
        @DisplayName("Get keys from the merkle map")
        void get() {
            assertThat(state.get(A_KEY)).get().isEqualTo(APPLE);
            assertThat(state.get(B_KEY)).get().isEqualTo(BANANA);
            assertThat(state.get(C_KEY)).get().isEqualTo(CHERRY);
            assertThat(state.get(D_KEY)).isEmpty();
            assertThat(state.get(E_KEY)).isEmpty();
            assertThat(state.get(F_KEY)).isEmpty();
            assertThat(state.get(G_KEY)).isEmpty();
        }

        @Test
        @DisplayName("Iterate over keys in the merkle map")
        void iterate() {
            assertThat(state.keys()).toIterable().containsExactlyInAnyOrder(A_KEY, B_KEY, C_KEY);
        }
    }
}
