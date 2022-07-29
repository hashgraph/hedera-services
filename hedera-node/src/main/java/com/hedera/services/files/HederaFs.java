/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import com.hedera.services.files.TieredHederaFs.IllegalArgumentType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * A non-hierarchical collection of files managed by {@link FileID} using create/read/update/delete
 * semantics.
 *
 * <p>Each file has an associated expiration time and key encapsulated in a {@link HFileMeta}, which
 * also indicates if the file has been deleted. If a file is deleted before it expires, its contents
 * are no longer be mutable or readable; however, files are only purged from the system after they
 * expire.
 *
 * <p>The system's behavior can be extended by registering {@link FileUpdateInterceptor} instances.
 */
public interface HederaFs {
    interface UpdateResult {
        boolean fileReplaced();

        boolean attrChanged();

        ResponseCodeEnum outcome();
    }

    /**
     * Gives the number of registered interceptors.
     *
     * @return the number of registered interceptors
     */
    int numRegisteredInterceptors();

    /**
     * Registers a new {@link FileUpdateInterceptor} with the file system.
     *
     * @param updateInterceptor the interceptor to register
     */
    void register(FileUpdateInterceptor updateInterceptor);

    /**
     * Creates a new file in the collection with the given data and metadata.
     *
     * @param contents the data for the file
     * @param attr the metadata of the file
     * @param sponsor the payer for the creation of the file
     * @return a globally unique entity id
     * @throws IllegalArgumentException with {@link IllegalArgumentType#FILE_WOULD_BE_EXPIRED} if
     *     expiry is past
     * @throws IllegalArgumentException with {@link IllegalArgumentType#OVERSIZE_CONTENTS} if the
     *     data are too large
     */
    FileID create(byte[] contents, HFileMeta attr, AccountID sponsor);

    /**
     * Checks for existence of a the given file; this succeeds even after deletion.
     *
     * @param id the file to look for
     * @return its existence
     */
    boolean exists(FileID id);

    /**
     * Returns the contents of the given file.
     *
     * @param id the file to cat
     * @return its contents
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#DELETED_FILE} if the file is
     *     deleted
     */
    byte[] cat(FileID id);

    /**
     * Returns the metadata for the given file.
     *
     * @param id the file to examine
     * @return its metadata
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#DELETED_FILE} if the file is
     *     deleted
     */
    HFileMeta getattr(FileID id);

    /**
     * Updates the metadata for the given file. Although it is possible to delete a file with this
     * mechanism, prefer {@link HederaFs#delete(FileID)}.
     *
     * @param id the file to update
     * @param attr the new metadata
     * @return an {@link UpdateResult} summarizing the result of the update metadata attempt
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#DELETED_FILE} if the file is
     *     deleted
     * @throws IllegalArgumentException with {@link IllegalArgumentType#FILE_WOULD_BE_EXPIRED} if
     *     expiry is past
     */
    UpdateResult setattr(FileID id, HFileMeta attr);

    /**
     * Updates the metadata for the given file, even if it is deleted.
     *
     * @param id the file to update
     * @param attr the new metadata
     * @return an {@link UpdateResult} summarizing the result of the update metadata attempt
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#FILE_WOULD_BE_EXPIRED} if
     *     expiry is past
     */
    UpdateResult sudoSetattr(FileID id, HFileMeta attr);

    /**
     * Replaces the contents of the given file.
     *
     * @param id the file to replace
     * @param newContents its proposed contents
     * @return an {@link UpdateResult} summarizing the result of the update attempt
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if the file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#DELETED_FILE} if the file is
     *     deleted
     * @throws IllegalArgumentException with {@link IllegalArgumentType#OVERSIZE_CONTENTS} if the
     *     data are too large
     */
    UpdateResult overwrite(FileID id, byte[] newContents);

    /**
     * Extends the contents of the given file.
     *
     * @param id the file to extend
     * @param moreContents its proposed extension
     * @return an {@link UpdateResult} summarizing the result of the append attempt
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if the file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#DELETED_FILE} if the file is
     *     deleted
     * @throws IllegalArgumentException with {@link IllegalArgumentType#OVERSIZE_CONTENTS} if the
     *     extended data are too large
     */
    UpdateResult append(FileID id, byte[] moreContents);

    /**
     * Marks the given file as deleted and removes its data from the system.
     *
     * @param id the file to delete
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if the file is
     *     missing
     * @throws IllegalArgumentException with {@link IllegalArgumentType#DELETED_FILE} if the file is
     *     deleted
     */
    void delete(FileID id);

    /**
     * Removes the given file from the system (both metadata and data).
     *
     * @param id the file to purge
     * @throws IllegalArgumentException with {@link IllegalArgumentType#UNKNOWN_FILE} if the file is
     *     missing
     */
    void rm(FileID id);
}
