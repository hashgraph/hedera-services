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
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.Profile;
import com.hedera.node.app.spi.config.VersionedConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigProviderImplTest {

    @Mock(strictness = Strictness.LENIENT)
    private PropertySource propertySource;

    @BeforeEach
    void configureMockForConfigData() {
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(Integer.class), ArgumentMatchers.any()))
                .thenReturn(1);
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(Long.class), ArgumentMatchers.any()))
                .thenReturn(Long.MAX_VALUE);
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(Double.class), ArgumentMatchers.any()))
                .thenReturn(1.2D);
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(Profile.class), ArgumentMatchers.any()))
                .thenReturn(Profile.TEST);
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(String.class), ArgumentMatchers.any()))
                .thenReturn("test");
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(Boolean.class), ArgumentMatchers.any()))
                .thenReturn(true);
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(List.class), ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(propertySource.getTypedProperty(ArgumentMatchers.eq(Set.class), ArgumentMatchers.any()))
                .thenReturn(Set.of());
    }

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
        assertThat(configuration.getVersion()).isZero();
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
        assertThat(configuration1).returns(0L, VersionedConfiguration::getVersion);
        assertThat(configuration2).returns(1L, VersionedConfiguration::getVersion);
    }
}
