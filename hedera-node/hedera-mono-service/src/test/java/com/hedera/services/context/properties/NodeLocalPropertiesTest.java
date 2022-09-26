/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.Profile.DEV;
import static com.hedera.services.context.properties.Profile.PROD;
import static com.hedera.services.context.properties.Profile.TEST;
import static com.hedera.services.context.properties.PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS;
import static com.hedera.services.context.properties.PropertyNames.GRPC_PORT;
import static com.hedera.services.context.properties.PropertyNames.GRPC_TLS_PORT;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_PROFILES_ACTIVE;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY;
import static com.hedera.services.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR;
import static com.hedera.services.context.properties.PropertyNames.ISS_RESET_PERIOD;
import static com.hedera.services.context.properties.PropertyNames.ISS_ROUNDS_TO_LOG;
import static com.hedera.services.context.properties.PropertyNames.NETTY_MODE;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE;
import static com.hedera.services.context.properties.PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE;
import static com.hedera.services.context.properties.PropertyNames.NETTY_START_RETRIES;
import static com.hedera.services.context.properties.PropertyNames.NETTY_START_RETRY_INTERVAL_MS;
import static com.hedera.services.context.properties.PropertyNames.NETTY_TLS_CERT_PATH;
import static com.hedera.services.context.properties.PropertyNames.NETTY_TLS_KEY_PATH;
import static com.hedera.services.context.properties.PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES;
import static com.hedera.services.context.properties.PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE;
import static com.hedera.services.context.properties.PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.services.context.properties.PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK;
import static com.hedera.services.context.properties.PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS;
import static com.hedera.services.context.properties.PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE;
import static com.hedera.services.context.properties.PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS;
import static com.hedera.services.context.properties.PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS;
import static com.hedera.services.context.properties.PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeLocalPropertiesTest {
    private PropertySource properties;

    private NodeLocalProperties subject;

    private static final Profile[] LEGACY_ENV_ORDER = {DEV, PROD, TEST};

    @BeforeEach
    void setup() {
        properties = mock(PropertySource.class);
    }

    @Test
    void constructsIntsAsExpected() {
        givenPropsWithSeed(1);

        subject = new NodeLocalProperties(properties);

        assertEquals(1, subject.port());
        assertEquals(2, subject.tlsPort());
        assertEquals(12, subject.recordStreamQueueCapacity());
        assertEquals(13, subject.queryBlobLookupRetries());
        assertEquals(19, subject.nettyMaxConcurrentCalls());
        assertEquals(20, subject.nettyFlowControlWindow());
        assertEquals(23, subject.nettyStartRetries());
        assertEquals(25, subject.numExecutionTimesToTrack());
        assertEquals(26, subject.issResetPeriod());
        assertEquals(27, subject.issRoundsToLog());
        assertEquals(28, subject.prefetchQueueCapacity());
        assertEquals(29, subject.prefetchThreadPoolSize());
        assertEquals(30, subject.prefetchCodeCacheTtlSecs());
        assertEquals(List.of("80"), subject.consThrottlesToSample());
        assertEquals(List.of("81"), subject.hapiThrottlesToSample());
    }

    @Test
    void constructsOthersAsExpected() {
        givenPropsWithSeed(1);

        subject = new NodeLocalProperties(properties);

        assertEquals(TEST, subject.activeProfile());
        assertEquals(6L, subject.hapiOpsStatsUpdateIntervalMs());
        assertEquals(7.0, subject.statsSpeedometerHalfLifeSecs());
        assertEquals(8.0, subject.statsRunningAvgHalfLifeSecs());
        assertEquals(logDir(9), subject.recordLogDir());
        assertEquals(10L, subject.recordLogPeriod());
        assertTrue(subject.isRecordStreamEnabled());
        assertEquals(14L, subject.nettyProdKeepAliveTime());
        assertEquals("hedera1.crt", subject.nettyTlsCrtPath());
        assertEquals("hedera2.key", subject.nettyTlsKeyPath());
        assertEquals(15L, subject.nettyProdKeepAliveTimeout());
        assertEquals(16L, subject.nettyMaxConnectionAge());
        assertEquals(17L, subject.nettyMaxConnectionAgeGrace());
        assertEquals(18L, subject.nettyMaxConnectionIdle());
        assertEquals("0.0.4", subject.devListeningAccount());
        assertFalse(subject.devOnlyDefaultNodeListens());
        assertEquals("B", subject.accountsExportPath());
        assertFalse(subject.exportAccountsOnStartup());
        assertEquals(Profile.PROD, subject.nettyMode());
        assertEquals(24L, subject.nettyStartRetryIntervalMs());
    }

    @Test
    void reloadWorksAsExpectedForInts() {
        givenPropsWithSeed(2);

        // when:
        subject = new NodeLocalProperties(properties);

        // expect:
        assertEquals(2, subject.port());
        assertEquals(3, subject.tlsPort());
        assertEquals(logDir(10), subject.recordLogDir());
        assertEquals(13, subject.recordStreamQueueCapacity());
        assertEquals(14, subject.queryBlobLookupRetries());
        assertEquals(20, subject.nettyMaxConcurrentCalls());
        assertEquals(21, subject.nettyFlowControlWindow());
        assertEquals(24, subject.nettyStartRetries());
        assertEquals(26, subject.numExecutionTimesToTrack());
        assertEquals(27, subject.issResetPeriod());
        assertEquals(28, subject.issRoundsToLog());
        assertEquals(29, subject.prefetchQueueCapacity());
        assertEquals(30, subject.prefetchThreadPoolSize());
        assertEquals(31, subject.prefetchCodeCacheTtlSecs());
        assertEquals(logDir(32), subject.sidecarDir());
    }

    @Test
    void reloadWorksAsExpectedForOthers() {
        givenPropsWithSeed(2);

        // when:
        subject = new NodeLocalProperties(properties);

        // expect:
        assertEquals(DEV, subject.activeProfile());
        assertEquals(7L, subject.hapiOpsStatsUpdateIntervalMs());
        assertEquals(8.0, subject.statsSpeedometerHalfLifeSecs());
        assertEquals(9.0, subject.statsRunningAvgHalfLifeSecs());
        assertEquals(logDir(10), subject.recordLogDir());
        assertEquals(11L, subject.recordLogPeriod());
        assertFalse(subject.isRecordStreamEnabled());
        assertEquals(15L, subject.nettyProdKeepAliveTime());
        assertEquals("hedera2.crt", subject.nettyTlsCrtPath());
        assertEquals("hedera3.key", subject.nettyTlsKeyPath());
        assertEquals(16L, subject.nettyProdKeepAliveTimeout());
        assertEquals(17L, subject.nettyMaxConnectionAge());
        assertEquals(18L, subject.nettyMaxConnectionAgeGrace());
        assertEquals(19L, subject.nettyMaxConnectionIdle());
        assertEquals("0.0.3", subject.devListeningAccount());
        assertTrue(subject.devOnlyDefaultNodeListens());
        assertEquals("A", subject.accountsExportPath());
        assertTrue(subject.exportAccountsOnStartup());
        assertEquals(Profile.TEST, subject.nettyMode());
        assertEquals(25L, subject.nettyStartRetryIntervalMs());
        assertEquals(83L, subject.entityUtilStatsUpdateIntervalMs());
        assertEquals(84L, subject.throttleUtilStatsUpdateIntervalMs());
        assertEquals(logDir(32), subject.sidecarDir());
    }

    private void givenPropsWithSeed(int i) {
        given(properties.getIntProperty(GRPC_PORT)).willReturn(i);
        given(properties.getIntProperty(GRPC_TLS_PORT)).willReturn(i + 1);
        given(properties.getIntProperty("precheck.account.maxLookupRetries")).willReturn(i + 2);
        given(properties.getIntProperty("precheck.account.lookupRetryBackoffIncrementMs"))
                .willReturn(i + 3);
        given(properties.getProfileProperty(HEDERA_PROFILES_ACTIVE))
                .willReturn(LEGACY_ENV_ORDER[(i + 4) % 3]);
        given(properties.getLongProperty(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS))
                .willReturn(i + 5L);
        given(properties.getDoubleProperty(STATS_SPEEDOMETER_HALF_LIFE_SECS)).willReturn(i + 6.0);
        given(properties.getDoubleProperty(STATS_RUNNING_AVG_HALF_LIFE_SECS)).willReturn(i + 7.0);
        given(properties.getStringProperty(HEDERA_RECORD_STREAM_LOG_DIR)).willReturn(logDir(i + 8));
        given(properties.getLongProperty(HEDERA_RECORD_STREAM_LOG_PERIOD)).willReturn(i + 9L);
        given(properties.getBooleanProperty(HEDERA_RECORD_STREAM_IS_ENABLED))
                .willReturn(i % 2 == 1);
        given(properties.getIntProperty(HEDERA_RECORD_STREAM_QUEUE_CAPACITY)).willReturn(i + 11);
        given(properties.getIntProperty(QUERIES_BLOB_LOOK_UP_RETRIES)).willReturn(i + 12);
        given(properties.getLongProperty(NETTY_PROD_KEEP_ALIVE_TIME)).willReturn(i + 13L);
        given(properties.getStringProperty(NETTY_TLS_CERT_PATH)).willReturn("hedera" + i + ".crt");
        given(properties.getStringProperty(NETTY_TLS_KEY_PATH))
                .willReturn("hedera" + (i + 1) + ".key");
        given(properties.getLongProperty(NETTY_PROD_KEEP_ALIVE_TIMEOUT)).willReturn(i + 14L);
        given(properties.getLongProperty(NETTY_PROD_MAX_CONNECTION_AGE)).willReturn(i + 15L);
        given(properties.getLongProperty(NETTY_PROD_MAX_CONNECTION_AGE_GRACE)).willReturn(i + 16L);
        given(properties.getLongProperty(NETTY_PROD_MAX_CONNECTION_IDLE)).willReturn(i + 17L);
        given(properties.getIntProperty(NETTY_PROD_MAX_CONCURRENT_CALLS)).willReturn(i + 18);
        given(properties.getIntProperty(NETTY_PROD_FLOW_CONTROL_WINDOW)).willReturn(i + 19);
        given(properties.getStringProperty(DEV_DEFAULT_LISTENING_NODE_ACCOUNT))
                .willReturn(i % 2 == 0 ? "0.0.3" : "0.0.4");
        given(properties.getBooleanProperty(DEV_ONLY_DEFAULT_NODE_LISTENS)).willReturn(i % 2 == 0);
        given(properties.getStringProperty(HEDERA_ACCOUNTS_EXPORT_PATH))
                .willReturn(i % 2 == 0 ? "A" : "B");
        given(properties.getBooleanProperty(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP))
                .willReturn(i % 2 == 0);
        given(properties.getProfileProperty(NETTY_MODE)).willReturn(LEGACY_ENV_ORDER[(i + 21) % 3]);
        given(properties.getIntProperty(NETTY_START_RETRIES)).willReturn(i + 22);
        given(properties.getLongProperty(NETTY_START_RETRY_INTERVAL_MS)).willReturn(i + 23L);
        given(properties.getIntProperty(STATS_EXECUTION_TIMES_TO_TRACK)).willReturn(i + 24);
        given(properties.getIntProperty(ISS_RESET_PERIOD)).willReturn(i + 25);
        given(properties.getIntProperty(ISS_ROUNDS_TO_LOG)).willReturn(i + 26);
        given(properties.getIntProperty(HEDERA_PREFETCH_QUEUE_CAPACITY)).willReturn(i + 27);
        given(properties.getIntProperty(HEDERA_PREFETCH_THREAD_POOL_SIZE)).willReturn(i + 28);
        given(properties.getIntProperty(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS)).willReturn(i + 29);
        given(properties.getStringsProperty(STATS_CONS_THROTTLES_TO_SAMPLE))
                .willReturn(List.of("" + (i + 79)));
        given(properties.getStringsProperty(STATS_HAPI_THROTTLES_TO_SAMPLE))
                .willReturn(List.of("" + (i + 80)));
        given(properties.getLongProperty(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS))
                .willReturn(i + 81L);
        given(properties.getLongProperty(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS))
                .willReturn(i + 82L);
        given(properties.getStringProperty(HEDERA_RECORD_STREAM_SIDE_CAR_DIR))
                .willReturn(logDir(i + 30));
    }

    static String logDir(int num) {
        return "myRecords/dir" + num;
    }
}
