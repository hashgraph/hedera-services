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

package com.hedera.node.blocknode.config.data;

import com.hedera.node.blocknode.config.types.FileSystem;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("blockNodeFileSystem")
public record BlockNodeFileSystemConfig(
        @ConfigProperty(defaultValue = "LOCAL") FileSystem fileSystem,
        @ConfigProperty(defaultValue = "/block-node/blocknode-core/build/blocks/")
                String blocksExportPath,
        @ConfigProperty(defaultValue = "/hedera-node/hedera-app/build/node/data/block-streams/block0.0.3/")
                String blocksImportPath
        ) {}

