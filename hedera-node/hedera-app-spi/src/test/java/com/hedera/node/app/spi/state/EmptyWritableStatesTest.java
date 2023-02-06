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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmptyWritableStatesTest extends StateTestBase {
    private final EmptyWritableStates states = new EmptyWritableStates();

    @Test
    @DisplayName("Size is zero")
    void size() {
        assertThat(states.size()).isZero();
    }

    @Test
    @DisplayName("Is Empty")
    void empty() {
        assertThat(states.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Contains is always false")
    void contains() {
        assertThat(states.contains(FRUIT_STATE_KEY)).isFalse();
    }

    @Test
    @DisplayName("Throws NPE if the key is null")
    void nullKey() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> states.get(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Throws IAE for any non-null key")
    void nonNullKey() {
        assertThatThrownBy(() -> states.get(UNKNOWN_KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Throws IAE for any non-null singleton key")
    void nonNullSingletonKey() {
        assertThatThrownBy(() -> states.getSingleton(UNKNOWN_KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
