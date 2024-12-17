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

package com.hedera.node.app.service.file;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Files.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public interface ReadableFileStore {

    /**
     * Returns the file metadata needed. If the file doesn't exist returns failureReason. If the
     * file exists , the failure reason will be null.
     *
     * @param id file id being looked up
     * @return file's metadata
     */
    // TODO : Change to return File instead of FileMetadata
    @Nullable
    FileMetadata getFileMetadata(@NonNull FileID id);

    /**
     * Returns the file needed from state, if not exist will return null.
     * @param id file id being looked up
     * @return file if found, null otherwise
     */
    @Nullable
    File getFileLeaf(@NonNull FileID id);

    /**
     * Returns the number of files in the state.
     *
     * @return the number of files in the state
     */
    long sizeOfState();
}
