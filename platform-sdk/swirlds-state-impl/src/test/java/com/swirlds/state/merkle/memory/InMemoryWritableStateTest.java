/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.swirlds.state.test.fixtures.merkle.MerkleTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryWritableStateTest extends MerkleTestBase {

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
            assertThatThrownBy(() -> new InMemoryWritableKVState<>(
                            null, inMemoryValueClassId(FRUIT_STATE_KEY), STRING_CODEC, STRING_CODEC, fruitMerkleMap))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("You must specify the merkle map")
        void nullMerkleMapThrows() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new InMemoryWritableKVState<>(
                            FRUIT_STATE_KEY, inMemoryValueClassId(FRUIT_STATE_KEY), STRING_CODEC, STRING_CODEC, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("The stateKey matches that supplied by the metadata")
        void stateKey() {
            final var state = createState();
            assertThat(state.getStateKey()).isEqualTo(FRUIT_STATE_KEY);
        }

        @Test
        @DisplayName("The size of the state is the size of the merkle map")
        void sizeWorks() {
            final var state = createState();
            assertThat(state.size()).isZero();

            add(A_KEY, APPLE);
            add(B_KEY, BANANA);
            add(C_KEY, CHERRY);
            assertThat(state.sizeOfDataSource()).isEqualTo(fruitMerkleMap.size());
        }
    }

    private InMemoryWritableKVState<String, String> createState() {
        return new InMemoryWritableKVState<>(
                FRUIT_STATE_KEY, inMemoryValueClassId(FRUIT_STATE_KEY), STRING_CODEC, STRING_CODEC, fruitMerkleMap);
    }

    private void add(String key, String value) {
        add(fruitMerkleMap, inMemoryValueClassId(FRUIT_STATE_KEY), STRING_CODEC, STRING_CODEC, key, value);
    }

    @Nested
    @DisplayName("Query Tests")
    final class QueryTest {
        private InMemoryWritableKVState<String, String> state;

        @BeforeEach
        void setUp() {
            setupFruitMerkleMap();
            state = createState();
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

    @Nested
    @DisplayName("Mutation Tests")
    final class MutationTest {
        private InMemoryWritableKVState<String, String> state;

        @BeforeEach
        void setUp() {
            setupFruitMerkleMap();
            state = createState();
            add(A_KEY, APPLE);
            add(B_KEY, BANANA);
        }

        boolean merkleMapContainsKey(String key) {
            return fruitMerkleMap.containsKey(new InMemoryKey<>(key));
        }

        String readValueFromMerkleMap(String key) {
            final var val = fruitMerkleMap.get(new InMemoryKey<>(key));
            return val == null ? null : val.getValue();
        }

        @Test
        @DisplayName("Put a new entry and commit it")
        void putAndCommit() {
            // Make sure this key is NOT in the merkle map
            assertThat(merkleMapContainsKey(E_KEY)).isFalse();

            // Put the value into the state
            state.put(E_KEY, EGGPLANT);

            // Verify it is STILL not in the merkle map
            assertThat(merkleMapContainsKey(E_KEY)).isFalse();

            // Commit it and verify that it IS now in the merkle map
            state.commit();
            assertThat(merkleMapContainsKey(E_KEY)).isTrue();
        }

        @Test
        @DisplayName("Put a new entry and roll it back")
        void putAndReset() {
            // Make sure this key is NOT in the merkle map
            assertThat(merkleMapContainsKey(E_KEY)).isFalse();

            // Put the value into the state
            state.put(E_KEY, EGGPLANT);

            // Verify it is STILL not in the merkle map
            assertThat(merkleMapContainsKey(E_KEY)).isFalse();

            // Reset the state and verify it is STILL not in the map
            state.reset();
            assertThat(merkleMapContainsKey(E_KEY)).isFalse();

            // Now commit it, and verify it is STILL not in the map because the
            // modification was lost after reset
            state.commit();
            assertThat(merkleMapContainsKey(E_KEY)).isFalse();
        }

        @Test
        @DisplayName("Remove an entry and commit it")
        void removeAndCommit() {
            // Make sure this key IS in the merkle map
            assertThat(merkleMapContainsKey(A_KEY)).isTrue();

            // Remove the value from the state
            state.remove(A_KEY);

            // Verify it is STILL in the merkle map
            assertThat(merkleMapContainsKey(A_KEY)).isTrue();

            // Commit it and verify that it is now NOT in the merkle map
            state.commit();
            assertThat(merkleMapContainsKey(A_KEY)).isFalse();
        }

        @Test
        @DisplayName("Remove an entry and roll back the change")
        void removeAndRollback() {
            // Make sure this key IS in the merkle map
            assertThat(merkleMapContainsKey(B_KEY)).isTrue();

            // Remove the value
            state.remove(B_KEY);

            // Verify it is STILL in the merkle map
            assertThat(merkleMapContainsKey(B_KEY)).isTrue();

            // Roll back and confirm it is STILL in the map
            state.reset();
            assertThat(merkleMapContainsKey(B_KEY)).isTrue();

            // Commit it and verify that it is STILL in the map since the change was rolled back
            state.commit();
            assertThat(merkleMapContainsKey(B_KEY)).isTrue();
        }

        /**
         * A variety of modifications over many fast-copies, including rolled-back modifications,
         * with verification that the merkle map has all the right values at each stage in the
         * process.
         */
        @Test
        @DisplayName("The Smörgåsbord of modifications, rollbacks, commits, and fast copies")
        void smorgasbord() {
            // This needs to be done so fast-copy on merkle map will work
            setupConstructableRegistry();

            // Let's read with get and getForModify, remove something, put a modification, and
            // put something new.
            assertThat(state.get(A_KEY)).isEqualTo(APPLE);
            assertThat(state.get(B_KEY)).isEqualTo(BANANA);
            state.put(C_KEY, CHERRY);
            state.remove(D_KEY);
            state.put(E_KEY, EGGPLANT);
            state.commit();

            // The merkle state should now be:
            assertThat(readValueFromMerkleMap(A_KEY)).isEqualTo(APPLE);
            assertThat(readValueFromMerkleMap(B_KEY)).isEqualTo(BANANA);
            assertThat(readValueFromMerkleMap(C_KEY)).isEqualTo(CHERRY);
            assertThat(readValueFromMerkleMap(D_KEY)).isNull();
            assertThat(readValueFromMerkleMap(E_KEY)).isEqualTo(EGGPLANT);

            // Now let's make a fast copy and create a new state and make some more
            // modifications and reads. And then let's throw them all away and make
            // sure the merkle map hasn't changed.
            fruitMerkleMap = fruitMerkleMap.copy();
            state = createState();
            assertThat(state.getForModify(A_KEY)).isEqualTo(APPLE);
            state.remove(B_KEY);
            assertThat(state.get(C_KEY)).isEqualTo(CHERRY);
            state.put(D_KEY, DATE);
            state.put(E_KEY, ELDERBERRY);
            state.reset();

            // The merkle state should still be:
            assertThat(readValueFromMerkleMap(A_KEY)).isEqualTo(APPLE);
            assertThat(readValueFromMerkleMap(B_KEY)).isEqualTo(BANANA);
            assertThat(readValueFromMerkleMap(C_KEY)).isEqualTo(CHERRY);
            assertThat(readValueFromMerkleMap(D_KEY)).isNull();
            assertThat(readValueFromMerkleMap(E_KEY)).isEqualTo(EGGPLANT);

            // Now reuse the same state, make some modifications, and commit them.
            state.put(A_KEY, ACAI);
            state.remove(B_KEY);
            state.remove(C_KEY);
            state.put(D_KEY, DATE);
            state.put(E_KEY, ELDERBERRY);
            state.commit();

            // The merkle state should now be:
            assertThat(readValueFromMerkleMap(A_KEY)).isEqualTo(ACAI);
            assertThat(readValueFromMerkleMap(B_KEY)).isNull();
            assertThat(readValueFromMerkleMap(C_KEY)).isNull();
            assertThat(readValueFromMerkleMap(D_KEY)).isEqualTo(DATE);
            assertThat(readValueFromMerkleMap(E_KEY)).isEqualTo(ELDERBERRY);
        }
    }
}
