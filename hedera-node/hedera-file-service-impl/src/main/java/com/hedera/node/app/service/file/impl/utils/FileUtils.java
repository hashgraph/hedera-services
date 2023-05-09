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
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.config.FileServiceConfig;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides utility methods for file operations.
 */
public class FileUtils {
    private static final Logger logger = LogManager.getLogger(FileUtils.class);

    public static void validateContent(@NonNull byte[] content, @NonNull FileServiceConfig fileServiceConfig) {
        var contentLength = content.length;

        if (contentLength <= 0) {
            throw new HandleException(FILE_CONTENT_EMPTY);
        }

        if (contentLength > fileServiceConfig.maxSizeKB() * 1024L) {
            throw new HandleException(MAX_FILE_SIZE_EXCEEDED);
        }
    }

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
}
