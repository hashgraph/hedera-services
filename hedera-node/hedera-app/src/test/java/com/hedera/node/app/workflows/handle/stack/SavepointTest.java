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

package com.hedera.node.app.workflows.handle.stack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavepointTest {

    private static final Configuration BASE_CONFIG = new HederaTestConfigBuilder(false).getOrCreateConfig();

    @Mock
    private WrappedHederaState state;

    @Test
    void testConstructor() {
        // when
        final var savepoint = new Savepoint(state, BASE_CONFIG);

        // then
        assertThat(savepoint.state()).isEqualTo(state);
        assertThat(savepoint.configuration()).isEqualTo(BASE_CONFIG);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidParameters() {
        assertThatThrownBy(() -> new Savepoint(null, BASE_CONFIG)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Savepoint(state, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSetConfiguration() {
        // given
        final var savepoint = new Savepoint(state, BASE_CONFIG);
        final var newConfig = new HederaTestConfigBuilder(false).getOrCreateConfig();

        // when
        savepoint.configuration(newConfig);

        // then
        assertThat(savepoint.configuration()).isEqualTo(newConfig);
    }
}
