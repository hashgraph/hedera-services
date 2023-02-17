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

package com.hedera.node.app.spi.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for the base class for all mutable states. All non-abstract methods in this class are
 * final, so we can test here very thoroughly with a dummy {@link WritableKVStateBase}.
 *
 * <p>In this test, we create a backing store with only {(A=APPLE),(B=BANANA)}. We then have a
 * series of tests that will replace the values for A, B, or remove them, or add new values.
 */
class WritableKVStateBaseTest extends ReadableKVStateBaseTest {
    private static final String NUM_ITERATIONS_ARG = "WritableKVStateBaseTest.DeterministicUpdates.numIterations";
    protected WritableKVStateBase<String, String> state;

    @Override
    protected Map<String, String> createBackingMap() {
        final var map = new HashMap<String, String>();
        this.backingMap = Mockito.spy(map);
        backingMap.put(A_KEY, APPLE);
        backingMap.put(B_KEY, BANANA);

        // This method call is really unexpected. I tried to add both keys to the map before
        // creating the spy, and it seems to me that should have worked, but it doesn't, the
        // spy doesn't know anything about the values put into the map. Instead, I had to
        // add them afterward. But then I need to reset the spy, so all my verify methods are
        // right. Kind of bogus, honestly.
        //noinspection unchecked
        Mockito.reset(this.backingMap);

        return backingMap;
    }

