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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeys;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateContent;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.hapi.fees.usage.file.FileOpsUsage;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.service.mono.fees.calculation.file.txns.FileCreateResourceUsage;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.ExpiryMeta;
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
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_CREATE}.
 */
@Singleton
public class FileCreateHandler implements TransactionHandler {
    private final FileOpsUsage fileOpsUsage;

    @Inject
    public FileCreateHandler(final FileOpsUsage fileOpsUsage) {
        this.fileOpsUsage = fileOpsUsage;
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for create a file
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if any issue happens on the pre handle level
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var transactionBody = context.body().fileCreateOrThrow();

        validateAndAddRequiredKeys(null, transactionBody.keys(), context);

        if (!transactionBody.hasExpirationTime()) {
            throw new PreCheckException(INVALID_EXPIRATION_TIME);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext handleContext) throws HandleException {
        requireNonNull(handleContext);

        final var builder = new File.Builder();
        final var fileServiceConfig = handleContext.configuration().getConfigData(FilesConfig.class);

        final var fileCreateTransactionBody = handleContext.body().fileCreateOrThrow();

        // TODO: skip at least the mutability check for privileged "payer" accounts
        if (fileCreateTransactionBody.hasKeys()) {
            KeyList transactionKeyList = fileCreateTransactionBody.keys();
            builder.keys(transactionKeyList);
        }

        /* Validate if the current file can be created */
        final var fileStore = handleContext.writableStore(WritableFileStore.class);
        if (fileStore.sizeOfState() >= fileServiceConfig.maxNumber()) {
            throw new HandleException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        }

        var expiry = fileCreateTransactionBody.hasExpirationTime()
                ? fileCreateTransactionBody.expirationTimeOrThrow().seconds()
                : NA;
        final var entityExpiryMeta = new ExpiryMeta(
                expiry,
                NA,
                // Shard and realm will be ignored if num is NA
                null);

        try {
            final var effectiveExpiryMeta =
                    handleContext.expiryValidator().resolveCreationAttempt(false, entityExpiryMeta, false);
            builder.expirationSecond(effectiveExpiryMeta.expiry());

            handleContext.attributeValidator().validateMemo(fileCreateTransactionBody.memo());
            builder.memo(fileCreateTransactionBody.memo());

            builder.keys(fileCreateTransactionBody.keys());
            final var fileId = FileID.newBuilder()
                    .fileNum(handleContext.newEntityNum())
                    .shardNum(fileCreateTransactionBody.shardIDOrThrow().shardNum())
                    .realmNum(fileCreateTransactionBody.realmIDOrThrow().realmNum())
                    .build();
            builder.fileId(fileId);
            validateContent(PbjConverter.asBytes(fileCreateTransactionBody.contents()), fileServiceConfig);
            builder.contents(fileCreateTransactionBody.contents());

            final var file = builder.build();
            fileStore.put(file);

            handleContext.recordBuilder(CreateFileRecordBuilder.class).fileID(fileId);
        } catch (final HandleException e) {
            if (e.getStatus() == INVALID_EXPIRATION_TIME) {
                // Since for some reason CreateTransactionBody does not have an expiration time,
                // it makes more sense to propagate AUTORENEW_DURATION_NOT_IN_RANGE
                throw new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            throw e;
        }
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull FeeContext feeContext) {
        final var op = feeContext.body();
        return feeContext.feeCalculator(SubType.DEFAULT).legacyCalculate(sigValueObj -> {
            return new FileCreateResourceUsage(fileOpsUsage).usageGiven(fromPbj(op), sigValueObj);
        });
    }
}
