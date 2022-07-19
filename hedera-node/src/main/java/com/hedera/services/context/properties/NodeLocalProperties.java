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

import com.hedera.services.context.annotations.CompositeProps;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NodeLocalProperties {
    private final PropertySource properties;

    private int port;
    private int tlsPort;
    private long statsHapiOpsSpeedometerUpdateIntervalMs;
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
    private boolean dumpFcmsOnIss;
    private int numExecutionTimesToTrack;
    private int issResetPeriod;
    private int issRoundsToDump;
    private int prefetchQueueCapacity;
    private int prefetchThreadPoolSize;
    private int prefetchCodeCacheTtlSecs;
    private String sidecarDir;

    @Inject
    public NodeLocalProperties(@CompositeProps PropertySource properties) {
        this.properties = properties;

        reload();
    }

    public void reload() {
        port = properties.getIntProperty("grpc.port");
        tlsPort = properties.getIntProperty("grpc.tlsPort");
        activeProfile = properties.getProfileProperty("hedera.profiles.active");
        statsHapiOpsSpeedometerUpdateIntervalMs =
                properties.getLongProperty("stats.hapiOps.speedometerUpdateIntervalMs");
        statsSpeedometerHalfLifeSecs =
                properties.getDoubleProperty("stats.speedometerHalfLifeSecs");
        statsRunningAvgHalfLifeSecs = properties.getDoubleProperty("stats.runningAvgHalfLifeSecs");
        recordLogDir = properties.getStringProperty("hedera.recordStream.logDir");
        sidecarDir = properties.getStringProperty("hedera.recordStream.sidecarDir");
        recordLogPeriod = properties.getLongProperty("hedera.recordStream.logPeriod");
        recordStreamEnabled = properties.getBooleanProperty("hedera.recordStream.isEnabled");
        recordStreamQueueCapacity = properties.getIntProperty("hedera.recordStream.queueCapacity");
        queryBlobLookupRetries = properties.getIntProperty("queries.blob.lookupRetries");
        nettyProdKeepAliveTime = properties.getLongProperty("netty.prod.keepAliveTime");
        nettyTlsCrtPath = properties.getStringProperty("netty.tlsCrt.path");
        nettyTlsKeyPath = properties.getStringProperty("netty.tlsKey.path");
        nettyProdKeepAliveTimeout = properties.getLongProperty("netty.prod.keepAliveTimeout");
        nettyMaxConnectionAge = properties.getLongProperty("netty.prod.maxConnectionAge");
        nettyMaxConnectionAgeGrace = properties.getLongProperty("netty.prod.maxConnectionAgeGrace");
        nettyMaxConnectionIdle = properties.getLongProperty("netty.prod.maxConnectionIdle");
        nettyMaxConcurrentCalls = properties.getIntProperty("netty.prod.maxConcurrentCalls");
        nettyFlowControlWindow = properties.getIntProperty("netty.prod.flowControlWindow");
        devListeningAccount = properties.getStringProperty("dev.defaultListeningNodeAccount");
        devOnlyDefaultNodeListens = properties.getBooleanProperty("dev.onlyDefaultNodeListens");
        accountsExportPath = properties.getStringProperty("hedera.accountsExportPath");
        exportAccountsOnStartup = properties.getBooleanProperty("hedera.exportAccountsOnStartup");
        nettyMode = properties.getProfileProperty("netty.mode");
        nettyStartRetries = properties.getIntProperty("netty.startRetries");
        nettyStartRetryIntervalMs = properties.getLongProperty("netty.startRetryIntervalMs");
        dumpFcmsOnIss = properties.getBooleanProperty("iss.dumpFcms");
        numExecutionTimesToTrack = properties.getIntProperty("stats.executionTimesToTrack");
        issResetPeriod = properties.getIntProperty("iss.resetPeriod");
        issRoundsToDump = properties.getIntProperty("iss.roundsToDump");
        prefetchQueueCapacity = properties.getIntProperty("hedera.prefetch.queueCapacity");
        prefetchThreadPoolSize = properties.getIntProperty("hedera.prefetch.threadPoolSize");
        prefetchCodeCacheTtlSecs = properties.getIntProperty("hedera.prefetch.codeCacheTtlSecs");
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

    public long statsHapiOpsSpeedometerUpdateIntervalMs() {
        return statsHapiOpsSpeedometerUpdateIntervalMs;
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

    public String sidecarDir() {
        return sidecarDir;
    }

    public long recordLogPeriod() {
        return recordLogPeriod;
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

    public boolean shouldDumpFcmsOnIss() {
        return dumpFcmsOnIss;
    }

    public int numExecutionTimesToTrack() {
        return numExecutionTimesToTrack;
    }

    public int issResetPeriod() {
        return issResetPeriod;
    }

    public int issRoundsToDump() {
        return issRoundsToDump;
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
}
