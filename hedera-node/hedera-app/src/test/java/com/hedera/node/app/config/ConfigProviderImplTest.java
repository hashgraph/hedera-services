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

package com.hedera.node.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigProviderImplTest {

    @Mock(strictness = Strictness.LENIENT)
    private PropertySource propertySource;

    @Test
    void testInvalidCreation() {
        assertThatThrownBy(() -> new ConfigProviderImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitialConfig() {
        // given
        final var configProvider = new ConfigProviderImpl(propertySource);

        // when
        final var configuration = configProvider.getConfiguration();

        // then
        assertThat(configuration).isNotNull();
    }

    @Test
    void testUpdateCreatesNewConfig() {
        // given
        final var configProvider = new ConfigProviderImpl(propertySource);

        // when
        final var configuration1 = configProvider.getConfiguration();
        configProvider.update();
        final var configuration2 = configProvider.getConfiguration();

        // then
        assertThat(configuration1).isNotSameAs(configuration2);
    }
}
