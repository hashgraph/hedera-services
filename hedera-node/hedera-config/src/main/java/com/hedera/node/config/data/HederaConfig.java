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

package com.hedera.node.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("hedera")
public record HederaConfig(
        @ConfigProperty(defaultValue = "1001") long firstUserEntity,
        @ConfigProperty(defaultValue = "0") long realm,
        @ConfigProperty(defaultValue = "0") long shard,
        @ConfigProperty(value = "recordStream.sidecarMaxSizeMb", defaultValue = "256") int recordStreamSidecarMaxSizeMb,
        @ConfigProperty(value = "transaction.maxMemoUtf8Bytes", defaultValue = "100") int transactionMaxMemoUtf8Bytes,
        @ConfigProperty(value = "transaction.maxValidDuration", defaultValue = "180") long transactionMaxValidDuration,
        @ConfigProperty(value = "transaction.minValidDuration", defaultValue = "15") long transactionMinValidDuration,
        @ConfigProperty(value = "transaction.minValidityBufferSecs", defaultValue = "10")
                int transactionMinValidityBufferSecs,
        @ConfigProperty(value = "recordStream.recordFileVersion", defaultValue = "6") int recordStreamRecordFileVersion,
        @ConfigProperty(value = "recordStream.signatureFileVersion", defaultValue = "6")
                int recordStreamSignatureFileVersion,
        @ConfigProperty(value = "allowances.maxTransactionLimit", defaultValue = "20")
                int allowancesMaxTransactionLimit,
        @ConfigProperty(value = "allowances.maxAccountLimit", defaultValue = "100") int allowancesMaxAccountLimit,
        @ConfigProperty(value = "allowances.isEnabled", defaultValue = "true") boolean allowancesIsEnabled,
        @ConfigProperty(value = "recordStream.enableTraceabilityMigration", defaultValue = "true")
                boolean recordStreamEnableTraceabilityMigration,
        @ConfigProperty(value = "recordStream.compressFilesOnCreation", defaultValue = "true")
                boolean recordStreamCompressFilesOnCreation,
        @ConfigProperty(defaultValue = "data/onboard/exportedAccount.txt") String accountsExportPath,
        @ConfigProperty(defaultValue = "false") boolean exportAccountsOnStartup,
        @ConfigProperty(value = "prefetch.queueCapacity", defaultValue = "70000") int prefetchQueueCapacity,
        @ConfigProperty(value = "prefetch.threadPoolSize", defaultValue = "4") int prefetchThreadPoolSize,
        @ConfigProperty(value = "prefetch.codeCacheTtlSecs", defaultValue = "600") int prefetchCodeCacheTtlSecs,
        // @ConfigProperty(value = "profiles.active", defaultValue = "PROD") Profile profilesActive,
        @ConfigProperty(value = "profiles.active", defaultValue = "PROD") String activeProfile,
        @ConfigProperty(value = "recordStream.isEnabled", defaultValue = "true") boolean recordStreamIsEnabled,
        @ConfigProperty(value = "recordStream.logDir", defaultValue = "/opt/hgcapp/recordStreams")
                String recordStreamLogDir,
        @ConfigProperty(value = "recordStream.sidecarDir", defaultValue = "sidecar") String recordStreamSideCarDir,
        @ConfigProperty(value = "recordStream.logPeriod", defaultValue = "2") long recordStreamLogPeriod,
        @ConfigProperty(value = "recordStream.queueCapacity", defaultValue = "5000") int recordStreamQueueCapacity,
        @ConfigProperty(value = "recordStream.logEveryTransaction", defaultValue = "false")
                boolean recordStreamLogEveryTransaction,
        @ConfigProperty(value = "workflow.verificationTimeoutMS", defaultValue = "20000")
                long workflowVerificationTimeoutMS,
        // FUTURE: Set<HederaFunctionality>.
        @ConfigProperty(value = "workflows.enabled", defaultValue = "") String workflowsEnabled) {}
