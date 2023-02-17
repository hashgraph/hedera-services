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

package com.hedera.node.app.state.merkle.disk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.state.merkle.MerkleTestBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OnDiskReadableStateTest extends MerkleTestBase {
    private StateMetadata<String, String> md;
    private VirtualMap<OnDiskKey<String>, OnDiskValue<String>> virtualMap;

    @BeforeEach
    void setUp() {
        md = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.onDisk(FRUIT_STATE_KEY, STRING_SERDES, STRING_SERDES, 100));
        virtualMap = createVirtualMap("TEST LABEL", md);
    }

    @Nested
    @DisplayName("Constructor Tests")
    final class ConstructorTest {

        @Test
        @DisplayName("You must specify the metadata")
        void nullStateKeyThrows() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new OnDiskReadableKVState<>(null, virtualMap))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("You must specify the virtual map")
        void nullVirtualMapThrows() {
            //noinspection DataFlowIssue
            assertThatThrownBy(() -> new OnDiskReadableKVState<>(md, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("The stateKey matches that supplied")
        void stateKey() {
            final var state = new OnDiskReadableKVState<>(md, virtualMap);
            assertThat(state.getStateKey()).isEqualTo(FRUIT_STATE_KEY);
        }
    }

    @Nested
    @DisplayName("Query Tests")
    final class QueryTest {
        private OnDiskReadableKVState<String, String> state;

        @BeforeEach
        void setUp() {
            state = new OnDiskReadableKVState<>(md, virtualMap);
            add(virtualMap, md, A_KEY, APPLE);
            add(virtualMap, md, B_KEY, BANANA);
            add(virtualMap, md, C_KEY, CHERRY);
        }

        @Test
        @DisplayName("Get keys from the virtual map")
        void get() {
            assertThat(state.get(A_KEY)).isEqualTo(APPLE);
            assertThat(state.get(B_KEY)).isEqualTo(BANANA);
            assertThat(state.get(C_KEY)).isEqualTo(CHERRY);
            assertThat(state.get(D_KEY)).isNull();
            assertThat(state.get(E_KEY)).isNull();
            assertThat(state.get(F_KEY)).isNull();
            assertThat(state.get(G_KEY)).isNull();
        }
    }
}
