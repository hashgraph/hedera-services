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
        @Test
        @DisplayName("Adding a key that is not in the backing store impacts the size")
        void putNew() {
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Before inserting, the size of backing store should be 2 (setup of the test adds 2 keys) and modifications
            // are none
            assertEquals(2, state.size());
            assertEquals(2, delegate.size());
            assertEquals(0, delegate.modifiedKeys().size());

            state.put(C_KEY, CHERRY);

            // After doing a put, the size is increased as modifications are considered.
            // But the modifiedKeys or size of delegate doesn't change until commit
            assertEquals(3, state.size());
            assertEquals(2, delegate.size());
            assertEquals(0, delegate.modifiedKeys().size());

            // Commit should not cause the size of backing store to be increased by 1.
            // Instead, modifications on delegate have increased.
            // Since modifications are increased, size of delegate also increases.
            state.commit();
            Mockito.verify(state, Mockito.times(1)).putIntoDataSource(C_KEY, CHERRY);
            Mockito.verify(state, Mockito.never()).removeFromDataSource(anyString());
            assertEquals(3, state.size());
            assertEquals(3, delegate.size());
            assertEquals(1, delegate.modifiedKeys().size());
        }

        @Test
        @DisplayName("Removing a key that is in the backing store impacts the size")
        void removeExisting() {
            assertThat(state.readKeys()).isEmpty();
            assertThat(state.modifiedKeys()).isEmpty();

            // Before remove, the size of backing store should be 2 (setup of the test adds 2 keys) and modifications
            // are none
            assertEquals(2, state.size());
            assertEquals(2, delegate.size());
            assertEquals(0, delegate.modifiedKeys().size());

            state.remove(A_KEY);

            // After remove, the size of backing store should be 2 (setup of the test adds 2 keys) and modifications are
            // 1
            // So the size of state should be 1. But those changes don't affect delegate
            // until commit.
            assertEquals(1, state.size());
            assertEquals(2, delegate.size());
            assertEquals(0, delegate.modifiedKeys().size());

            // Commit should cause change in modifications on delegate.
            // So the size of the delegate also decreases by 1.
            state.commit();
            Mockito.verify(state, Mockito.never()).putIntoDataSource(anyString(), anyString());
            Mockito.verify(state, Mockito.times(1)).removeFromDataSource(A_KEY);
            assertEquals(1, state.size());
            assertEquals(1, delegate.size());
            assertEquals(1, delegate.modifiedKeys().size());
        }
    }
}
