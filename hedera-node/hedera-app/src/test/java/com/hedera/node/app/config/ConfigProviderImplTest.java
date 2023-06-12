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

import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class ConfigProviderImplTest {

    @Test
    void testNullConfig() {
        // then
        assertThatThrownBy(() -> new ConfigProviderImpl(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testInitialConfig(final boolean isGenesis) {
        // given
        final var configProvider = new ConfigProviderImpl(isGenesis);

        // when
        final var configuration = configProvider.getConfiguration();

        // then
        assertThat(configuration).isNotNull();
        assertThat(configuration.getVersion()).isZero();
    }

    @Test
    void testApplicationPropertiesLoaded() {
        // given
        final var configProvider = new ConfigProviderImpl(false);

        // when
        final var configuration = configProvider.getConfiguration();
        final String value = configuration.getValue("foo.test");

        // then
        assertThat(value).isEqualTo("123");
    }

    @Test
    void testGenesisNotAlwaysUsed() {
        // given
        final var configProvider = new ConfigProviderImpl(false);

        // when
        final var configuration = configProvider.getConfiguration();
        final String value = configuration.getValue("bar.test");

        // then
        assertThat(value).isEqualTo("456");
    }

    @Test
    void testGenesisOverwritesApplication() {
        // given
        final var configProvider = new ConfigProviderImpl(true);

        // when
        final var configuration = configProvider.getConfiguration();
        final String value = configuration.getValue("bar.test");

        // then
        assertThat(value).isEqualTo("genesis");
    }

    @Test
    void testDifferentApplicationPropertiesFile(final EnvironmentVariables environment) {
        // given
        environment.set(ConfigProviderImpl.APPLICATION_PROPERTIES_PATH_ENV, "for-test/application.properties.test");
        final var configProvider = new ConfigProviderImpl(false);

        // when
        final var configuration = configProvider.getConfiguration();
        final String bar = configuration.getValue("bar.test");

        // then
        assertThat(bar).isEqualTo("456Test");
    }

    @Test
    void testDifferentGenesisPropertiesFile(final EnvironmentVariables environment) {
        // given
        environment.set(ConfigProviderImpl.GENESIS_PROPERTIES_PATH_ENV, "for-test/genesis.properties.test");
        final var configProvider = new ConfigProviderImpl(true);

        // when
        final var configuration = configProvider.getConfiguration();
        final String bar = configuration.getValue("bar.test");

        // then
        assertThat(bar).isEqualTo("genesisTest");
    }

    @Test
    void testApplicationPropertiesFileIsOptional(final EnvironmentVariables environment) {
        // given
        environment.set(ConfigProviderImpl.APPLICATION_PROPERTIES_PATH_ENV, "does-not-exist");
        final var configProvider = new ConfigProviderImpl(true);

        // when
        final var configuration = configProvider.getConfiguration();
        final String bar = configuration.getValue("bar.test");

        // then
        assertThat(configuration.exists("foo.test")).isFalse();
        assertThat(bar).isEqualTo("genesis");
    }

    @Test
    void testGenesisPropertiesFileIsOptional(final EnvironmentVariables environment) {
        // given
        environment.set(ConfigProviderImpl.GENESIS_PROPERTIES_PATH_ENV, "does-not-exist");
        final var configProvider = new ConfigProviderImpl(true);

        // when
        final var configuration = configProvider.getConfiguration();
        final String bar = configuration.getValue("bar.test");

        // then
        assertThat(bar).isEqualTo("456");
    }

    @Test
    void testUpdateDoesUseApplicationProperties() {
        // given
        final var configProvider = new ConfigProviderImpl(false);
        final Bytes bytes = Bytes.wrap(new byte[] {});

        // when
        configProvider.update(bytes);
        final VersionedConfiguration configuration = configProvider.getConfiguration();
        final String value1 = configuration.getValue("foo.test");
        final String value2 = configuration.getValue("bar.test");

        // then
        assertThat(configuration.getVersion()).isEqualTo(1);
        assertThat(value1).isEqualTo("123");
        assertThat(value2).isEqualTo("456");
    }

    @Test
    void testUpdateDoesNotUseGenesisProperties() {
        // given
        final var configProvider = new ConfigProviderImpl(true);
        final Bytes bytes = Bytes.wrap(new byte[] {});

        // when
        configProvider.update(bytes);
        final VersionedConfiguration configuration = configProvider.getConfiguration();

        // then
        assertThat(configuration.getVersion()).isEqualTo(1);
        assertThat(configuration.getValue("bar.test")).isNotEqualTo("genesis");
    }

    @Test
    void testUpdateProvidesConfigProperty() {
        // given
        final var configProvider = new ConfigProviderImpl(true);
        final Bytes bytes = Bytes.wrap("update.test=789".getBytes(StandardCharsets.UTF_8));

        // when
        configProvider.update(bytes);
        final VersionedConfiguration configuration = configProvider.getConfiguration();
        final String value = configuration.getValue("update.test");

        // then
        assertThat(configuration.getVersion()).isEqualTo(1);
        assertThat(value).isEqualTo("789");
    }

    @Test
    void testUpdateProvidesConfigProperties() {
        // given
        final var configProvider = new ConfigProviderImpl(true);
        final StringBuilder sb = new StringBuilder("update.test1=789")
                .append(System.lineSeparator())
                .append("update.test2=abc")
                .append(System.lineSeparator())
                .append("# update.test3=COMMENT");
        final Bytes bytes = Bytes.wrap(sb.toString().getBytes(StandardCharsets.UTF_8));

        // when
        configProvider.update(bytes);
        VersionedConfiguration configuration = configProvider.getConfiguration();
        final String value1 = configuration.getValue("update.test1");
        final String value2 = configuration.getValue("update.test2");

        // then
        assertThat(configuration.getVersion()).isEqualTo(1);
        assertThat(value1).isEqualTo("789");
        assertThat(value2).isEqualTo("abc");
    }

    @Test
    void testUpdateWithNullBytes() {
        // given
        final var configProvider = new ConfigProviderImpl(true);

        // then
        assertThatThrownBy(() -> configProvider.update(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testUpdateWithInvalidBytes() {
        // given
        final var configProvider = new ConfigProviderImpl(true);
        final Bytes bytes = Bytes.wrap("\\uxxxx".getBytes(StandardCharsets.UTF_8));

        // then
        assertThatThrownBy(() -> configProvider.update(bytes)).isInstanceOf(IllegalArgumentException.class);
    }
}
