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
package com.hedera.node.app.spi.config;

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

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.List;

@ConfigData
public record NodeConfig(
        @ConfigProperty(GRPC_PORT) int port,
        @ConfigProperty(GRPC_TLS_PORT) int tlsPort,
        @ConfigProperty(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS)
                long hapiOpStatsUpdateIntervalMs,
        @ConfigProperty(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS)
                long entityUtilStatsUpdateIntervalMs,
        @ConfigProperty(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS)
                long throttleUtilStatsUpdateIntervalMs,
        @ConfigProperty(HEDERA_PROFILES_ACTIVE) Profile activeProfile,
        @ConfigProperty(STATS_SPEEDOMETER_HALF_LIFE_SECS) double statsSpeedometerHalfLifeSecs,
        @ConfigProperty(STATS_RUNNING_AVG_HALF_LIFE_SECS) double statsRunningAvgHalfLifeSecs,
        @ConfigProperty(HEDERA_RECORD_STREAM_LOG_DIR) String recordLogDir,
        @ConfigProperty(HEDERA_RECORD_STREAM_LOG_PERIOD) long recordLogPeriod,
        @ConfigProperty(HEDERA_RECORD_STREAM_IS_ENABLED) boolean recordStreamEnabled,
        @ConfigProperty(HEDERA_RECORD_STREAM_QUEUE_CAPACITY) int recordStreamQueueCapacity,
        @ConfigProperty(QUERIES_BLOB_LOOK_UP_RETRIES) int queryBlobLookupRetries,
        @ConfigProperty(NETTY_PROD_KEEP_ALIVE_TIME) long nettyProdKeepAliveTime,
        @ConfigProperty(NETTY_TLS_CERT_PATH) String nettyTlsCrtPath,
        @ConfigProperty(NETTY_TLS_KEY_PATH) String nettyTlsKeyPath,
        @ConfigProperty(NETTY_PROD_KEEP_ALIVE_TIMEOUT) long nettyProdKeepAliveTimeout,
        @ConfigProperty(NETTY_PROD_MAX_CONNECTION_AGE) long nettyMaxConnectionAge,
        @ConfigProperty(NETTY_PROD_MAX_CONNECTION_AGE_GRACE) long nettyMaxConnectionAgeGrace,
        @ConfigProperty(NETTY_PROD_MAX_CONNECTION_IDLE) long nettyMaxConnectionIdle,
        @ConfigProperty(NETTY_PROD_MAX_CONCURRENT_CALLS) int nettyMaxConcurrentCalls,
        @ConfigProperty(NETTY_PROD_FLOW_CONTROL_WINDOW) int nettyFlowControlWindow,
        @ConfigProperty(DEV_DEFAULT_LISTENING_NODE_ACCOUNT) String devListeningAccount,
        @ConfigProperty(DEV_ONLY_DEFAULT_NODE_LISTENS) boolean devOnlyDefaultNodeListens,
        @ConfigProperty(HEDERA_ACCOUNTS_EXPORT_PATH) String accountsExportPath,
        @ConfigProperty(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP) boolean exportAccountsOnStartup,
        @ConfigProperty(NETTY_MODE) Profile nettyMode,
        @ConfigProperty(NETTY_START_RETRIES) int nettyStartRetries,
        @ConfigProperty(NETTY_START_RETRY_INTERVAL_MS) long nettyStartRetryIntervalMs,
        @ConfigProperty(STATS_EXECUTION_TIMES_TO_TRACK) int numExecutionTimesToTrack,
        @ConfigProperty(ISS_RESET_PERIOD) int issResetPeriod,
        @ConfigProperty(ISS_ROUNDS_TO_LOG) int issRoundsToLog,
        @ConfigProperty(HEDERA_PREFETCH_QUEUE_CAPACITY) int prefetchQueueCapacity,
        @ConfigProperty(HEDERA_PREFETCH_THREAD_POOL_SIZE) int prefetchThreadPoolSize,
        @ConfigProperty(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS) int prefetchCodeCacheTtlSecs,
        @ConfigProperty(STATS_CONS_THROTTLES_TO_SAMPLE) List<String> consThrottlesToSample,
        @ConfigProperty(STATS_HAPI_THROTTLES_TO_SAMPLE) List<String> hapiThrottlesToSample,
        @ConfigProperty(HEDERA_RECORD_STREAM_SIDE_CAR_DIR) String sidecarDir) {}
