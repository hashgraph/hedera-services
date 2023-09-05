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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_CONTENT_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.preValidate;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeys;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateContent;
import static com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.WritableUpgradeFileStore;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#FILE_APPEND}.
 */
@Singleton
public class FileAppendHandler implements TransactionHandler {
    private static final Logger logger = LogManager.getLogger(FileAppendHandler.class);

    @Inject
    public FileAppendHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for append a file
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if any issue happens on the pre handle level
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var transactionBody = context.body().fileAppendOrThrow();
        final var fileStore = context.createStore(ReadableFileStore.class);
        final var transactionFileId = requireNonNull(transactionBody.fileID());
        preValidate(transactionFileId, fileStore, context, false);

        var file = fileStore.getFileLeaf(transactionFileId);
        validateAndAddRequiredKeys(file, null, context);
    }

    @Override
    public void handle(@NonNull final HandleContext handleContext) throws HandleException {
        requireNonNull(handleContext);

        final var fileAppend = handleContext.body().fileAppendOrThrow();
        final var target = fileAppend.fileID();
        final var data = fileAppend.contents();
        final var fileServiceConfig = handleContext.configuration().getConfigData(FilesConfig.class);
        if (data == null || data.length() <= 0) {
            logger.debug("FileAppend: No data to append");
        }

        if (target == null) {
            throw new HandleException(INVALID_FILE_ID);
        }

        // the update file always will be for the node, not a particular ledger that's why we just compare the num
        if (target.fileNum() == fileServiceConfig.upgradeFileNumber()) {
            handleAppendUpgradeFile(fileAppend, handleContext);
            return;
        }

        final var fileStore = handleContext.writableStore(WritableFileStore.class);
        final var optionalFile = fileStore.get(target);

        if (optionalFile.isEmpty()) {
            throw new HandleException(INVALID_FILE_ID);
        }
        final var file = optionalFile.get();

        feeCalculation(handleContext, fileAppend, file, fileServiceConfig);

        // TODO: skip at least the mutability check for privileged "payer" accounts

        // First validate this file is mutable; and the pending mutations are allowed
        validateFalse(file.keys() == null, UNAUTHORIZED);

        if (file.deleted()) {
            throw new HandleException(FILE_DELETED);
        }

        var contents = PbjConverter.asBytes(file.contents());

        if (data == null) {
            throw new HandleException(FILE_CONTENT_EMPTY);
        }
        var newContents = ArrayUtils.addAll(contents, PbjConverter.asBytes(data));
        validateContent(newContents, fileServiceConfig);
        /* Copy all the fields from existing file and change deleted flag */
        final var fileBuilder = new File.Builder()
                .fileId(file.fileId())
                .expirationSecond(file.expirationSecond())
                .keys(file.keys())
                .contents(Bytes.wrap(newContents))
                .memo(file.memo())
                .deleted(file.deleted());

        /* --- Put the modified file. It will be in underlying state's modifications map.
        It will not be committed to state until commit is called on the state.--- */
        fileStore.put(fileBuilder.build());
    }

    private void feeCalculation(
            HandleContext handleContext,
            FileAppendTransactionBody fileAppend,
            File file,
            @NonNull FilesConfig fileServiceConfig) {
        final var dataLength =
                (fileAppend.contents() != null) ? fileAppend.contents().length() : 0;

        /**
         * TODO: revisit after modularizaion completed
         * PR conversation: 8089
         */
        final long effectiveLifeTime;
        final var fileNum = file.fileId().fileNum();
        final var firstSoftwareUpdateFile =
                fileServiceConfig.softwareUpdateRange().left();
        final var lastSoftwareUpdateFile =
                fileServiceConfig.softwareUpdateRange().right();
        if (firstSoftwareUpdateFile <= fileNum && fileNum <= lastSoftwareUpdateFile) {
            effectiveLifeTime = THREE_MONTHS_IN_SECONDS;
        } else {
            final var effCreationTime =
                    handleContext.body().transactionID().transactionValidStart().seconds();
            final var effExpiration = (file.expirationSecond() > 0) ? file.expirationSecond() : effCreationTime;
            effectiveLifeTime = effExpiration - effCreationTime;
        }

        final var fees = handleContext
                .feeCalculator(SubType.DEFAULT)
                .addBytesPerTransaction(BASIC_ENTITY_ID_SIZE + dataLength)
                .addStorageBytesSeconds(dataLength * effectiveLifeTime)
                .calculate();

        handleContext.feeAccumulator().charge(handleContext.payer(), fees);
    }

    private void handleAppendUpgradeFile(FileAppendTransactionBody fileAppend, HandleContext handleContext) {
        final var fileStore = handleContext.writableStore(WritableUpgradeFileStore.class);
        File file = fileStore.peek();
        if (file == null) {
            throw new HandleException(INVALID_FILE_ID);
        }
        fileStore.append(fileAppend.contents());
    }
}
