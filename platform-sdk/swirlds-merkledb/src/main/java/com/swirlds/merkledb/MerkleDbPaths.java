/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import java.nio.file.Path;

/**
 * Simple class for building and holding the set of sub-paths for data in a MerkleDb datasource directory
 */
public class MerkleDbPaths {

    public final Path storageDir;
    public final Path metadataFile;
    public final Path pathToDiskLocationInternalNodesFile;
    public final Path pathToDiskLocationLeafNodesFile;
    public final Path hashStoreRamFile;
    public final Path hashStoreDiskDirectory;
    public final Path keyToPathDirectory;
    public final Path pathToKeyValueDirectory;

    /**
     * Create a set of all the sub-paths for stored data in a MerkleDb data source.
     *
     * @param storageDir
     * 		directory to store data files in
     */
    public MerkleDbPaths(final Path storageDir) {
        this.storageDir = storageDir;
        metadataFile = storageDir.resolve("table_metadata.pbj");
        pathToDiskLocationInternalNodesFile = storageDir.resolve("pathToDiskLocationInternalNodes.ll");
        pathToDiskLocationLeafNodesFile = storageDir.resolve("pathToDiskLocationLeafNodes.ll");
        hashStoreRamFile = storageDir.resolve("internalHashStoreRam.hl");
        hashStoreDiskDirectory = storageDir.resolve("internalHashStoreDisk");
        keyToPathDirectory = storageDir.resolve("objectKeyToPath");
        pathToKeyValueDirectory = storageDir.resolve("pathToHashKeyValue");
    }
}
