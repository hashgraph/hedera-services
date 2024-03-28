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
        @ConfigProperty(defaultValue = "/build/blocks/") String blocksExportPath,
        @ConfigProperty(defaultValue = "/hedera-node/hedera-app/build/node/hedera-node/data/block-streams/block0.0.3") String blocksImportPath,
        @ConfigProperty(defaultValue = "bucket1") String s3BucketName,
        @ConfigProperty(defaultValue = "us-east-1") String s3Region,
        @ConfigProperty(defaultValue = "http://localhost:9090") String s3Uri,
        @ConfigProperty(defaultValue = "valid-test-key-id") String s3AccessKeyId,
        @ConfigProperty(defaultValue = "1234567890") String s3SecretAccessKey) {}
