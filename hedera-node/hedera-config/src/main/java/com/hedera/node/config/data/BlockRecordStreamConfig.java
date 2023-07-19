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
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * Configuration for record streams.
 *
 * @param enabled if we should write record streams
 * @param logDir directory for writing record files
 * @param sidecarDir directory for writing sidecar files, it is specified relative to logDir; blank==same dir
 * @param logPeriod the number of seconds in consensus time between writing record files
 * @param queueCapacity ?? the number of files to queue for writing before blocking
 * @param sidecarMaxSizeMb the maximum size of a sidecar file in MB before rolling over to a new file
 * @param recordFileVersion the format version number for record files
 * @param signatureFileVersion the format version number for signature files
 * @param enableTraceabilityMigration ??
 * @param compressFilesOnCreation when true record and sidecar files are compressed with GZip when created
 * @param numOfBlockHashesInState the number of block hashes to keep in state for block history
 * @param streamFileProducer the type of stream file producer to use. Currently only "concurrent" is supported
 */
@ConfigData("hedera.recordStream")
public record BlockRecordStreamConfig(
        @ConfigProperty(defaultValue = "true") boolean enabled,
        @ConfigProperty(defaultValue = "hedera-node/data/recordStreams") String logDir, // NODE LOCAL PROPERTY
        @ConfigProperty(defaultValue = "sidecar") String sidecarDir, // NODE LOCAL PROPERTY
        @ConfigProperty(defaultValue = "2") @Min(1) int logPeriod, // NETWORK PROPERTY
        @ConfigProperty(defaultValue = "5000") @Min(1) int queueCapacity, // COULD BE NODE LOCAL PROPERTY
        @ConfigProperty(defaultValue = "256") @Min(1) @Max(1024) int sidecarMaxSizeMb, // NETWORK PROPERTY
        @ConfigProperty(defaultValue = "6") @Min(1) int recordFileVersion, // NETWORK PROPERTY
        @ConfigProperty(defaultValue = "6") @Min(1) int signatureFileVersion, // NETWORK PROPERTY
        @ConfigProperty(defaultValue = "true") boolean enableTraceabilityMigration, // ??
        @ConfigProperty(defaultValue = "true") boolean compressFilesOnCreation, // NOT SURE
        @ConfigProperty(defaultValue = "256") @Min(1) @Max(4096) int numOfBlockHashesInState, // NETWORK PROPERTY
        @ConfigProperty(defaultValue = "concurrent")
                String streamFileProducer) {} // COULD BE NODE LOCAL PROPERTY OR NETWORK PROPERTY
