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

package com.hedera.node.app.records;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for the record streams, All properties with prefix "hedera.recordStream".
 *
 * @param isEnabled if we should write record files
 * @param logDir directory for writing record files
 * @param sidecarDir directory for writing sidecar files, it is specified relative to logDir; blank==same dir
 * @param logPeriod the number of seconds in consensus time between writing record files
 * @param queueCapacity ?? the number of files to queue for writing before blocking
 * @param logEveryTransaction ??
 * @param sidecarMaxSizeMb the maximum size of a sidecar file in MB before rolling over to a new file
 * @param recordFileVersion the format version number for record files
 * @param signatureFileVersion the format version number for signature files
 * @param enableTraceabilityMigration ??
 * @param compressFilesOnCreation when true record and sidecar files are compressed with GZip when created
 * @param numOfBlockHashesInState the number of block hashes to keep in state for block history
 */
@ConfigData("hedera.recordStream")
public record BlockRecordStreamConfig(
        @ConfigProperty(defaultValue = "true") boolean isEnabled,
        @ConfigProperty(defaultValue = "hedera-node/data/recordStreams") String logDir,
        @ConfigProperty(defaultValue = "sidecar") String sidecarDir,
        @ConfigProperty(defaultValue = "2") int logPeriod,
        @ConfigProperty(defaultValue = "5000") int queueCapacity,
        @ConfigProperty(defaultValue = "false") boolean logEveryTransaction,
        @ConfigProperty(defaultValue = "256") int sidecarMaxSizeMb,
        @ConfigProperty(defaultValue = "6") int recordFileVersion,
        @ConfigProperty(defaultValue = "6") int signatureFileVersion,
        @ConfigProperty(defaultValue = "true") boolean enableTraceabilityMigration,
        @ConfigProperty(defaultValue = "true") boolean compressFilesOnCreation,
        @ConfigProperty(defaultValue = "256") int numOfBlockHashesInState) {}
