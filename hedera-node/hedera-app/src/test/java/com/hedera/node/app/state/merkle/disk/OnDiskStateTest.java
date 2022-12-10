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
package com.hedera.node.app.state.merkle.disk;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnDiskStateTest {
    //    private static final String STATE_KEY = "TEST_STATE_KEY";
    //    private static final Long A_KEY = 1L;
    //    private static final Long B_KEY = 2L;
    //    private static final Long C_KEY = 3L;
    //    private static final Long D_KEY = 4L;
    //    private static final Long E_KEY = 5L;
    //    private static final String A_VALUE = "Apple";
    //    private static final String B_VALUE = "Banana";
    //    private static final String C_VALUE = "Cherry";
    //    private static final String D_VALUE = "Date";
    //    private static final String E_VALUE = "Eggplant";
    //    private VirtualMap<OnDiskKey<Long>, OnDiskValue<String>> virtualMap;
    //
    //    @BeforeEach
    //    protected void setUp(@TempDir Path dir) {
    //        //noinspection unchecked
    //        final var dbBuilder =
    //                new JasperDbBuilder<OnDiskKey<Long>, OnDiskValue<String>>()
    //                        // Use this temporary directory for all the database files
    //                        .storageDir(dir)
    //                        // We will only be using 5 keys, so set this to something small
    //                        .maxNumOfKeys(20)
    //                        // Long.MAX_VALUE means, keep the index in RAM
    //                        .internalHashesRamToDiskThreshold(Long.MAX_VALUE)
    //                        // Nope. RAM please.
    //                        .preferDiskBasedIndexes(false)
    //                        // Serialize the key.
    //                        .keySerializer(null)
    //                        .virtualInternalRecordSerializer(null);
    //
    //        virtualMap = new VirtualMap<>("Alphabet", dbBuilder);
    //    }
    //
    //    private OnDiskState<Long, String> createState(
    //            VirtualMap<OnDiskKey<Long>, OnDiskValue<String>> map) {
    //        return new OnDiskState<>(
    //                STATE_KEY,
    //                map,
    //                MerkleTestBase::parseLong,
    //                MerkleTestBase::parseString,
    //                MerkleTestBase::writeLong,
    //                MerkleTestBase::writeString);
    //    }
    //
    //    private boolean virtualMapContainsKey(Long key) {
    //        return virtualMap.containsKey(
    //                new OnDiskKey<>(key, MerkleTestBase::parseLong, MerkleTestBase::writeLong));
    //    }
    //
    //    private String readValueFromVirtualMap(Long key) {
    //        final var val =
    //                virtualMap.get(
    //                        new OnDiskKey<>(key, MerkleTestBase::parseLong,
    // MerkleTestBase::writeLong));
    //        return val == null ? null : val.getValue();
    //    }
    //
    //    @Nested
    //    @DisplayName("Constructor Tests")
    //    final class ConstructorTest {
    //        @Test
    //        @DisplayName("Constructor requires a non-null state key")
    //        void nullStateKeyThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new OnDiskState<>(
    //                                    null,
    //                                    virtualMap,
    //                                    MerkleTestBase::parseLong,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeLong,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null merkle map")
    //        void nullMapThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new OnDiskState<>(
    //                                    STATE_KEY,
    //                                    null,
    //                                    MerkleTestBase::parseLong,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeLong,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null key serializer")
    //        void nullKeySerializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new OnDiskState<>(
    //                                    STATE_KEY,
    //                                    virtualMap,
    //                                    null,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeLong,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null value serializer")
    //        void nullValueSerializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new OnDiskState<>(
    //                                    STATE_KEY,
    //                                    virtualMap,
    //                                    MerkleTestBase::parseLong,
    //                                    null,
    //                                    MerkleTestBase::writeLong,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null key deserializer")
    //        void nullKeyDeserializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new OnDiskState<>(
    //                                    STATE_KEY,
    //                                    virtualMap,
    //                                    MerkleTestBase::parseLong,
    //                                    MerkleTestBase::parseString,
    //                                    null,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null value deserializer")
    //        void nullValueDeserializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new OnDiskState<>(
    //                                    STATE_KEY,
    //                                    virtualMap,
    //                                    MerkleTestBase::parseLong,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeLong,
    //                                    null));
    //        }
    //    }
    //
    //    /** Various tests for mutations to the store */
    //    @Nested
    //    @DisplayName("Mutations")
    //    final class MutationsTest {
    //
    //        /** Before each test, pre-configure the merkle map to have these built-in values. */
    //        @BeforeEach
    //        void setUp() {
    //            final var state = createState(virtualMap);
    //            state.put(A_KEY, A_VALUE);
    //            state.put(B_KEY, B_VALUE);
    //            state.put(C_KEY, C_VALUE);
    //            state.put(D_KEY, D_VALUE);
    //            state.commit();
    //
    //            // Now make a copy, so we are working on a new fast copy and not the original.
    //            // Probably doesn't make a difference, but it seems like a good thing to simulate.
    //            virtualMap = virtualMap.copy();
    //
    //            // And to be safe, let's make sure the elements are all there
    //            assertEquals(A_VALUE, readValueFromVirtualMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromVirtualMap(B_KEY));
    //            assertEquals(C_VALUE, readValueFromVirtualMap(C_KEY));
    //            assertEquals(D_VALUE, readValueFromVirtualMap(D_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, put a new value into it, and then commit it. The new value MUST
    // not
    //         * be present in the merkle tree until after commit.
    //         */
    //        @Test
    //        @DisplayName("Put a new entry and commit it")
    //        void putAndCommit() {
    //            // Make sure this key is NOT in the merkle map
    //            assertFalse(virtualMapContainsKey(E_KEY));
    //
    //            // Create a state and put the value
    //            final var state = createState(virtualMap);
    //            state.put(E_KEY, E_VALUE);
    //
    //            // Verify it is STILL not in the merkle map
    //            assertFalse(virtualMapContainsKey(E_KEY));
    //
    //            // Commit it and verify that it IS now in the merkle map
    //            state.commit();
    //            assertEquals(E_VALUE, readValueFromVirtualMap(E_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, put a new value into it, and then commit it. The new value MUST
    // not
    //         * be present in the merkle tree until after commit.
    //         */
    //        @Test
    //        @DisplayName("Put a new entry and roll it back")
    //        void putAndReset() {
    //            // Make sure this key is NOT in the merkle map
    //            assertFalse(virtualMapContainsKey(E_KEY));
    //
    //            // Create a state and put the value
    //            final var state = createState(virtualMap);
    //            state.put(E_KEY, E_VALUE);
    //
    //            // Verify it is STILL not in the merkle map
    //            assertFalse(virtualMapContainsKey(E_KEY));
    //
    //            // Reset the state and verify it is STILL not in the map
    //            state.reset();
    //            assertFalse(virtualMapContainsKey(E_KEY));
    //
    //            // Now commit it, and verify it is STILL not in the map because the
    //            // modification was lost after reset
    //            state.commit();
    //            assertFalse(virtualMapContainsKey(E_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, remove an existing value from it, and then commit it. The
    // removed
    //         * value MUST be present in the merkle tree until after commit.
    //         */
    //        @Test
    //        @DisplayName("Remove an entry and commit it")
    //        void removeAndCommit() {
    //            // Make sure this key IS in the merkle map
    //            assertTrue(virtualMapContainsKey(A_KEY));
    //
    //            // Create a state and remove the value
    //            final var state = createState(virtualMap);
    //            state.remove(A_KEY);
    //
    //            // Verify it is STILL in the merkle map
    //            assertTrue(virtualMapContainsKey(A_KEY));
    //
    //            // Commit it and verify that it is now NOT in the merkle map
    //            state.commit();
    //            assertFalse(virtualMapContainsKey(A_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, remove an existing value from it, and then roll back. The
    // removed
    //         * value MUST never be removed from the merkle tree.
    //         */
    //        @Test
    //        @DisplayName("Remove an entry and roll back the change")
    //        void removeAndRollback() {
    //            // Make sure this key IS in the merkle map
    //            assertTrue(virtualMapContainsKey(B_KEY));
    //
    //            // Create a state and remove the value
    //            final var state = createState(virtualMap);
    //            state.remove(B_KEY);
    //
    //            // Verify it is STILL in the merkle map
    //            assertTrue(virtualMapContainsKey(B_KEY));
    //
    //            // Roll back and confirm it is STILL in the map
    //            state.reset();
    //            assertTrue(virtualMapContainsKey(B_KEY));
    //
    //            // Commit it and verify that it is STILL in the map since the change was rolled
    // back
    //            state.commit();
    //            assertTrue(virtualMapContainsKey(B_KEY));
    //        }
    //
    //        /**
    //         * A variety of modifications over many fast-copies, including rolled-back
    // modifications,
    //         * with verification that the merkle map has all the right values at each stage in the
    //         * process.
    //         */
    //        @Test
    //        @DisplayName("The Smörgåsbord of modifications, rollbacks, commits, and fast copies")
    //        void smorgasbord() {
    //            // Let's read with get and getForModify, remove something, put a modification, and
    // put
    //            // something new.
    //            var state = createState(virtualMap);
    //            assertEquals(A_VALUE, state.get(A_KEY).orElse(null));
    //            assertEquals(B_VALUE, state.getForModify(B_KEY).orElse(null));
    //            state.put(C_KEY, "Cat");
    //            state.remove(D_KEY);
    //            state.put(E_KEY, E_VALUE);
    //            state.commit();
    //
    //            // The merkle state should now be:
    //            assertEquals(A_VALUE, readValueFromVirtualMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromVirtualMap(B_KEY));
    //            assertEquals("Cat", readValueFromVirtualMap(C_KEY));
    //            assertFalse(virtualMapContainsKey(D_KEY));
    //            assertEquals(E_VALUE, readValueFromVirtualMap(E_KEY));
    //
    //            // Now let's make a fast copy and create a new state and make some more
    // modifications
    //            // and reads.
    //            // And then let's throw them all away and make sure the merkle map hasn't changed.
    //            virtualMap = virtualMap.copy();
    //            state = createState(virtualMap);
    //            assertEquals(A_VALUE, state.get(A_KEY).orElse(null));
    //            state.remove(B_KEY);
    //            assertEquals("Cat", state.getForModify(C_KEY).orElse(null));
    //            state.put(D_KEY, "Dog");
    //            state.put(E_KEY, "Elephant");
    //            state.reset();
    //
    //            // The merkle state should still be:
    //            assertEquals(A_VALUE, readValueFromVirtualMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromVirtualMap(B_KEY));
    //            assertEquals("Cat", readValueFromVirtualMap(C_KEY));
    //            assertFalse(virtualMapContainsKey(D_KEY));
    //            assertEquals(E_VALUE, readValueFromVirtualMap(E_KEY));
    //
    //            // Now reuse the same state, make some modifications, and commit them.
    //            state.put(A_KEY, "Aardvark");
    //            state.remove(B_KEY);
    //            state.remove(C_KEY);
    //            state.put(D_KEY, D_VALUE);
    //            state.put(E_KEY, "Elephant");
    //            state.commit();
    //
    //            // The merkle state should now be:
    //            assertEquals("Aardvark", readValueFromVirtualMap(A_KEY));
    //            assertFalse(virtualMapContainsKey(B_KEY));
    //            assertFalse(virtualMapContainsKey(C_KEY));
    //            assertEquals(D_VALUE, readValueFromVirtualMap(D_KEY));
    //            assertEquals("Elephant", readValueFromVirtualMap(E_KEY));
    //        }
    //    }
}
