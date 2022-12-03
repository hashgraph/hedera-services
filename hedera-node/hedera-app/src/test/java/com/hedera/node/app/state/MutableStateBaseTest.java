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
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

/**
 * Tests for the base class for all mutable states. All non-abstract methods in this class are
 * final, so we can test here very thoroughly with a dummy {@link MutableStateBase}.
 */
class MutableStateBaseTest {
    protected static final String STATE_KEY = "TEST_STATE_KEY";
    private static final String UNKNOWN_KEY = "BOGUS";
    private static final String KNOWN_KEY_1 = "A";
    private static final String KNOWN_VALUE_1 = "Apple";
    private static final String KNOWN_KEY_2 = "B";
    private static final String KNOWN_VALUE_2 = "Banana";

    private MutableStateBase<String, String> state;
    private Map<String, DummyMerkleNode> backingStore;

    @BeforeEach
    void setUp() {
        final var map = new HashMap<String, DummyMerkleNode>();
        this.backingStore = Mockito.spy(map);
        backingStore.put(KNOWN_KEY_1, new DummyMerkleNode(KNOWN_VALUE_1));
        backingStore.put(KNOWN_KEY_2, new DummyMerkleNode(KNOWN_VALUE_2));

        // This method call is really unexpected. I tried to add both keys to the map before
        // creating
        // the spy, and it seems to me that should have worked, but it doesn't, the spy doesn't know
        // anything about the values put into the map. Instead, I had to add them afterward. But
        // then
        // I need to reset the spy, so all my verify methods are right. Kind of bogus, honestly.
        //noinspection unchecked
        Mockito.reset(this.backingStore);

        this.state = Mockito.spy(createState(backingStore));
    }

    protected MutableStateBase<String, String> createState(
            @NonNull final Map<String, DummyMerkleNode> merkleMap) {
        return new DummyMutableState(STATE_KEY, merkleMap);
    }

    @Nested
    @DisplayName("getForModify")
    final class GetForModifyTest {

