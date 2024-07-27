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
import com.hedera.node.config.types.BlockStreamWriterMode;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("blockStream")
public record BlockStreamConfig(
        // Default value of RECORDS disables the block stream; setting BLOCKS or BOTH enables it
        @ConfigProperty(defaultValue = "BOTH") @NetworkProperty StreamMode streamMode,
        /* [FILE|GRPC] */
        @ConfigProperty(defaultValue = "FILE") @NodeProperty BlockStreamWriterMode writerMode,
        @ConfigProperty(defaultValue = "data/block-streams") @NodeProperty String blockFileDir,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean compressFilesOnCreation) {}
