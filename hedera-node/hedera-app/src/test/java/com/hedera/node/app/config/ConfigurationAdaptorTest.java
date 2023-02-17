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

import static com.hedera.node.app.spi.config.PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT;
import static com.hedera.node.app.spi.config.PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_TLS_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PROFILES_ACTIVE;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR;
import static com.hedera.node.app.spi.config.PropertyNames.ISS_RESET_PERIOD;
import static com.hedera.node.app.spi.config.PropertyNames.ISS_ROUNDS_TO_LOG;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_MODE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_START_RETRIES;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_START_RETRY_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_TLS_CERT_PATH;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_TLS_KEY_PATH;
import static com.hedera.node.app.spi.config.PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.WORKFLOWS_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.GlobalConfig;
import com.hedera.node.app.spi.config.NodeConfig;
import com.hedera.node.app.spi.config.Profile;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito.BDDMyOngoingStubbing;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigurationAdaptorTest {

    @Mock(strictness = Strictness.LENIENT)
    private PropertySource propertySource;

    @BeforeEach
    void configureMockForConfigData() {
        final Function<String, BDDMyOngoingStubbing<Object>> createMock =
                name ->
                        given(
                                propertySource.getTypedProperty(
                                        ArgumentMatchers.any(), ArgumentMatchers.eq(name)));
        final Consumer<String> integerMockRule = name -> createMock.apply(name).willReturn(1);
        final Consumer<String> longMockRule =
                name -> createMock.apply(name).willReturn(Long.MAX_VALUE);
        final Consumer<String> doubleMockRule = name -> createMock.apply(name).willReturn(1.2D);
        final Consumer<String> profileMockRule =
                name -> createMock.apply(name).willReturn(Profile.TEST);
        final Consumer<String> stringMockRule = name -> createMock.apply(name).willReturn("test");
        final Consumer<String> booleanMockRule = name -> createMock.apply(name).willReturn(true);
        final Consumer<String> listMockRule = name -> createMock.apply(name).willReturn(List.of());

        integerMockRule.accept(GRPC_PORT);
        integerMockRule.accept(GRPC_TLS_PORT);
        longMockRule.accept(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS);
        longMockRule.accept(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS);
        longMockRule.accept(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS);
        profileMockRule.accept(HEDERA_PROFILES_ACTIVE);
        doubleMockRule.accept(STATS_SPEEDOMETER_HALF_LIFE_SECS);
        doubleMockRule.accept(STATS_RUNNING_AVG_HALF_LIFE_SECS);
        stringMockRule.accept(HEDERA_RECORD_STREAM_LOG_DIR);
        longMockRule.accept(HEDERA_RECORD_STREAM_LOG_PERIOD);
        booleanMockRule.accept(HEDERA_RECORD_STREAM_IS_ENABLED);
        integerMockRule.accept(HEDERA_RECORD_STREAM_QUEUE_CAPACITY);
        integerMockRule.accept(QUERIES_BLOB_LOOK_UP_RETRIES);
        longMockRule.accept(NETTY_PROD_KEEP_ALIVE_TIME);
        stringMockRule.accept(NETTY_TLS_CERT_PATH);
        stringMockRule.accept(NETTY_TLS_KEY_PATH);
        longMockRule.accept(NETTY_PROD_KEEP_ALIVE_TIMEOUT);
        longMockRule.accept(NETTY_PROD_MAX_CONNECTION_AGE);
        longMockRule.accept(NETTY_PROD_MAX_CONNECTION_AGE_GRACE);
        longMockRule.accept(NETTY_PROD_MAX_CONNECTION_IDLE);
        integerMockRule.accept(NETTY_PROD_MAX_CONCURRENT_CALLS);
        integerMockRule.accept(NETTY_PROD_FLOW_CONTROL_WINDOW);
        stringMockRule.accept(DEV_DEFAULT_LISTENING_NODE_ACCOUNT);
        booleanMockRule.accept(DEV_ONLY_DEFAULT_NODE_LISTENS);
        stringMockRule.accept(HEDERA_ACCOUNTS_EXPORT_PATH);
        booleanMockRule.accept(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP);
        profileMockRule.accept(NETTY_MODE);
        integerMockRule.accept(NETTY_START_RETRIES);
        longMockRule.accept(NETTY_START_RETRY_INTERVAL_MS);
        integerMockRule.accept(STATS_EXECUTION_TIMES_TO_TRACK);
        integerMockRule.accept(ISS_RESET_PERIOD);
        integerMockRule.accept(ISS_ROUNDS_TO_LOG);
        integerMockRule.accept(HEDERA_PREFETCH_QUEUE_CAPACITY);
        integerMockRule.accept(HEDERA_PREFETCH_THREAD_POOL_SIZE);
        integerMockRule.accept(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS);
        listMockRule.accept(STATS_CONS_THROTTLES_TO_SAMPLE);
        listMockRule.accept(STATS_HAPI_THROTTLES_TO_SAMPLE);
        stringMockRule.accept(HEDERA_RECORD_STREAM_SIDE_CAR_DIR);
        booleanMockRule.accept(WORKFLOWS_ENABLED);
    }

    @Test
    void createInvalidCreation() {
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
        assertFalse(exists);
        
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
        assertTrue(exists);
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetNames() {
        // given
        given(propertySource.allPropertyNames()).willReturn(Set.of("foo", "bar"));
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final Set<String> names =
                configurationAdapter.getPropertyNames().collect(Collectors.toSet());

        // then
        assertEquals(Set.of("foo", "bar"), names);
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
        assertEquals("value", value);
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
        assertEquals("value", value);
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
        assertEquals(1, value);
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
        assertEquals(12, value);
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetValues() {
        // given
        given(propertySource.containsProperty("test")).willReturn(true);
        given(propertySource.getTypedProperty(List.class, "test"))
                .willReturn(List.of("A", "B", "C"));
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final List<String> values = configurationAdapter.getValues("test");

        // then
        assertEquals(List.of("A", "B", "C"), values);
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
        assertEquals(List.of("A", "B", "C"), values);
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
        assertEquals(List.of(1, 2, 3), values);
        verify(propertySource).containsProperty("test");
        verify(propertySource).getTypedProperty(List.class, "test");
    }

    @Test
    void testGetTypedDefaultValues() {
        // given
        given(propertySource.containsProperty("test")).willReturn(false);
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final List<Integer> values =
                configurationAdapter.getValues("test", Integer.class, List.of(1, 2, 3));

        // then
        assertEquals(List.of(1, 2, 3), values);
        verify(propertySource).containsProperty("test");
    }

    @Test
    void testGetNodeConfig() {
        // given
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final NodeConfig data = configurationAdapter.getConfigData(NodeConfig.class);

        // then
        assertNotNull(data);
        assertEquals(1, data.port());
        assertEquals(1, data.tlsPort());
        assertEquals(Long.MAX_VALUE, data.hapiOpStatsUpdateIntervalMs());
        assertEquals(Long.MAX_VALUE, data.entityUtilStatsUpdateIntervalMs());
        assertEquals(Long.MAX_VALUE, data.throttleUtilStatsUpdateIntervalMs());
        assertEquals(Profile.TEST, data.activeProfile());
        assertEquals(1.2D, data.statsSpeedometerHalfLifeSecs());
        assertEquals(1.2D, data.statsRunningAvgHalfLifeSecs());
        assertEquals("test", data.recordLogDir());
        assertEquals(Long.MAX_VALUE, data.recordLogPeriod());
        assertEquals(true, data.recordStreamEnabled());
        assertEquals(1, data.recordStreamQueueCapacity());
        assertEquals(1, data.queryBlobLookupRetries());
        assertEquals(Long.MAX_VALUE, data.nettyProdKeepAliveTime());
        assertEquals("test", data.nettyTlsCrtPath());
        assertEquals("test", data.nettyTlsKeyPath());
        assertEquals(Long.MAX_VALUE, data.nettyProdKeepAliveTimeout());
        assertEquals(Long.MAX_VALUE, data.nettyMaxConnectionAge());
        assertEquals(Long.MAX_VALUE, data.nettyMaxConnectionAgeGrace());
        assertEquals(Long.MAX_VALUE, data.nettyMaxConnectionIdle());
        assertEquals(1, data.nettyMaxConcurrentCalls());
        assertEquals(1, data.nettyFlowControlWindow());
        assertEquals("test", data.devListeningAccount());
        assertEquals(true, data.devOnlyDefaultNodeListens());
        assertEquals("test", data.accountsExportPath());
        assertEquals(true, data.exportAccountsOnStartup());
        assertEquals(Profile.TEST, data.nettyMode());
        assertEquals(1, data.nettyStartRetries());
        assertEquals(Long.MAX_VALUE, data.nettyStartRetryIntervalMs());
        assertEquals(1, data.numExecutionTimesToTrack());
        assertEquals(1, data.issResetPeriod());
        assertEquals(1, data.issRoundsToLog());
        assertEquals(1, data.prefetchQueueCapacity());
        assertEquals(1, data.prefetchThreadPoolSize());
        assertEquals(1, data.prefetchCodeCacheTtlSecs());
        assertEquals(List.of(), data.consThrottlesToSample());
        assertEquals(List.of(), data.hapiThrottlesToSample());
        assertEquals("test", data.sidecarDir());
    }

    @Test
    void testGetGlobalConfig() {
        // given
        final ConfigurationAdaptor configurationAdapter = new ConfigurationAdaptor(propertySource);

        // when
        final GlobalConfig data = configurationAdapter.getConfigData(GlobalConfig.class);

        // then
        assertNotNull(data);
        assertEquals(true, data.workflowsEnabled());
    }
}