    protected WritableKVStateBase<String, String> createFruitState(@NonNull final Map<String, String> map) {
        this.state = Mockito.spy(new MapWritableKVState<>(FRUIT_STATE_KEY, map));
        return state;
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Verify the value is null
            final var value = state.getForModify(UNKNOWN_KEY);
            assertThat(value).isNull();
            assertThat(state.readKeys()).contains(UNKNOWN_KEY);
            assertThat(state.readKeys()).contains(UNKNOWN_KEY);

            // Note, it is NOT a modified key, because it didn't exist in the underlying data store
            assertThat(state.modifiedKeys()).doesNotContain(UNKNOWN_KEY);

            // Commit should cause no changes to the backing store
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * Using getForModify on an unknown value twice MUST only hit the backend data source once.
         */
        @Test
        @DisplayName("Call on an unknown value twice hits the backend only once")
        void getForModifyUnknownValueTwice() {
            // Call getForModify twice and verify the spied on backingMap was called only once.
            state.getForModify(A_KEY);
            state.getForModify(A_KEY);
            Mockito.verify(backingMap, Mockito.times(1)).get(A_KEY);
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
            // Before running getForModify, readKeys and modifiedKeys must be empty
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Verify the value is what we expected
            final var value = state.getForModify(A_KEY);
            assertThat(value).isEqualTo(APPLE);

            // Verify the key used in lookup is now in readKeys but NOT modifiedKeys
            assertThat(state.readKeys()).contains(A_KEY);
            assertThat(state.modifiedKeys()).isEmpty();

            // Commit should cause no changes to the backing store
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Verify the value is what we expected
            for (int i = 0; i < 2; i++) {
                final var value = state.getForModify(A_KEY);
                assertThat(value).isEqualTo(APPLE);

                // Verify the key used in lookup is now in readKeys and NOT in modifiedKeys
                assertThat(state.readKeys()).contains(A_KEY);
                assertThat(state.modifiedKeys()).doesNotContain(A_KEY);
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
         * for K. T2 is just going to overwrite it. So in this flow, it should not register K as a
         * "readKey" because it is never actually read from the backing data source.
         *
         * <p>When committed, the most current "put" value is sent to the backing store.
         */
        @Test
        @DisplayName("A key that was put and did not exist before")
        void getForModifyAfterPut() {
            // Put some value
            state.put(C_KEY, CHERRY);

            // Before running getForModify, readKeys is empty and modified keys contains C_KEY
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(C_KEY);

            // Verify the value is what we expected
            final var value = state.getForModify(C_KEY);
            assertThat(value).isEqualTo(CHERRY);

            // Verify the key used in lookup is not in readKeys and is in modifiedKeys
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).contains(C_KEY);
            assertThat(state.modifiedKeys()).hasSize(1);

            // Commit should cause the most recent put value to be saved
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(C_KEY, CHERRY);
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
            state.put(B_KEY, BLACKBERRY);

            // Before running getForModify, readKeys is empty and modified keys contains B_KEY
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(B_KEY);

            // Verify the value is what we expected
            final var value = state.getForModify(B_KEY);
            assertThat(value).isEqualTo(BLACKBERRY);

            // Verify the key used in lookup is not in readKeys and is in modifiedKeys
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).contains(B_KEY);
            assertThat(state.modifiedKeys()).hasSize(1);

            // Commit should cause the most recent put value to be saved
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(B_KEY, BLACKBERRY);
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
            state.remove(A_KEY);

            // Before running getForModify, readKeys is empty and modified keys contains A_KEY
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(A_KEY);

            // Verify the value is null
            final var value = state.getForModify(A_KEY);
            assertThat(value).isNull();

            // Verify the key used in lookup is not in readKeys and is in modifiedKeys
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).contains(A_KEY);
            assertThat(state.modifiedKeys()).hasSize(1);

            // Commit should cause the value to be removed
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            state.put(C_KEY, CHERRY);
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(C_KEY);

            // We should be able to "get" the modification
            assertThat(state.get(C_KEY)).isEqualTo(CHERRY);
            assertThat(state.readKeys()).isEmpty();

            // Commit should cause the value to be added
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(C_KEY, CHERRY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            state.put(A_KEY, ACAI);
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(A_KEY);

            // Commit should cause the value to be updated
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(A_KEY, ACAI);
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * If the key has been previously part of "getForModify", and then we "put", then the key
         * will still be listed as a "read" key, and will also be a modified key.
         */
        @Test
        @DisplayName("Put a key that was previously 'getForModify'")
        void putAfterGetForModify() {
            state.getForModify(B_KEY);
            assertThat(state.readKeys()).isNotEmpty();
            assertThat(state.modifiedKeys()).isEmpty();
            assertThat(state.readKeys()).contains(B_KEY);

            state.put(B_KEY, BLACKBERRY);
            assertThat(state.readKeys()).contains(B_KEY);
            assertThat(state.modifiedKeys()).contains(B_KEY);

            // Commit should cause the value to be updated
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(B_KEY, BLACKBERRY);
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /** Calling put twice is idempotent. */
        @Test
        @DisplayName("Put a key twice")
        void putTwice() {
            state.put(B_KEY, BLACKBERRY);
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isNotEmpty();
            assertThat(state.modifiedKeys()).contains(B_KEY);

            state.put(B_KEY, BLUEBERRY);
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).contains(B_KEY);

            // Commit should cause the value to be updated to the latest value
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(B_KEY, BLUEBERRY);
            Mockito.verify(state, Mockito.never()).removeFromDataSource(Mockito.anyString());
        }

        /**
         * If a key has been removed, and then is "put" back, it should ultimately be "put" and not
         * removed.
         */
        @Test
        @DisplayName("Put a key after having removed it")
        void putAfterRemove() {
            state.remove(B_KEY);
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isNotEmpty();
            assertThat(state.modifiedKeys()).contains(B_KEY);

            state.put(B_KEY, BLACKBERRY);
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).contains(B_KEY);

            // Commit should cause the value to be updated to the latest value
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(B_KEY, BLACKBERRY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove an unknown key
            state.remove(C_KEY);

            // "readKeys" is still empty, but "modifiedKeys" has the new key
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isNotEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(C_KEY);

            // Commit should cause the value to be removed (even though it doesn't actually exist in
            // the backend)
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(C_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key
            state.remove(A_KEY);

            // "readKeys" is still empty, but "modifiedKeys" has the key
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isNotEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(A_KEY);

            // Commit should cause the value to be removed
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key
            for (int i = 0; i < 2; i++) {
                state.remove(B_KEY);

                // "readKeys" is still empty, but "modifiedKeys" has the key
                assertThat(state.readKeys()).isEmpty();
                assertThat(state.modifiedKeys()).isNotEmpty();
                assertThat(state.modifiedKeys()).hasSize(1);
                assertThat(state.modifiedKeys()).contains(B_KEY);
            }

            // Commit should cause the value to be removed
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(B_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key after getting it
            assertThat(state.get(A_KEY)).isEqualTo(APPLE);
            state.remove(A_KEY);

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertThat(state.readKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.readKeys()).contains(A_KEY);
            assertThat(state.modifiedKeys()).contains(A_KEY);

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key after getting it
            assertThat(state.get(C_KEY)).isNull();
            state.remove(C_KEY);

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertThat(state.readKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.readKeys()).contains(C_KEY);
            assertThat(state.modifiedKeys()).contains(C_KEY);

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(C_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key after getting it
            assertThat(state.get(A_KEY)).isEqualTo(APPLE);
            state.remove(A_KEY);

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertThat(state.readKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.readKeys()).contains(A_KEY);
            assertThat(state.modifiedKeys()).contains(A_KEY);

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key after getting it
            assertThat(state.getForModify(C_KEY)).isNull();
            state.remove(C_KEY);

            // "readKeys" is now populated, and "modifiedKeys" has the key
            assertThat(state.readKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.readKeys()).contains(C_KEY);
            assertThat(state.modifiedKeys()).contains(C_KEY);

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(C_KEY);
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
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Remove a known key after putting it
            state.put(A_KEY, ACAI);
            state.remove(A_KEY);

            // "readKeys" is not populated, and "modifiedKeys" has the key
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).hasSize(1);
            assertThat(state.modifiedKeys()).contains(A_KEY);

            // Commit should cause the value to be removed but not "put"
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(Mockito.anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
        }
    }

    @Nested
    @DisplayName("iterator")
    final class IteratorTest {
        @Test
        @DisplayName("Iterator on an empty state returns false to `hasNext`")
        void hasNextFalseOnEmptyState() {
            // Empty out the state first
            state.remove(A_KEY);
            state.remove(B_KEY);
            state.commit();
            state.reset();

            // OK, we have an empty state with an empty backend. So we can test this.
            final var itr = state.keys();
            assertThat(itr.hasNext()).isFalse();
        }

        @Test
        @DisplayName("Iterator on an empty state throws exception on `next`")
        void nextThrowsOnEmptyState() {
            // Empty out the state first
            state.remove(A_KEY);
            state.remove(B_KEY);
            state.commit();
            state.reset();

            // OK, we have an empty state with an empty backend. So we can test this.
            final var itr = state.keys();
            assertThatThrownBy(itr::next).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("Iteration after `remove` and before `commit` includes modifications")
        void testIterationAfterRemove() {
            state.remove(B_KEY);
            assertThat(state.keys()).toIterable().containsExactlyInAnyOrder(A_KEY);
        }

        @Test
        @DisplayName("Iteration after `remove` and `commit` includes modifications")
        void testIterationAfterRemoveAndCommit() {
            state.remove(B_KEY);
            state.commit();
            assertThat(state.keys()).toIterable().contains(A_KEY);
        }

        @Test
        @DisplayName("Iteration after `put` and before `commit` includes modifications")
        void testIterationAfterPut() {
            state.put(C_KEY, CHERRY);
            assertThat(state.keys()).toIterable().contains(A_KEY, B_KEY, C_KEY);
        }

        @Test
        @DisplayName("Iteration after `put` and `commit` includes modifications")
        void testIterationAfterPutAndCommit() {
            state.put(C_KEY, CHERRY);
            state.commit();
            assertThat(state.keys()).toIterable().contains(A_KEY, B_KEY, C_KEY);
        }

        @Test
        @DisplayName("Iteration over keys with all modifications until the iteration concludes")
        void iterateOverAllChanges() {
            state.put(C_KEY, CHERRY);
            state.put(D_KEY, DATE);
            state.put(E_KEY, EGGPLANT);
            state.put(F_KEY, FIG);
            state.remove(A_KEY);
            state.remove(E_KEY);
            state.put(E_KEY, ELDERBERRY);
            state.remove(F_KEY);

            final var itr = state.keys();
            final var foundKeys = new HashSet<String>();
            for (int i = 0; i < 4; i++) {
                assertThat(itr.hasNext()).isTrue();
                foundKeys.add(itr.next());
            }

            assertThat(itr.hasNext()).isFalse();
            assertThat(foundKeys).containsExactlyInAnyOrder(B_KEY, C_KEY, D_KEY, E_KEY);
        }

        @Test
        @DisplayName("Changes made after the iterator is created are not considered")
        void iterateAfterChangesAreMade() {
            final var itr = state.keys();
            state.put(C_KEY, CHERRY);
            state.remove(A_KEY);
            assertThat(itr).toIterable().containsExactlyInAnyOrder(A_KEY, B_KEY);
        }
    }

    @Test
    @DisplayName("After making many modifications and reads, reset the state")
    void reset() {
        assertThat(state.get(C_KEY)).isNull();
        assertThat(state.get(A_KEY)).isEqualTo(APPLE);
        assertThat(state.get(D_KEY)).isNull();
        assertThat(state.get(B_KEY)).isEqualTo(BANANA);
        state.put(A_KEY, ACAI);
        state.put(E_KEY, ELDERBERRY);
        state.remove(B_KEY);
        state.remove(F_KEY);

        assertThat(state.readKeys()).hasSize(4);
        assertThat(state.readKeys()).contains(A_KEY);
        assertThat(state.readKeys()).contains(B_KEY);
        assertThat(state.readKeys()).contains(C_KEY);
        assertThat(state.readKeys()).contains(D_KEY);

        assertThat(state.modifiedKeys()).hasSize(4);
        assertThat(state.modifiedKeys()).contains(A_KEY);
        assertThat(state.modifiedKeys()).contains(B_KEY);
        assertThat(state.modifiedKeys()).contains(E_KEY);
        assertThat(state.modifiedKeys()).contains(F_KEY);

        state.reset();
        assertThat(state.readKeys()).isEmpty();
        assertThat(state.modifiedKeys()).isEmpty();
    }

    /**
     * It is essential that EVERY node causes the exact same mutations on the backend data store in
     * the exact same order. If we have a map containing all modifications, it may be that iterating
     * the map on different nodes produces mutations in a different order, since maps do not
     * guarantee deterministic ordering (unless you use some kind of sorted map).
     *
     * <p>This kind of error is very hard to test directly, so we use a "hammer" test to just do
     * many iterations on many threads concurrently to try to cause it to fail. If the test
     * succeeds, it does not mean there is no bug, but if the test fails, it means there is
     * DEFINITELY a bug. This test may be flaky, but if it ever fails, you must investigate!
     */
    @Test
    @DisplayName("Updates to the underlying data source are deterministic")
    @Tag("Hammer")
    void deterministicUpdates() {
        // We will execute 'numIterations' iterations. This is parameterized with a system property
        // so that we may dial the number up or down for certain types of tests (for example,
        // nightly vs. continuous). Each iteration will construct a list of modifications (add,
        // remove, modify). We will then create 39 threads each with their own WritableKVStateBase.
        // Each will apply the modifications and commit them. We will then join back and verify
        // that the order in which modifications were applied is identical. The hope is that if
        // there is any non-determinism, we will tease it out by hammering it in this way.
        final int numIterations = Integer.getInteger(NUM_ITERATIONS_ARG, 100);
        final var numMutations = 20;
        final var numThreads = 39;
        final var executors = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numIterations; i++) {
            // Contains lambdas that will mutate the map in some way, and then populate the
            // mutation list with random mutations (puts and removes)
            List<Consumer<WritableKVStateBase<Integer, String>>> mutations = new ArrayList<>();
            for (int j = 0; j < numMutations; j++) {
                final var key = random().nextInt(100);
                if (random().nextInt(10) < 8) {
                    final var randomSuffix = randomString(10);
                    mutations.add(state -> state.put(key, randomSuffix));
                } else {
                    mutations.add(state -> state.remove(key));
                }
            }

            // Now, spin up the threads. Each one will have its own unique state. The state
            // is special in that it keeps track of the order in which remove and add calls
            // were made to it, so we can compare those later.
            final var latch = new CountDownLatch(numThreads);
            final var mutationOrders = new ArrayList<List<Integer>>();
            for (int t = 0; t < numThreads; t++) {
                final var state = new MapWritableKVState<Integer, String>(FRUIT_STATE_KEY) {
                    private final List<Integer> keys = new ArrayList<>();

                    @Override
                    protected void putIntoDataSource(@NonNull Integer key, @NonNull String value) {
                        keys.add(key);
                        super.putIntoDataSource(key, value);
                    }

                    @Override
                    protected void removeFromDataSource(@NonNull Integer key) {
                        keys.add(key);
                        super.removeFromDataSource(key);
                    }
                };

                // Add a bunch of random junk to the state and then reset it.
                // If the backend data structures are not able to maintain deterministic
                // behavior in the face of this (like a HashMap), then the test will fail.
                for (int j = 0; j < random().nextInt(100, 1000); j++) {
                    state.put(random().nextInt(), randomString(100));
                }
                state.reset();

                mutationOrders.add(state.keys);
                executors.execute(() -> {
                    for (var mutator : mutations) {
                        mutator.accept(state);
                    }
                    state.commit();
                    latch.countDown();
                });
            }

            try {
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    fail("Failed to complete task in a reasonable time");
                }
            } catch (InterruptedException e) {
                fail("Test was interrupted");
            }

            // Now we verify that every single "mutationOrder" array is the same
            List<Integer> mutationOrder = mutationOrders.get(0);
            for (final var mo : mutationOrders) {
                assertThat(mo).containsExactlyElementsOf(mutationOrder);
            }
        }
    }
}
