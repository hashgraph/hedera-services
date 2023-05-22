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

import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.preValidate;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeys;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.records.DeleteFileRecordBuilder;
import com.hedera.node.app.service.file.impl.utils.FileServiceUtils;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#SYSTEM_UNDELETE}.
 */
@Singleton
public class FileSystemUndeleteHandler implements TransactionHandler {
    @Inject
    public FileSystemUndeleteHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for undelete system file
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @throws NullPointerException if any of the arguments are {@code null}
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var transactionBody = context.body().systemUndeleteOrThrow();
        final var fileStore = context.createStore(ReadableFileStoreImpl.class);
        final var fileMeta = preValidate(transactionBody.fileID(), fileStore, context, true);

        validateAndAddRequiredKeys(fileMeta.keys(), context, true);
    }

    /**
     * Given the appropriate context, undelete system file.
     *
     * @param systemUndeleteTransactionBody the {@link SystemUndeleteTransactionBody} of the active
     *     system file undelete transaction
     * @param fileStore the {@link WritableFileStoreImpl} to use to delete the file
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final SystemUndeleteTransactionBody systemUndeleteTransactionBody,
            @NonNull final WritableFileStoreImpl fileStore) {
        requireNonNull(handleContext);
        requireNonNull(systemUndeleteTransactionBody);
        requireNonNull(fileStore);

        final var fileId = systemUndeleteTransactionBody.fileIDOrElse(FileID.DEFAULT);

        final File file = FileServiceUtils.verifySystemFile(handleContext, fileStore, fileId, true);

        final var oldExpiry = file.expirationTime();
        if (oldExpiry <= handleContext.consensusNow().getEpochSecond()) {
            fileStore.removeFile(fileId.fileNum());
        } else {
            /* Copy all the fields from existing topic and change deleted flag */
            final var fileBuilder = new File.Builder()
                    .fileNumber(file.fileNumber())
                    .expirationTime(file.expirationTime())
                    .keys(file.keys())
                    .contents(file.contents())
                    .memo(file.memo())
                    .deleted(false);

            /* --- Put the modified file. It will be in underlying state's modifications map.
            It will not be committed to state until commit is called on the state.--- */
            fileStore.put(fileBuilder.build());
        }
    }

    /** {@inheritDoc} */
    @Override
    public DeleteFileRecordBuilder newRecordBuilder() {
        return new DeleteFileRecordBuilder();
    }
}
