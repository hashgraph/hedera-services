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
import static org.assertj.core.data.Index.atIndex;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.config.internal.ConfigurationAdaptor;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.GlobalConfig;
import com.hedera.node.app.spi.config.NodeConfig;
import com.hedera.node.app.spi.config.Profile;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("removal")
@ExtendWith(MockitoExtension.class)
class ConfigurationAdaptorTest {

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
        assertThrows(NullPointerException.class, () -> new ConfigurationAdaptor(null));
    }

    @Test
    void testNotExists() {
        // given
        given(propertySource.containsProperty("test")).willReturn(false);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final boolean exists = configurationAdapter.exists("test");

        // then
        assertThat(exists).isFalse();
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testExists() {
        // given
        given(propertySource.containsProperty("test")).willReturn(true);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final boolean exists = configurationAdapter.exists("test");

        // then
        assertThat(exists).isTrue();
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetNames() {
        // given
        given(propertySource.allPropertyNames()).willReturn(Set.of("foo", "bar"));
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final Set<String> names = configurationAdapter.getPropertyNames().collect(Collectors.toSet());

        // then
        assertThat(names).hasSize(2);
        assertThat(names).contains("foo");
        assertThat(names).contains("bar");
        verify(propertySource).allPropertyNames();
    }

    @Test
    void testGetValue() {
        // given
        given(propertySource.containsProperty("test")).willReturn(true);
        given(propertySource.getRawValue("test")).willReturn("value");
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final String value = configurationAdapter.getValue("test");

        // then
        assertThat(value).isEqualTo("value");
        verify(propertySource).getRawValue("test");
    }

    @Test
    void testGetDefaultValue() {
        // given
        given(propertySource.containsProperty("test")).willReturn(false);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final String value = configurationAdapter.getValue("test", "value");

        // then
        assertThat(value).isEqualTo("value");
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetTypedValue() {
        // given
        given(propertySource.containsProperty("test")).willReturn(true);
        given(propertySource.getTypedProperty(Integer.class, "test")).willReturn(1);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final int value = configurationAdapter.getValue("test", Integer.class);

        // then
        assertThat(value).isEqualTo(1);
        verify(propertySource).containsProperty("test");
        verify(propertySource).getTypedProperty(Integer.class, "test");
    }

    @Test
    void testGetTypedDefaultValue() {
        // given
        given(propertySource.containsProperty("test")).willReturn(false);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final int value = configurationAdapter.getValue("test", Integer.class, 12);

        // then
        assertThat(value).isEqualTo(12);
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetValues() {
        // given
        given(propertySource.containsProperty("test")).willReturn(true);
        given(propertySource.getTypedProperty(List.class, "test")).willReturn(List.of("A", "B", "C"));
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final List<String> values = configurationAdapter.getValues("test");

        // then
        assertThat(values).hasSize(3);
        assertThat(values).contains("A", atIndex(0));
        assertThat(values).contains("B", atIndex(1));
        assertThat(values).contains("C", atIndex(2));
        verify(propertySource).containsProperty("test");
        verify(propertySource).getTypedProperty(List.class, "test");
    }

    @Test
    void testGetDefaultValues() {
        // given
        given(propertySource.containsProperty("test")).willReturn(false);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final List<String> values = configurationAdapter.getValues("test", List.of("A", "B", "C"));

        // then
        assertThat(values).hasSize(3);
        assertThat(values).contains("A", atIndex(0));
        assertThat(values).contains("B", atIndex(1));
        assertThat(values).contains("C", atIndex(2));
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetTypedValues() {
        // given
        given(propertySource.containsProperty("test")).willReturn(true);
        given(propertySource.getTypedProperty(List.class, "test")).willReturn(List.of(1, 2, 3));
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final List<Integer> values = configurationAdapter.getValues("test", Integer.class);

        // then
        assertThat(values).hasSize(3);
        assertThat(values).contains(1, atIndex(0));
        assertThat(values).contains(2, atIndex(1));
        assertThat(values).contains(3, atIndex(2));
        verify(propertySource).containsProperty("test");
        verify(propertySource).getTypedProperty(List.class, "test");
    }

    @Test
    void testGetTypedDefaultValues() {
        // given
        given(propertySource.containsProperty("test")).willReturn(false);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final List<Integer> values = configurationAdapter.getValues("test", Integer.class, List.of(1, 2, 3));

        // then
        assertThat(values).hasSize(3);
        assertThat(values).contains(1, atIndex(0));
        assertThat(values).contains(2, atIndex(1));
        assertThat(values).contains(3, atIndex(2));
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetNodeConfig() {
        // given
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final NodeConfig data = configurationAdapter.getConfigData(NodeConfig.class);

        // then
        assertThat(data).isNotNull();
        assertThat(data.port()).isEqualTo(1);
        assertThat(data.tlsPort()).isEqualTo(1);
        assertThat(data.hapiOpStatsUpdateIntervalMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.entityUtilStatsUpdateIntervalMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.throttleUtilStatsUpdateIntervalMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.activeProfile()).isEqualTo(Profile.TEST);
        assertThat(data.statsSpeedometerHalfLifeSecs()).isEqualTo(1.2D);
        assertThat(data.statsRunningAvgHalfLifeSecs()).isEqualTo(1.2D);
        assertThat(data.recordLogDir()).isEqualTo("test");
        assertThat(data.recordLogPeriod()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.recordStreamEnabled()).isEqualTo(true);
        assertThat(data.recordStreamQueueCapacity()).isEqualTo(1);
        assertThat(data.queryBlobLookupRetries()).isEqualTo(1);
        assertThat(data.nettyProdKeepAliveTime()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.nettyTlsCrtPath()).isEqualTo("test");
        assertThat(data.nettyTlsKeyPath()).isEqualTo("test");
        assertThat(data.nettyProdKeepAliveTimeout()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.nettyMaxConnectionAge()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.nettyMaxConnectionAgeGrace()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.nettyMaxConnectionIdle()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.nettyMaxConcurrentCalls()).isEqualTo(1);
        assertThat(data.nettyFlowControlWindow()).isEqualTo(1);
        assertThat(data.devListeningAccount()).isEqualTo("test");
        assertThat(data.devOnlyDefaultNodeListens()).isEqualTo(true);
        assertThat(data.accountsExportPath()).isEqualTo("test");
        assertThat(data.exportAccountsOnStartup()).isEqualTo(true);
        assertThat(data.nettyMode()).isEqualTo(Profile.TEST);
        assertThat(data.nettyStartRetries()).isEqualTo(1);
        assertThat(data.nettyStartRetryIntervalMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(data.numExecutionTimesToTrack()).isEqualTo(1);
        assertThat(data.issResetPeriod()).isEqualTo(1);
        assertThat(data.issRoundsToLog()).isEqualTo(1);
        assertThat(data.prefetchQueueCapacity()).isEqualTo(1);
        assertThat(data.prefetchThreadPoolSize()).isEqualTo(1);
        assertThat(data.prefetchCodeCacheTtlSecs()).isEqualTo(1);
        assertThat(data.consThrottlesToSample()).isEmpty();
        assertThat(data.hapiThrottlesToSample()).isEmpty();
        assertThat(data.sidecarDir()).isEqualTo("test");
    }

    @Test
    void testGetGlobalConfig() {
        // given
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final GlobalConfig data = configurationAdapter.getConfigData(GlobalConfig.class);

        // then
        assertThat(data).isNotNull();
        assertThat(data.workflowsEnabled()).isEmpty();
    }
}
