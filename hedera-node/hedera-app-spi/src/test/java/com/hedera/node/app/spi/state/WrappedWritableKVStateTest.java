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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * This test extends the {@link WritableKVStateBaseTest}, getting all the test methods used there,
 * but this time executed on a {@link WrappedWritableKVState}.
 */
class WrappedWritableKVStateTest extends WritableKVStateBaseTest {
    private WritableKVStateBase<String, String> delegate;

    protected WritableKVStateBase<String, String> createFruitState(@NonNull final Map<String, String> map) {
        this.delegate = new MapWritableKVState<>(FRUIT_STATE_KEY, map);
        this.state = Mockito.spy(new WrappedWritableKVState<>(delegate));
        return this.state;
    }

    @Test
    @DisplayName("If we commit on the wrapped state, the commit goes to the delegate, but not the" + " backing store")
    void commitGoesToDelegateNotBackingStore() {
        state.put(B_KEY, BLACKBERRY);
        state.put(E_KEY, ELDERBERRY);

        // These values should be in the wrapped state, but not in the delegate
        assertThat(state.get(B_KEY)).isEqualTo(BLACKBERRY); // Has the new value
        assertThat(state.get(E_KEY)).isEqualTo(ELDERBERRY); // Has the new value
        assertThat(delegate.get(B_KEY)).isEqualTo(BANANA); // Has the old value
        assertThat(delegate.get(E_KEY)).isNull(); // Has no value yet

        // After committing, the values MUST be flushed to the delegate
        state.commit();
        assertThat(state.get(B_KEY)).isEqualTo(BLACKBERRY); // Has the new value
        assertThat(state.get(E_KEY)).isEqualTo(ELDERBERRY); // Has the new value
        assertThat(delegate.get(B_KEY)).isEqualTo(BLACKBERRY); // Has the new value
        assertThat(delegate.get(E_KEY)).isEqualTo(ELDERBERRY); // Has the new value
    }

    @Nested
    @DisplayName("size")
    final class SizeTest {
        /**
         * Gives size of backing store. Since on WrappedWritableKVState, all the changes are only
         * buffered to modifications, size of backing store will not change on committing new values.
         */
        @Test
        @DisplayName("Put a key that does not already exist in the backing store")
        void putNew() {
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            state.put(C_KEY, CHERRY);

            // Before commit, the size of backing store should be 2 and modifications are none
            assertEquals(2, state.size());
            assertEquals(2, delegate.size());
            assertEquals(0, delegate.modifiedKeys().size());

            // Commit should not cause the size of backing store to be increased by 1.
            // Instead, modifications on delegate should have the committed value
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(anyString(), anyString());
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(C_KEY, CHERRY);
            Mockito.verify(state, Mockito.never()).removeFromDataSource(anyString());
            assertEquals(2, state.size());
            assertEquals(2, delegate.size());
            assertEquals(1, delegate.modifiedKeys().size());
        }

        /**
         * Gives size of backing store. Since on WrappedWritableKVState, all the changes are only
         * buffered to modifications, size of backing store will not change on removing existing values.
         */
        @Test
        @DisplayName("Remove a key existing in the backing store")
        void removeExisting() {
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            state.remove(A_KEY);
            // Before commit, the size should be 2
            assertEquals(2, state.size());
            assertEquals(2, delegate.size());
            assertEquals(0, delegate.modifiedKeys().size());

            // Commit should cause the size to be decreased by 1
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(anyString(), anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
            assertEquals(2, state.size());
            assertEquals(2, delegate.size());
            assertEquals(1, delegate.modifiedKeys().size());
        }
    }
}
