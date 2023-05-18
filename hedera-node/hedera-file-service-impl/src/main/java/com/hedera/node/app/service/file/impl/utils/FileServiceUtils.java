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

package com.hedera.node.app.service.file.impl.utils;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_CONTENT_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.validation.Validations.mustExist;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.FilesConfig;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/** Provides utility methods for file operations. */
public class FileServiceUtils {

    /**
     * Validates the content of a file that it is not empty and not above max size 1MB.
     *
     * @param content the file content
     * @param fileServiceConfig the file service configuration
     */
    public static void validateContent(
            @NonNull byte[] content, @NonNull FilesConfig fileServiceConfig) {
        var contentLength = content.length;

        if (contentLength <= 0) {
            throw new HandleException(FILE_CONTENT_EMPTY);
        }

        if (contentLength > fileServiceConfig.maxSizeKb() * 1024L) {
            throw new HandleException(MAX_FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * The function validation the file id and returns the file metadata.
     *
     * @param fileId the file id to validate and to fetch the metadata
     * @param fileStore the file store to fetch the metadata of specified file id
     * @return the file metadata of specific file id
     * @throws PreCheckException if the file id is invalid or the file does not exist
     */
    public static @NonNull FileMetadata preValidate(
            @Nullable final FileID fileId, @NonNull final ReadableFileStore fileStore)
            throws PreCheckException {

        if (fileId == null) {
            throw new PreCheckException(INVALID_FILE_ID);
        }

        final var fileMeta = fileStore.getFileMetadata(fileId);
        mustExist(fileMeta, INVALID_FILE_ID);
        return fileMeta;
    }

    /**
     * The function validates the keys and adds them to the context.
     *
     * @param listKeys the list of keys to validate and add to required keys in context
     * @param context the prehandle context for the transaction.
     * @param areKeysRequired create allows files to be created without additional keys. Therefore,
     *     this flag is needed.
     * @throws PreCheckException
     */
    public static void validateAndAddRequiredKeys(
            @Nullable final KeyList listKeys,
            @NonNull final PreHandleContext context,
            final boolean areKeysRequired)
            throws PreCheckException {
        if (listKeys == null || !listKeys.hasKeys() || listKeys.keys().isEmpty()) {
            // @todo('protobuf change needed') change to immutable file response code
            if (areKeysRequired) throw new PreCheckException(UNAUTHORIZED);
        }

        if (listKeys != null && listKeys.hasKeys())
            for (Key key : listKeys.keys()) context.requireKey(key);
    }

    /**
     * The function validates the file id and returns the file metadata for system files.
     *
     * @param handleContext the handle context for the transaction.
     * @param fileStore the file store to fetch the metadata of specified file id
     * @param fileId the file id to validate and to fetch the metadata
     * @param canBeDeleted flag if the file can be deleted or not
     * @return the file metadata of specific system file id
     */
    public static @NonNull File verifyFileSystem(
            @NonNull final HandleContext handleContext,
            @NonNull final WritableFileStoreImpl fileStore,
            @NonNull final FileID fileId,
            final boolean canBeDeleted) {
        requireNonNull(handleContext);
        requireNonNull(fileStore);
        requireNonNull(fileId);

        var optionalFile = fileStore.get(fileId.fileNum());

        if (optionalFile.isEmpty()) {
            throw new HandleException(INVALID_FILE_ID);
        }

        // where to get the max special file number
        final var fileServiceConfig =
                handleContext.getConfiguration().getConfigData(FilesConfig.class);
        // TODO: change to the property file config value
        if (fileId.fileNum() > 750) {
            throw new HandleException(INVALID_FILE_ID);
        }

        final var file = optionalFile.get();

        if (!file.hasKeys() || file.keys().keys().isEmpty()) {
            // @todo('protobuf change needed') change to immutable file response code
            throw new HandleException(UNAUTHORIZED);
        }

        if (!canBeDeleted && file.deleted()) {
            throw new HandleException(FILE_DELETED);
        }

        return file;
    }

    /**
     * The function validates the file id and returns the file metadata for system files.
     *
     * @param handleContext the handle context for the transaction.
     * @param fileStore the file store to fetch the metadata of specified file id
     * @param fileId the file id to validate and to fetch the metadata
     * @return the file metadata of specific system file id
     */
    public static @NonNull File verifyFileSystem(
            @NonNull final HandleContext handleContext,
            @NonNull final WritableFileStoreImpl fileStore,
            @NonNull final FileID fileId) {
        return verifyFileSystem(handleContext, fileStore, fileId, false);
    }
}
