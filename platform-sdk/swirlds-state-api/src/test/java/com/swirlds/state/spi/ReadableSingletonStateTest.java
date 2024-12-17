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

package com.swirlds.state.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.state.test.fixtures.StateTestBase;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ReadableSingletonStateTest extends StateTestBase {

    protected AtomicReference<String> backingStore = new AtomicReference<>(AUSTRALIA);

    ReadableSingletonStateBase<String> createState() {
        return new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, () -> backingStore.get());
    }

    /**
     * When we are asked to get an unknown item (something not in the backing store), the {@link
     * ReadableSingletonStateBase} still needs to remember we read this value, because the fact that
     * the value was missing might be an important piece of information when working in pre-handle.
     */
    @Test
    @DisplayName("`get` with no value")
    void testNonExistingGet() {
        backingStore.set(null);
        final var state = createState();
        assertThat(state.isRead()).isFalse();
        assertThat(state.get()).isNull();
        assertThat(state.isRead()).isTrue();
    }

    /**
     * When asked to get a known item, not only is it returned, but we also record this in the
     * "readKeys".
     */
    @Test
    @DisplayName("`get` of a known item returns that item")
    void testExistingGet() {
        backingStore.set(BRAZIL);
        final var state = createState();
        assertThat(state.isRead()).isFalse();
        assertThat(state.get()).isEqualTo(BRAZIL);
        assertThat(state.isRead()).isTrue();
    }

    @Test
    @DisplayName("`reset` clears the readKeys cache")
    void testReset() {
        // Given a state which has been read and has the "read" flag set
        backingStore.set(CHAD);
        final var state = createState();
        assertThat(state.get()).isEqualTo(CHAD);
        assertThat(state.isRead()).isTrue();

        // When we reset
        state.reset();

        // Then the "read" flag is false
        assertThat(state.isRead()).isFalse();

        // And when the value in the backing store is changed
        backingStore.set(DENMARK);
        // Then it doesn't affect the state
        assertThat(state.isRead()).isFalse();

        // Until we ask for the value, and then it shows the new value
        assertThat(state.get()).isEqualTo(DENMARK);
        assertThat(state.isRead()).isTrue();
    }

    /**
     * This specific behavior should never show up in actual code, because all states associated
     * with a transaction are destroyed after having been committed (or in a nested/wrapped
     * scenario, we shouldn't be updating the backing data while a wrapped data is being used).
     * However, in such a situation, the right behavior is to show the updated values in the backing
     * store, unless the value has been overridden in this state (which cannot happen on readable
     * states).
     */
    @Test
    @DisplayName("State sees changes committed to backend")
    void dirtyRead() {
        backingStore.set(CHAD);
        final var state = createState();
        assertThat(state.get()).isEqualTo(CHAD);
        backingStore.set(DENMARK);
        assertThat(state.get()).isEqualTo(DENMARK);
    }
}