        /**
         * Using getForModify on an unknown value MUST return null, and it MUST record the key in
         * "readKeys", since nothing was modified as a result of this call, but it was read. When
         * committed, nothing should change on the backing store.
         */
        @Test
        @DisplayName("Call on an unknown value returns null and is a read key")
        void getForModifyUnknownValue() {
            // Before running getForModify, readKeys and modifiedKeys must be empty
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Verify the value is null
            final var opt = state.getForModify(UNKNOWN_KEY);
            assertNotNull(opt);
            assertTrue(opt.isEmpty());
            assertTrue(state.readKeys().contains(UNKNOWN_KEY));

            // Note, it is NOT a modified key, because it didn't exist in the underlying data store
            assertFalse(state.modifiedKeys().contains(UNKNOWN_KEY));

            // Commit should cause no changes to the backing store
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * Using getForModify on an unknown value twice MUST only hit the backend data source once.
         */
        @Test
        @DisplayName("Call on an unknown value twice hits the backend only once")
        void getForModifyUnknownValueTwice() {
            // Call getForModify twice and verify the spied on backingStore was called only once.
            state.getForModify(KNOWN_KEY_1);
            state.getForModify(KNOWN_KEY_1);
            Mockito.verify(backingStore, Mockito.times(1)).get(KNOWN_KEY_1);
        }

        /**
         * Using getForModify on an existing key MUST return the associated value, and it MUST
         * record the key in "readKeys". When committed, nothing is changed on the backing store,
         * since getForModify only signals to the backend that something should be prepared to be
         * modified, but the actual modification is done through "put".
         */
        @Test
        @DisplayName("Call on an existing value gets it and marks it as read and modified")
        void getForModifyKnownValue() {
            // Before running getForModify, we have a certain DummyMerkleNode in our backing store.
            // After we call getForModify, it should be copied, so we should have a different
            // instance,
            // and we should get the value from the instance.
            final var originalMerkle = backingStore.get(KNOWN_KEY_1);

            // Before running getForModify, readKeys and modifiedKeys must be empty
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Verify the value is what we expected
            final var opt = state.getForModify(KNOWN_KEY_1);
            assertNotNull(opt);
            assertTrue(opt.isPresent());
            assertEquals(KNOWN_VALUE_1, opt.get());

            // Verify the backing store now has a new (fake) merkle node instance
            assertNotSame(originalMerkle, backingStore.get(KNOWN_KEY_1));

            // Verify the key used in lookup is now in readKeys and modifiedKeys
            assertTrue(state.readKeys().contains(KNOWN_KEY_1));
            assertTrue(state.modifiedKeys().isEmpty());

            // Commit should cause no changes to the backing store
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * Using getForModify on an existing key more than once MUST return the associated value,
         * and it MUST record the key in "readKeys" where it is present only once.
         */
        @Test
        @DisplayName("Called twice on an existing value")
        void getForModifyKnownValueTwice() {
            // Before running getForModify, readKeys and modifiedKeys must be empty
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Verify the value is what we expected
            for (int i = 0; i < 2; i++) {
                final var opt = state.getForModify(KNOWN_KEY_1);
                assertNotNull(opt);
                assertTrue(opt.isPresent());
                assertEquals(KNOWN_VALUE_1, opt.get());

                // Verify the key used in lookup is now in readKeys and modifiedKeys
                assertTrue(state.readKeys().contains(KNOWN_KEY_1));
                assertFalse(state.modifiedKeys().contains(KNOWN_KEY_1));
            }
        }

        /**
         * Using getForModify on a key that has already been "put" MUST return the associated value
         * that was previously put. The key MUST NOT be in "readKeys", and MUST be in
         * "modifiedKeys".
         *
         * <p>Given transaction T1 that modifies the value for some key K, and transaction T2 that
         * *never* reads the value of K but always does a {@code put(K, V)}, and then subsequently
         * does a {@code getForModify}, it doesn't matter <b>at all</b> what T1 did with the value
         * for K. T2 is just going to overwrite it. So in this flow, it we should not register K as
         * a "readKey" because it is never actually read from the backing data source.
         *
         * <p>When committed, the most current "put" value is sent to the backing store.
         */
        @Test
        @DisplayName("A key that was put (and did not exist before) first")
        void getForModifyAfterPut() {
            // Put some value
            state.put("C", "Cherry");

            // Before running getForModify, readKeys is empty and modified keys contains "C"
            assertTrue(state.readKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains("C"));

            // Verify the value is what we expected
            final var opt = state.getForModify("C");
            assertNotNull(opt);
            assertTrue(opt.isPresent());
            assertEquals("Cherry", opt.get());

            // Verify the key used in lookup is not in readKeys and is in modifiedKeys
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains("C"));
            assertEquals(1, state.modifiedKeys().size());

            // Commit should cause the most recent put value to be saved
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource("C", "Cherry");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * Using getForModify on a key that has already been "put", where that key DID exist in the
         * backend already, MUST return the associated value that was previously put. The key MUST
         * NOT be in "readKeys", and MUST be in "modifiedKeys". The reasoning here is the same as
         * with {@link #getForModifyAfterPut()}.
         *
         * <p>When committed, the most current "put" value is sent to the backing store.
         */
        @Test
        @DisplayName("A key that was put (and did exist before) first")
        void getForModifyAfterPutOnExistingKey() {
            // Put some value
            state.put(KNOWN_KEY_2, "Bear");

            // Before running getForModify, readKeys is empty and modified keys contains KNOWN_KEY_2
            assertTrue(state.readKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));

            // Verify the value is what we expected
            final var opt = state.getForModify(KNOWN_KEY_2);
            assertNotNull(opt);
            assertTrue(opt.isPresent());
            assertEquals("Bear", opt.get());

            // Verify the key used in lookup is not in readKeys and is in modifiedKeys
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));
            assertEquals(1, state.modifiedKeys().size());

            // Commit should cause the most recent put value to be saved
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(KNOWN_KEY_2, "Bear");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * Using getForModify on a key that has already been "removed" MUST return null. The key
         * MUST be in "modifiedKeys", but must not be in "readKeys". When committed, the key will be
         * removed.
         */
        @Test
        @DisplayName("A key that has been previously removed will return null for the value")
        void getForModifyAfterRemove() {
            // Remove some value
            state.remove(KNOWN_KEY_1);

            // Before running getForModify, readKeys is empty and modified keys contains KNOWN_KEY_1
            assertTrue(state.readKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));

            // Verify the value is null
            final var opt = state.getForModify(KNOWN_KEY_1);
            assertNotNull(opt);
            assertTrue(opt.isEmpty());

            // Verify the key used in lookup is not in readKeys and is in modifiedKeys
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));
            assertEquals(1, state.modifiedKeys().size());

