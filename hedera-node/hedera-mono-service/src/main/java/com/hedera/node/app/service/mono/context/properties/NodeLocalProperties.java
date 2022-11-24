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
package com.hedera.node.app.service.mono.context.properties;

import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NodeLocalProperties {
    private final PropertySource properties;

    private int port;
    private int tlsPort;
    private long hapiOpStatsUpdateIntervalMs;
    private long entityUtilStatsUpdateIntervalMs;
    private long throttleUtilStatsUpdateIntervalMs;
    private Profile activeProfile;
    private double statsSpeedometerHalfLifeSecs;
    private double statsRunningAvgHalfLifeSecs;
    private String recordLogDir;
    private long recordLogPeriod;
    private boolean recordStreamEnabled;
    private int recordStreamQueueCapacity;
    private int queryBlobLookupRetries;
    private long nettyProdKeepAliveTime;
    private String nettyTlsCrtPath;
    private String nettyTlsKeyPath;
    private long nettyProdKeepAliveTimeout;
    private long nettyMaxConnectionAge;
    private long nettyMaxConnectionAgeGrace;
    private long nettyMaxConnectionIdle;
    private int nettyMaxConcurrentCalls;
    private int nettyFlowControlWindow;
    private String devListeningAccount;
    private boolean devOnlyDefaultNodeListens;
    private String accountsExportPath;
    private boolean exportAccountsOnStartup;
    private Profile nettyMode;
    private int nettyStartRetries;
    private long nettyStartRetryIntervalMs;
    private int numExecutionTimesToTrack;
    private int issResetPeriod;
    private int issRoundsToLog;
    private int prefetchQueueCapacity;
    private int prefetchThreadPoolSize;
    private int prefetchCodeCacheTtlSecs;
    private List<String> consThrottlesToSample;
    private List<String> hapiThrottlesToSample;
    private String sidecarDir;

    @Inject
    public NodeLocalProperties(@CompositeProps PropertySource properties) {
        this.properties = properties;

        reload();
    }

    public void reload() {
        port = properties.getIntProperty(PropertyNames.GRPC_PORT);
        tlsPort = properties.getIntProperty(PropertyNames.GRPC_TLS_PORT);
        activeProfile = properties.getProfileProperty(PropertyNames.HEDERA_PROFILES_ACTIVE);
        hapiOpStatsUpdateIntervalMs =
                properties.getLongProperty(
                        PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS);
        statsSpeedometerHalfLifeSecs =
                properties.getDoubleProperty(PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS);
        statsRunningAvgHalfLifeSecs =
                properties.getDoubleProperty(PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS);
        recordLogDir = properties.getStringProperty(PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR);
        sidecarDir = properties.getStringProperty(PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR);
        recordLogPeriod = properties.getLongProperty(PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD);
        recordStreamEnabled =
                properties.getBooleanProperty(PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED);
        recordStreamQueueCapacity =
                properties.getIntProperty(PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY);
        queryBlobLookupRetries =
                properties.getIntProperty(PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES);
        nettyProdKeepAliveTime =
                properties.getLongProperty(PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME);
        nettyTlsCrtPath = properties.getStringProperty(PropertyNames.NETTY_TLS_CERT_PATH);
        nettyTlsKeyPath = properties.getStringProperty(PropertyNames.NETTY_TLS_KEY_PATH);
        nettyProdKeepAliveTimeout =
                properties.getLongProperty(PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT);
        nettyMaxConnectionAge =
                properties.getLongProperty(PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE);
        nettyMaxConnectionAgeGrace =
                properties.getLongProperty(PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE);
        nettyMaxConnectionIdle =
                properties.getLongProperty(PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE);
        nettyMaxConcurrentCalls =
                properties.getIntProperty(PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS);
        nettyFlowControlWindow =
                properties.getIntProperty(PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW);
        devListeningAccount =
                properties.getStringProperty(PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT);
        devOnlyDefaultNodeListens =
                properties.getBooleanProperty(PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS);
        accountsExportPath =
                properties.getStringProperty(PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH);
        exportAccountsOnStartup =
                properties.getBooleanProperty(PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP);
        nettyMode = properties.getProfileProperty(PropertyNames.NETTY_MODE);
        nettyStartRetries = properties.getIntProperty(PropertyNames.NETTY_START_RETRIES);
        nettyStartRetryIntervalMs =
                properties.getLongProperty(PropertyNames.NETTY_START_RETRY_INTERVAL_MS);
        numExecutionTimesToTrack =
                properties.getIntProperty(PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK);
        issResetPeriod = properties.getIntProperty(PropertyNames.ISS_RESET_PERIOD);
        issRoundsToLog = properties.getIntProperty(PropertyNames.ISS_ROUNDS_TO_LOG);
        prefetchQueueCapacity =
                properties.getIntProperty(PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY);
        prefetchThreadPoolSize =
                properties.getIntProperty(PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE);
        prefetchCodeCacheTtlSecs =
                properties.getIntProperty(PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS);
        consThrottlesToSample =
                properties.getStringsProperty(PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE);
        hapiThrottlesToSample =
                properties.getStringsProperty(PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE);
        entityUtilStatsUpdateIntervalMs =
                properties.getLongProperty(
                        PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS);
        throttleUtilStatsUpdateIntervalMs =
                properties.getLongProperty(
                        PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS);
    }

    public int port() {
        return port;
    }

    public int tlsPort() {
        return tlsPort;
    }

    public Profile activeProfile() {
        return activeProfile;
    }

    public long hapiOpsStatsUpdateIntervalMs() {
        return hapiOpStatsUpdateIntervalMs;
    }

    public double statsSpeedometerHalfLifeSecs() {
        return statsSpeedometerHalfLifeSecs;
    }

    public double statsRunningAvgHalfLifeSecs() {
        return statsRunningAvgHalfLifeSecs;
    }

    public String recordLogDir() {
        return recordLogDir;
    }

    public long recordLogPeriod() {
        return recordLogPeriod;
    }

    public String sidecarDir() {
        return sidecarDir;
    }

    public boolean isRecordStreamEnabled() {
        return recordStreamEnabled;
    }

    public int recordStreamQueueCapacity() {
        return recordStreamQueueCapacity;
    }

    public int queryBlobLookupRetries() {
        return queryBlobLookupRetries;
    }

    public long nettyProdKeepAliveTime() {
        return nettyProdKeepAliveTime;
    }

    public String nettyTlsCrtPath() {
        return nettyTlsCrtPath;
    }

    public String nettyTlsKeyPath() {
        return nettyTlsKeyPath;
    }

    public long nettyProdKeepAliveTimeout() {
        return nettyProdKeepAliveTimeout;
    }

    public long nettyMaxConnectionAge() {
        return nettyMaxConnectionAge;
    }

    public long nettyMaxConnectionAgeGrace() {
        return nettyMaxConnectionAgeGrace;
    }

    public long nettyMaxConnectionIdle() {
        return nettyMaxConnectionIdle;
    }

    public int nettyMaxConcurrentCalls() {
        return nettyMaxConcurrentCalls;
    }

    public int nettyFlowControlWindow() {
        return nettyFlowControlWindow;
    }

    public String devListeningAccount() {
        return devListeningAccount;
    }

    public boolean devOnlyDefaultNodeListens() {
        return devOnlyDefaultNodeListens;
    }

    public String accountsExportPath() {
        return accountsExportPath;
    }

    public boolean exportAccountsOnStartup() {
        return exportAccountsOnStartup;
    }

    public Profile nettyMode() {
        return nettyMode;
    }

    public int nettyStartRetries() {
        return nettyStartRetries;
    }

    public long nettyStartRetryIntervalMs() {
        return nettyStartRetryIntervalMs;
    }

    public int numExecutionTimesToTrack() {
        return numExecutionTimesToTrack;
    }

    public int issResetPeriod() {
        return issResetPeriod;
    }

    public int issRoundsToLog() {
        return issRoundsToLog;
    }

    public int prefetchQueueCapacity() {
        return prefetchQueueCapacity;
    }

    public int prefetchThreadPoolSize() {
        return prefetchThreadPoolSize;
    }

    public int prefetchCodeCacheTtlSecs() {
        return prefetchCodeCacheTtlSecs;
    }

    public List<String> consThrottlesToSample() {
        return consThrottlesToSample;
    }

    public List<String> hapiThrottlesToSample() {
        return hapiThrottlesToSample;
    }

    public long entityUtilStatsUpdateIntervalMs() {
        return entityUtilStatsUpdateIntervalMs;
    }

    public long throttleUtilStatsUpdateIntervalMs() {
        return throttleUtilStatsUpdateIntervalMs;
    }
}
