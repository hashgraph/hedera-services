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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.FilesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides utility methods for file operations.
 */
public class FileServiceUtils {

    /**
     * Validates the content of a file that it is not empty and not above max size 1MB.
     * @param content the file content
     * @param fileServiceConfig the file service configuration
     */
    public static void validateContent(@NonNull byte[] content, @NonNull FilesConfig fileServiceConfig) {
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
     * @param fileAppendTransactionBody
     * @param fileStore
     * @return
     * @throws PreCheckException
     */
    public static @NonNull FileMetadata preValidate(
            @NonNull final FileAppendTransactionBody fileAppendTransactionBody,
            @NonNull final ReadableFileStore fileStore)
            throws PreCheckException {
        requireNonNull(fileAppendTransactionBody);

        if (!fileAppendTransactionBody.hasFileID()) {
            throw new PreCheckException(INVALID_FILE_ID);
        }

        final var fileMeta = fileStore.getFileMetadata(fileAppendTransactionBody.fileID());
        mustExist(fileMeta, INVALID_FILE_ID);
        return fileMeta;
    }

    /**
     * The function validates the keys and adds them to the context.
     * @param listKeys the list of keys to validate and add to required keys in context
     * @param context the prehandle context for the transaction.
     * @param areKeysRequired create allows files to be created without additional keys. Therefore, this flag is needed.
     * @throws PreCheckException
     */
    public static void validateAndAddRequiredKeys(
            @Nullable final KeyList listKeys, @NonNull final PreHandleContext context, final boolean areKeysRequired)
            throws PreCheckException {
        if (listKeys == null || !listKeys.hasKeys() || listKeys.keys().isEmpty()) {
            // @todo('protobuf change needed') change to immutable file response code
            if (areKeysRequired) throw new PreCheckException(UNAUTHORIZED);
        }

        if (listKeys != null && listKeys.hasKeys()) for (Key key : listKeys.keys()) context.requireKey(key);
    }
}