            // Commit should cause the value to be removed
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(KNOWN_KEY_1);
        }
    }

    @Nested
    @DisplayName("put")
    final class PutTest {
        /**
         * If the key is unknown in the backing store, then it will now be recorded as a
         * modification. It will not be listed as a "read" key. When committed, the new value should
         * be saved.
         */
        @Test
        @DisplayName("Put a key that does not already exist in the backing store")
        void putNew() {
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            state.put("C", "Cherry");
            assertTrue(state.readKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains("C"));

            // Commit should cause the value to be added
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource("C", "Cherry");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * Even if the key is known already in the backing store, a "put" will record the
         * modification with no respect to what is in the backing store already. It will not be a
         * "read" key.
         */
        @Test
        @DisplayName("Put a key that already exists in the backing store")
        void putExisting() {
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            state.put(KNOWN_KEY_1, "Aardvark");
            assertTrue(state.readKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));

            // Commit should cause the value to be updated
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(KNOWN_KEY_1, "Aardvark");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * If the key has been previously part of "getForModify", and then we "put", then the key
         * will still be listed as a "read" key, and will also be a modified key.
         */
        @Test
        @DisplayName("Put a key that was previously 'getForModify'")
        void putAfterGetForModify() {
            state.getForModify(KNOWN_KEY_2);
            assertFalse(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());
            assertTrue(state.readKeys().contains(KNOWN_KEY_2));

            state.put(KNOWN_KEY_2, "Bear");
            assertTrue(state.readKeys().contains(KNOWN_KEY_2));
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));

            // Commit should cause the value to be updated
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(KNOWN_KEY_2, "Bear");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /** Calling put twice is idempotent. */
        @Test
        @DisplayName("Put a key twice")
        void putTwice() {
            state.put(KNOWN_KEY_2, "Bear");
            assertTrue(state.readKeys().isEmpty());
            assertFalse(state.modifiedKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));

            state.put(KNOWN_KEY_2, "Ballast");
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));

            // Commit should cause the value to be updated to the latest value
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(KNOWN_KEY_2, "Ballast");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * If a key has been removed, and then is "put" back, it should ultimately be "put" and not
         * removed.
         */
        @Test
        @DisplayName("Put a key after having removed it")
        void putAfterRemove() {
            state.remove(KNOWN_KEY_2);
            assertTrue(state.readKeys().isEmpty());
            assertFalse(state.modifiedKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));

            state.put(KNOWN_KEY_2, "Bear");
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));

            // Commit should cause the value to be updated to the latest value
            state.commit();
            Mockito.verify(state, Mockito.times(1))
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(KNOWN_KEY_2, "Bear");
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }
    }

    @Nested
    @DisplayName("remove")
    final class RemoveTest {
        /**
         * If the key is unknown in the backing store, and is removed, it should be a no-op, but
         * MUST be listed in the set of modified keys. This is because it may be in pre-handle at
         * the time "remove" is called that there is no underlying data, but then later during
         * handle there actually is, and if we didn't record this as a modification, we wouldn't
         * have removed the value in the end!
         *
         * <p>On commit, the remove method MUST be called on the backing store.
         */
        @Test
        @DisplayName("Remove an unknown key")
        void removeUnknown() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove an unknown key
            state.remove("C");

            // "readKeys" is still empty, but "modifiedKeys" has the new key
            assertTrue(state.readKeys().isEmpty());
            assertFalse(state.modifiedKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains("C"));

            // Commit should cause the value to be removed (even though it doesn't actually exist in
            // the backend)
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource("C");
        }

        /**
         * If the key is known in the backing store, and is removed, it MUST be listed in the set of
         * modified keys.
         *
         * <p>On commit, the remove method MUST be called on the backing store.
         */
        @Test
        @DisplayName("Remove a known key")
        void removeKnown() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key
            state.remove(KNOWN_KEY_1);

            // "readKeys" is still empty, but "modifiedKeys" has the key
            assertTrue(state.readKeys().isEmpty());
            assertFalse(state.modifiedKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));

            // Commit should cause the value to be removed
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(KNOWN_KEY_1);
        }

        /**
         * If the key is removed twice, the remove call is idempotent.
         *
         * <p>On commit, the remove method MUST be called on the backing store just once.
         */
        @Test
        @DisplayName("Remove a known key twice")
        void removeTwice() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key
            for (int i = 0; i < 2; i++) {
                state.remove(KNOWN_KEY_2);

                // "readKeys" is still empty, but "modifiedKeys" has the key
                assertTrue(state.readKeys().isEmpty());
                assertFalse(state.modifiedKeys().isEmpty());
                assertEquals(1, state.modifiedKeys().size());
                assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));
            }

            // Commit should cause the value to be removed
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(KNOWN_KEY_2);
        }

        /**
         * If the key was previously "get" and is then removed, it MUST be present in both the
         * "readKeys" and "modified" keys.
         *
         * <p>On commit, the remove method MUST be called on the backing store.
         */
        @Test
        @DisplayName("Remove a known key after get")
        void removeAfterGet() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key after getting it
            assertEquals(KNOWN_VALUE_1, state.get(KNOWN_KEY_1).orElse(""));
            state.remove(KNOWN_KEY_1);

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertEquals(1, state.readKeys().size());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.readKeys().contains(KNOWN_KEY_1));
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(KNOWN_KEY_1);
        }

        /**
         * If the key was previously "get" and is then removed, and was unknown, it MUST be present
         * in both the "readKeys" and "modified" keys.
         *
         * <p>On commit, the remove method MUST be called on the backing store.
         */
        @Test
        @DisplayName("Remove a unknown key after get")
        void removeAfterGetUnknown() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key after getting it
            assertTrue(state.get("C").isEmpty());
            state.remove("C");

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertEquals(1, state.readKeys().size());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.readKeys().contains("C"));
            assertTrue(state.modifiedKeys().contains("C"));

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource("C");
        }

        /**
         * If the key was previously "getForModify" and is then removed, it MUST be present in both
         * the "readKeys" and "modified" keys.
         *
         * <p>On commit, the remove method MUST be called on the backing store.
         */
        @Test
        @DisplayName("Remove a known key after getForModify")
        void removeAfterGetForModify() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key after getting it
            assertEquals(KNOWN_VALUE_1, state.getForModify(KNOWN_KEY_1).orElse(""));
            state.remove(KNOWN_KEY_1);

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertEquals(1, state.readKeys().size());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.readKeys().contains(KNOWN_KEY_1));
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(KNOWN_KEY_1);
        }

        /**
         * If the key was previously "getForModify" and is then removed, and the key was not in the
         * backing store, it MUST be present in both the "readKeys" and "modified" keys.
         *
         * <p>On commit, the remove method MUST be called on the backing store.
         */
        @Test
        @DisplayName("Remove a unknown key after getForModify")
        void removeAfterGetForModifyUnknown() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key after getting it
            assertTrue(state.getForModify("C").isEmpty());
            state.remove("C");

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertEquals(1, state.readKeys().size());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.readKeys().contains("C"));
            assertTrue(state.modifiedKeys().contains("C"));

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource("C");
        }

        /**
         * If the key was previously "put" and is then removed, it MUST be present in only
         * "modified" keys.
         *
         * <p>On commit, the remove method MUST be called on the backing store and not the put
         * method.
         */
        @Test
        @DisplayName("Remove a known key after put")
        void removeAfterPut() {
            // Initially everything is clean
            assertTrue(state.readKeys().isEmpty());
            assertTrue(state.modifiedKeys().isEmpty());

            // Remove a known key after putting it
            state.put(KNOWN_KEY_1, "Aardvark");
            state.remove(KNOWN_KEY_1);

            // "readKeys" is not populated, and "modifiedKeys" has the key
            assertTrue(state.readKeys().isEmpty());
            assertEquals(1, state.modifiedKeys().size());
            assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never())
                    .putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(KNOWN_KEY_1);
        }
    }

    @Test
    @DisplayName("After making many modifications and reads, reset the state")
    void reset() {
        assertTrue(state.get("C").isEmpty());
        assertEquals(KNOWN_VALUE_1, state.get(KNOWN_KEY_1).orElse(null));
        assertTrue(state.getForModify("D").isEmpty());
        assertEquals(KNOWN_VALUE_2, state.get(KNOWN_KEY_2).orElse(null));
        state.put(KNOWN_KEY_1, "Aardvark");
        state.put("E", "Elephant");
        state.remove(KNOWN_KEY_2);
        state.remove("F");

        assertEquals(4, state.readKeys().size());
        assertTrue(state.readKeys().contains(KNOWN_KEY_1));
        assertTrue(state.readKeys().contains(KNOWN_KEY_2));
        assertTrue(state.readKeys().contains("C"));
        assertTrue(state.readKeys().contains("D"));

        assertEquals(4, state.modifiedKeys().size());
        assertTrue(state.modifiedKeys().contains(KNOWN_KEY_1));
        assertTrue(state.modifiedKeys().contains(KNOWN_KEY_2));
        assertTrue(state.modifiedKeys().contains("E"));
        assertTrue(state.modifiedKeys().contains("F"));

        state.reset();
        assertTrue(state.readKeys().isEmpty());
        assertTrue(state.modifiedKeys().isEmpty());
    }

    protected static class DummyMutableState extends MutableStateBase<String, String> {
        private final Map<String, DummyMerkleNode> merkleMap;

        DummyMutableState(
                @NonNull String stateKey, @NonNull Map<String, DummyMerkleNode> merkleMap) {
            super(stateKey);
            this.merkleMap = merkleMap;
        }

        @Override
        protected String readFromDataSource(@NonNull String key) {
            final var val = merkleMap.get(key);
            return val == null ? null : val.value;
        }

        @Override
        protected String getForModifyFromDataSource(@NonNull String key) {
            final var merkle = merkleMap.get(key);
            if (merkle != null) {
                final var copy = merkle.copy();
                merkleMap.put(key, copy);
                return copy.value;
            }
            return null;
        }

        @Override
        protected void putIntoDataSource(@NonNull String key, @NonNull String value) {
            final var merkle = merkleMap.get(key);
            if (merkle != null) {
                merkle.value = value;
            } else {
                merkleMap.put(key, new DummyMerkleNode(value));
            }
        }

        @Override
        protected void removeFromDataSource(@NonNull String key) {
            merkleMap.remove(key);
        }
    }

    protected static final class DummyMerkleNode {
        private String value;

        DummyMerkleNode(String value) {
            this.value = value;
        }

        DummyMerkleNode copy() {
            final var copy = new DummyMerkleNode(value);
            return copy;
        }
    }
}
