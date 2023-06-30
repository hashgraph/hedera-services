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
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.preValidate;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeys;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.FilesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_UPDATE}.
 */
@Singleton
public class FileUpdateHandler implements TransactionHandler {
    @Inject
    public FileUpdateHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for update a file
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *                passed to {@code #handle()}
     * @throws PreCheckException if any issue happens on the pre handle level
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var transactionBody = context.body().fileUpdateOrThrow();
        final var fileStore = context.createStore(ReadableFileStore.class);

        preValidate(transactionBody.fileID(), fileStore, context, false);
        validateAndAddRequiredKeys(transactionBody.keys(), context, true);
    }

    @Override
    public void handle(@NonNull final HandleContext handleContext) throws HandleException {
        requireNonNull(handleContext);

        final var fileStore = handleContext.writableStore(WritableFileStoreImpl.class);
        final var fileUpdate = handleContext.body().fileUpdateOrThrow();
        final var maybeFile = fileStore.get(fileUpdate.fileIDOrElse(FileID.DEFAULT));

        final var fileServiceConfig = handleContext.configuration().getConfigData(FilesConfig.class);

        if (maybeFile.isEmpty()) {
            throw new HandleException(INVALID_FILE_ID);
        }

        final var file = maybeFile.get();
        validateFalse(file.deleted(), FILE_DELETED);

        // First validate this file is mutable; and the pending mutations are allowed
        validateFalse(file.keys() == null && wantsToMutateNonExpiryField(fileUpdate), UNAUTHORIZED);

        validateMaybeNewMemo(handleContext.attributeValidator(), fileUpdate);

        // Now we apply the mutations to a builder
        final var builder = new File.Builder();
        // But first copy over the immutable topic attributes to the builder
        builder.fileId(file.fileId());
        builder.deleted(file.deleted());

        // And then resolve mutable attributes, and put the new topic back
        resolveMutableBuilderAttributes(fileUpdate, builder, fileServiceConfig, file);
        fileStore.put(builder.build());
    }

    private void resolveMutableBuilderAttributes(
            @NonNull final FileUpdateTransactionBody op,
            @NonNull final File.Builder builder,
            @NonNull final FilesConfig fileServiceConfig,
            @NonNull final File file) {
        if (op.hasKeys()) {
            builder.keys(op.keys());
        } else {
            builder.keys(file.keys());
        }
        var contentLength = op.contents().length();
        if (contentLength > 0) {
            if (contentLength > fileServiceConfig.maxSizeKb() * 1024L) {
                throw new HandleException(MAX_FILE_SIZE_EXCEEDED);
            }
            builder.contents(op.contents());
        } else {
            builder.contents(file.contents());
        }

        if (op.hasMemo()) {
            builder.memo(op.memo());
        } else {
            builder.memo(file.memo());
        }

        if (op.hasExpirationTime() && op.expirationTime().seconds() > file.expirationTime()) {
            builder.expirationTime(op.expirationTime().seconds());
        } else {
            builder.expirationTime(file.expirationTime());
        }
    }

    public static boolean wantsToMutateNonExpiryField(@NonNull final FileUpdateTransactionBody op) {
        return op.hasMemo() || op.hasKeys();
    }

    private void validateMaybeNewMemo(
            @NonNull final AttributeValidator attributeValidator, @NonNull final FileUpdateTransactionBody op) {
        if (op.hasMemo()) {
            attributeValidator.validateMemo(op.memo());
        }
    }
}
