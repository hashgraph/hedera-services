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

package com.hedera.node.app.spi.config.data;

import com.hedera.node.app.spi.config.types.Profile;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.List;

/**
 * This class contains the properties that are part of the {@code NodeLocalProperties} class in the mono-service
 * module.
 */
@ConfigData
public record NodeConfig(
        @ConfigProperty int port,
        @ConfigProperty int tlsPort,
        @ConfigProperty long hapiOpStatsUpdateIntervalMs,
        @ConfigProperty long entityUtilStatsUpdateIntervalMs,
        @ConfigProperty long throttleUtilStatsUpdateIntervalMs,
        @ConfigProperty Profile activeProfile,
        @ConfigProperty double statsSpeedometerHalfLifeSecs,
        @ConfigProperty double statsRunningAvgHalfLifeSecs,
        @ConfigProperty String recordLogDir,
        @ConfigProperty long recordLogPeriod,
        @ConfigProperty boolean recordStreamEnabled,
        @ConfigProperty int recordStreamQueueCapacity,
        @ConfigProperty int queryBlobLookupRetries,
        @ConfigProperty long nettyProdKeepAliveTime,
        @ConfigProperty String nettyTlsCrtPath,
        @ConfigProperty String nettyTlsKeyPath,
        @ConfigProperty long nettyProdKeepAliveTimeout,
        @ConfigProperty long nettyMaxConnectionAge,
        @ConfigProperty long nettyMaxConnectionAgeGrace,
        @ConfigProperty long nettyMaxConnectionIdle,
        @ConfigProperty int nettyMaxConcurrentCalls,
        @ConfigProperty int nettyFlowControlWindow,
        @ConfigProperty String devListeningAccount,
        @ConfigProperty boolean devOnlyDefaultNodeListens,
        @ConfigProperty String accountsExportPath,
        @ConfigProperty boolean exportAccountsOnStartup,
        @ConfigProperty Profile nettyMode,
        @ConfigProperty int nettyStartRetries,
        @ConfigProperty long nettyStartRetryIntervalMs,
        @ConfigProperty int numExecutionTimesToTrack,
        @ConfigProperty int issResetPeriod,
        @ConfigProperty int issRoundsToLog,
        @ConfigProperty int prefetchQueueCapacity,
        @ConfigProperty int prefetchThreadPoolSize,
        @ConfigProperty int prefetchCodeCacheTtlSecs,
        @ConfigProperty List<String> consThrottlesToSample,
        @ConfigProperty List<String> hapiThrottlesToSample,
        @ConfigProperty String sidecarDir,
        @ConfigProperty int workflowsPort,
        @ConfigProperty int workflowsTlsPort) {}
