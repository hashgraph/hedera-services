/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.merkle.disk.blockstream;

import com.hedera.node.config.NetworkProperty;
import com.hedera.node.config.NodeProperty;
import com.hedera.node.config.data.BlockRecordStreamConfig;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.annotation.ConstraintMethod;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;
import com.swirlds.config.extensions.validators.DefaultConfigViolation;

/**
 * Configuration for block streams. This configuration is enabled when BlockRecordStreamConfig is set to
 * recordFileVersion=7. It is an error for recordFileVersion != blockVersion.
 *
 * @param enabled if we should write record streams
 * @param logDir directory for writing block files
 * @param compressFilesOnCreation when true record and sidecar files are compressed with GZip when created
 * @param numOfBlockHashesInState the number of block hashes to keep in state for block history
 * @param streamFileProducer the type of stream file producer to use. Currently only "concurrent" and "sequential" are supported
 */
@ConfigData("hedera.blockStream")
public record BlockStreamConfig(
        @ConfigProperty(defaultValue = "true") @NodeProperty boolean enabled,
        /* [file|grpc] */
        @ConfigProperty(defaultValue = "file") @NodeProperty String writer,
        @ConfigProperty(defaultValue = "hedera-node/data/block-streams") @NodeProperty String logDir,
        @ConfigProperty(defaultValue = "1") @Min(1) @NetworkProperty int numRoundsInBlock,
        @ConstraintMethod("checkCompatibility") @ConfigProperty(defaultValue = "7") @Min(1) @NetworkProperty
                int blockVersion,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean logEveryTransaction,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean compressFilesOnCreation,
        @ConfigProperty(defaultValue = "256") @Min(1) @Max(4096) @NetworkProperty int numOfBlockHashesInState,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean writeSignatureFile,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean writeBlockProof,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean collectSignaturesInPreHandle,
        /* [serial|concurrent] */
        @ConfigProperty(defaultValue = "concurrent") @NetworkProperty String streamFileProducer) {

    public ConfigViolation checkCompatibility(final Configuration configuration) {
        // configs `recordFileVersion` and `blockVersion` should match
        final long recordFileVersion =
                configuration.getConfigData(BlockRecordStreamConfig.class).recordFileVersion();
        final long blockVersion =
                configuration.getConfigData(BlockStreamConfig.class).blockVersion();
        if (recordFileVersion != blockVersion) {
            return new DefaultConfigViolation(
                    "blockVersion",
                    "%d".formatted(blockVersion),
                    true,
                    "Configs recordFileVersion = " + recordFileVersion + " and blockVersion = " + blockVersion
                            + " should match");
        }
        return null;
    }
}
