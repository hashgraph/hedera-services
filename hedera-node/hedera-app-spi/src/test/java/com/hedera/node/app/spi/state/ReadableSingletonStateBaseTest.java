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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadableSingletonStateBaseTest extends SingletonStateTestBase {
    @Override
    protected ReadableSingletonStateBase<String> createState() {
        return new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, backingStore::get);
    }

    @Test
    @DisplayName("Constructor throws NPE if stateKey is null")
    void nullStateKey() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new ReadableSingletonStateBase<>(null, () -> AUSTRALIA))
                .isInstanceOf(NullPointerException.class);
    }

    /** Make sure the constructor is holding onto the state key properly */
    @Test
    @DisplayName("The state key must match what was provided in the constructor")
    void testStateKey() {
        final var state = new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, () -> AUSTRALIA);
        assertThat(state.getStateKey()).isEqualTo(COUNTRY_STATE_KEY);
    }

    @Test
    @DisplayName("Constructor throws NPE if backingStoreAccessor is null")
    void nullAccessor() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new ReadableSingletonStateBase<>(COUNTRY_STATE_KEY, null))
                .isInstanceOf(NullPointerException.class);
    }
}
