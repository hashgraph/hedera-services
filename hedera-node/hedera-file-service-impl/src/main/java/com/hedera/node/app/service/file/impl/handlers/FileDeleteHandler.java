/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.preValidate;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeys;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#FILE_DELETE}.
 */
@Singleton
public class FileDeleteHandler implements TransactionHandler {
    @Inject
    public FileDeleteHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for deleting a file
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @throws PreCheckException if any issue happens on the pre handle level
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var transactionBody = context.body().fileDeleteOrThrow();
        final var fileStore = context.createStore(ReadableFileStoreImpl.class);
        final var fileMeta = preValidate(transactionBody.fileID(), fileStore, context, false);

        validateAndAddRequiredKeys(fileMeta.keys(), context, true);
    }

    @Override
    public void handle(@NonNull final HandleContext handleContext) throws HandleException {
        requireNonNull(handleContext);

        final var fileDeleteTransactionBody = handleContext.body().fileDeleteOrThrow();
        if (!fileDeleteTransactionBody.hasFileID()) {
            throw new HandleException(INVALID_FILE_ID);
        }
        var fileId = fileDeleteTransactionBody.fileIDOrThrow();

        final var fileStore = handleContext.writableStore(WritableFileStoreImpl.class);
        var optionalFile = fileStore.get(fileId);

        if (optionalFile.isEmpty()) {
            throw new HandleException(INVALID_FILE_ID);
        }

        final var file = optionalFile.get();

        if (!file.hasKeys() || file.keys().keys().isEmpty()) {
            // @todo('protobuf change needed') change to immutable file response code
            throw new HandleException(UNAUTHORIZED);
        }

        if (file.deleted()) {
            throw new HandleException(FILE_DELETED);
        }

        /* Copy part of the fields from existing, delete the file content and set the deleted flag  */
        final var fileBuilder = new File.Builder()
                .fileId(file.fileId())
                .expirationTime(file.expirationTime())
                .keys(file.keys())
                .contents(null)
                .memo(file.memo())
                .deleted(true);

        /* --- Put the modified file. It will be in underlying state's modifications map.
        It will not be committed to state until commit is called on the state.--- */
        fileStore.put(fileBuilder.build());
    }
}
