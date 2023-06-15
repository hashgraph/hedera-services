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

package com.hedera.node.app.service.networkadmin;

import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with update files used in freeze transactions.
 * <br/>
 */
// This is a temporary location for this interface. It will be replaced by a new interface in FileService.
// @todo('Issue #6856')
public interface ReadableUpdateFileStore {

    /**
     * Gets the freeze file with the given FileID. If there is no file with given FileID then
     * returns {@link Optional#empty()}.
     *
     * @param fileId given id for the file
     * @return the file with the given id
     */
    @NonNull
    Optional<byte[]> get(FileID fileId);

    /**
     * Get the file ID of the prepared update file. If no prepared update file has been set
     * (i.e. if the network is not in the process of an upgrade), this method will return {@link Optional#empty()}.
     * @return the file ID of the prepared update file, or {@link Optional#empty()} if no prepared update file has been set
     */
    @NonNull
    Optional<FileID> updateFileID();

    /**
     * Get the hash of the prepared update file. If no prepared update file has been set
     * (i.e. if the network is not in the process of an upgrade), this method will return {@link Optional#empty()}.
     * @return the hash of the prepared update file, or {@link Optional#empty()} if no prepared update file has been set
     */
    @NonNull
    Optional<Bytes> updateFileHash();
}
