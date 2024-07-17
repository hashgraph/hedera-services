/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.node.config.NetworkProperty;
import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * Configuration for block streams. This configuration is enabled when BlockRecordStreamConfig is set to
 * recordFileVersion=8. It is an error for recordFileVersion != blockVersion.
 *
 * @param logDir directory for writing block files
 * @param compressFilesOnCreation when true record and sidecar files are compressed with GZip when created
 * @param numOfBlockHashesInState the number of block hashes to keep in state for block history
 * @param streamFileProducer the type of stream file producer to use. Currently only "concurrent" and "sequential" are supported
 */
@ConfigData("hedera.blockStream")
public record BlockStreamConfig(
        /* [file|grpc] */
        @ConfigProperty(defaultValue = "file") @NodeProperty String writer,
        @ConfigProperty(defaultValue = "data/block-streams") @NodeProperty String logDir,
        @ConfigProperty(defaultValue = "1") @Min(1) @NetworkProperty int numRoundsInBlock,
        @ConfigProperty(defaultValue = "8") @Min(1) @NetworkProperty int blockVersion,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean compressFilesOnCreation,
        @ConfigProperty(defaultValue = "256") @Min(1) @Max(4096) @NetworkProperty int numOfBlockHashesInState,
        /* [serial|concurrent] */
        @ConfigProperty(defaultValue = "serial") @NetworkProperty
                String streamFileProducer) {} // COULD BE NODE LOCAL PROPERTY OR NETWORK PROPERTY
