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

import com.hedera.node.config.VersionedConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigProviderImplTest {


    @Test
    void testInitialConfig() {
        // given
        final var configProvider = new ConfigProviderImpl();

        // when
        final var configuration = configProvider.getConfiguration();

        // then
        assertThat(configuration).isNotNull();
        assertThat(configuration.getVersion()).isZero();
    }

    @Test
    void testUpdateCreatesNewConfig() {
        // given
        final var configProvider = new ConfigProviderImpl();

        // when
        final var configuration1 = configProvider.getConfiguration();
        configProvider.update("name", "value");
        final var configuration2 = configProvider.getConfiguration();

        // then
        assertThat(configuration1).isNotSameAs(configuration2);
        assertThat(configuration1).returns(0L, VersionedConfiguration::getVersion);
        assertThat(configuration2).returns(1L, VersionedConfiguration::getVersion);
    }

    @Test
    void testUpdatedValue() {
        // given
        final var configProvider = new ConfigProviderImpl();
        final var configuration1 = configProvider.getConfiguration();
        final boolean existsInitially = configuration1.exists("port");

        // when
        configProvider.update("port", "8080");
        final var configuration2 = configProvider.getConfiguration();
        final String value2 = configuration2.getValue("port");
        configProvider.update("port", "9090");
        final var configuration3 = configProvider.getConfiguration();
        final String value3 = configuration3.getValue("port");

        // then
        assertThat(existsInitially).isFalse();
        assertThat(value2).isEqualTo("8080");
        assertThat(value3).isEqualTo("9090");
    }
}
