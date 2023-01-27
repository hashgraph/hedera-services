/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WritableSingletonStateBaseTest extends SingletonStateTestBase {

    @Override
    protected WritableSingletonStateBase<String> createState() {
        return new WritableSingletonStateBase<>(
                COUNTRY_STATE_KEY, backingStore::get, backingStore::set);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTest {
        @Test
        @DisplayName("Constructor throws NPE if stateKey is null")
        void nullStateKey() {
            //noinspection DataFlowIssue
            assertThatThrownBy(
                            () ->
                                    new WritableSingletonStateBase<>(
                                            null, () -> AUSTRALIA, val -> {}))
                    .isInstanceOf(NullPointerException.class);
        }

        /** Make sure the constructor is holding onto the state key properly */
        @Test
        @DisplayName("The state key must match what was provided in the constructor")
        void testStateKey() {
            final var state =
                    new WritableSingletonStateBase<>(COUNTRY_STATE_KEY, () -> AUSTRALIA, val -> {});
            assertThat(state.getStateKey()).isEqualTo(COUNTRY_STATE_KEY);
        }

        @Test
        @DisplayName("Constructor throws NPE if backingStoreAccessor is null")
        void nullAccessor() {
            //noinspection DataFlowIssue
            assertThatThrownBy(
                            () ->
                                    new WritableSingletonStateBase<>(
                                            COUNTRY_STATE_KEY, null, val -> {}))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Constructor throws NPE if backingStoreMutator is null")
        void nullMutator() {
            //noinspection DataFlowIssue
            assertThatThrownBy(
                            () ->
                                    new WritableSingletonStateBase<>(
                                            COUNTRY_STATE_KEY, () -> AUSTRALIA, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Modified Flag Tests")
    class ModifiedTest {
        @Test
        @DisplayName("`modified` is false on a new state")
        void initialValueForModified() {
            final var state = createState();
            assertThat(state.isModified()).isFalse();
        }

        @Test
        @DisplayName("`modified` is true after `put`")
        void modifiedAfterPut() {
            final var state = createState();
            state.put(BRAZIL);
            assertThat(state.isModified()).isTrue();
        }

        @Test
        @DisplayName("`modified` is not impacted by `get`")
        void readDoesNotModify() {
            final var state = createState();
            assertThat(state.get()).isEqualTo(AUSTRALIA);
            assertThat(state.isModified()).isFalse();
        }
    }

    @Nested
    @DisplayName("Put Tests")
    class PutTest {
        @Test
        @DisplayName("`get` reads `put` value")
        void getAfterPut() {
            final var state = createState();
            assertThat(state.get()).isEqualTo(AUSTRALIA);
            state.put(BRAZIL);
            assertThat(state.get()).isEqualTo(BRAZIL);
        }
    }

    @Nested
    @DisplayName("Commit Tests")
    class CommitTest {
        @Test
        @DisplayName("`commit` of clean state is no-op")
        void commitClean() {
            final var state = createState();
            state.commit();
            assertThat(backingStore.get()).isEqualTo(AUSTRALIA);
        }

        @Test
        @DisplayName("`commit` of newly put value stores in backing store")
        void commitDirty() {
            final var state = createState();
            state.put(FRANCE);
            state.commit();
            assertThat(backingStore.get()).isEqualTo(FRANCE);
        }

        @Test
        @DisplayName("`commit` of newly put null stores in backing store")
        void commitNull() {
            final var state = createState();
            state.put(null);
            state.commit();
            assertThat(backingStore.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Reset Tests")
    class ResetTest {
        @Test
        @DisplayName("`modified` is cleared on reset")
        void modifiedCleared() {
            final var state = createState();
            state.put(BRAZIL);
            assertThat(state.isModified()).isTrue();
            state.reset();
            assertThat(state.isModified()).isFalse();
        }

        @Test
        @DisplayName("modified value is cleared on reset")
        void valueCleared() {
            final var state = createState();
            state.put(BRAZIL);
            state.reset();
            assertThat(state.get()).isEqualTo(AUSTRALIA);
        }
    }

    /**
     * This specific behavior should never show up in actual code, because all states associated
     * with a transaction are destroyed after having been committed (or in a nested/wrapped
     * scenario, we shouldn't be updating the backing data while a wrapped data is being used).
     * However, in such a situation, the right behavior is to show the updated values in the backing
     * store, unless the value has been overridden in this state.
     */
    @Test
    @DisplayName("State sees changes committed to backend if not modified on the state")
    void dirtyRead() {
        backingStore.set(CHAD);
        final var state = createState();
        assertThat(state.get()).isEqualTo(CHAD);
        backingStore.set(DENMARK);
        assertThat(state.get()).isEqualTo(DENMARK); // Sees change
        state.put(ESTONIA);
        assertThat(state.get()).isEqualTo(ESTONIA);
        backingStore.set(FRANCE);
        assertThat(state.get()).isEqualTo(ESTONIA); // Does not see change
    }
}
