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

import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * This test extends the {@link WritableKVStateBaseTest}, getting all the test methods used there,
 * but this time executed on a {@link WrappedWritableKVState}.
 */
class WrappedWritableKVStateTest extends WritableKVStateBaseTest {
    private WritableKVStateBase<String, String> delegate;

    protected WritableKVStateBase<String, String> createFruitState(
            @NonNull final Map<String, String> map) {
        this.delegate = new MapWritableKVState<>(FRUIT_STATE_KEY, map);
        this.state = Mockito.spy(new WrappedWritableKVState<>(delegate));
        return this.state;
    }

    @Test
    @DisplayName(
            "If we commit on the wrapped state, the commit goes to the delegate, but not the"
                    + " backing store")
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
}
