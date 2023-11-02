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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.preValidate;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeysForDelete;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.verifyNotSystemFile;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.hapi.utils.fee.FileFeeBuilder;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.mono.fees.calculation.file.txns.FileDeleteResourceUsage;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#FILE_DELETE}.
 */
@Singleton
public class FileDeleteHandler implements TransactionHandler {
    private final FileFeeBuilder usageEstimator;

    @Inject
    public FileDeleteHandler(final FileFeeBuilder usageEstimator) {
        this.usageEstimator = usageEstimator;
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
        final var fileStore = context.createStore(ReadableFileStore.class);
        final var transactionFileId = requireNonNull(transactionBody.fileID());
        preValidate(transactionFileId, fileStore, context, true);

        var file = fileStore.getFileLeaf(transactionFileId);
        validateAndAddRequiredKeysForDelete(file, context);
    }

    @Override
    public void handle(@NonNull final HandleContext handleContext) throws HandleException {
        requireNonNull(handleContext);

        final var fileDeleteTransactionBody = handleContext.body().fileDeleteOrThrow();
        if (!fileDeleteTransactionBody.hasFileID()) {
            throw new HandleException(INVALID_FILE_ID);
        }
        var fileId = fileDeleteTransactionBody.fileIDOrThrow();

        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var fileStore = handleContext.writableStore(WritableFileStore.class);

        final File file = verifyNotSystemFile(ledgerConfig, fileStore, fileId);

        // First validate this file is mutable; and the pending mutations are allowed
        validateFalse(file.keys() == null, UNAUTHORIZED);

        /* Copy part of the fields from existing, delete the file content and set the deleted flag  */
        final var fileBuilder = new File.Builder()
                .fileId(file.fileId())
                .expirationSecond(file.expirationSecond())
                .keys(file.keys())
                .contents(Bytes.EMPTY)
                .memo(file.memo())
                .deleted(true);

        /* --- Put the modified file. It will be in underlying state's modifications map.
        It will not be committed to state until commit is called on the state.--- */
        fileStore.put(fileBuilder.build());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        final var op = feeContext.body();
        return feeContext.feeCalculator(SubType.DEFAULT).legacyCalculate(sigValueObj -> new FileDeleteResourceUsage(
                        usageEstimator)
                .usageGiven(fromPbj(op), sigValueObj));
    }
}
