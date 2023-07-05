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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.file.FileGetInfoQuery;
import com.hedera.hapi.node.file.FileGetInfoResponse;
import com.hedera.hapi.node.file.FileInfo;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.base.FileQueryBase;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_GET_INFO}.
 */
@Singleton
public class FileGetInfoHandler extends FileQueryBase {

    @Inject
    public FileGetInfoHandler() {}

    @Override
    public @NonNull QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.fileGetInfoOrThrow().header();
    }

    @Override
    public @NonNull Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = FileGetInfoResponse.newBuilder().header(header);
        return Response.newBuilder().fileGetInfo(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        final var query = context.query();
        final FileGetInfoQuery op = query.fileGetInfoOrThrow();
        if (op.hasFileID()) {
            validateFileExistence(op.fileID(), context);
        }
    }

    @Override
    public @NonNull Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var query = context.query();
        final var fileStore = context.createStore(ReadableFileStore.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var op = query.fileGetInfoOrThrow();
        final var responseBuilder = FileGetInfoResponse.newBuilder();
        final var file = op.fileIDOrElse(FileID.DEFAULT);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        responseBuilder.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = infoForFile(file, fileStore, ledgerConfig);
            optionalInfo.ifPresent(responseBuilder::fileInfo);
        }

        return Response.newBuilder().fileGetInfo(responseBuilder).build();
    }

    /**
     * Provides information about a file.
     * @param fileID the file to get information about
     * @param fileStore the file store
     * @param ledgerConfig
     * @return the information about the file
     */
    private @Nullable Optional<FileInfo> infoForFile(
            @NonNull final FileID fileID,
            @NonNull final ReadableFileStore fileStore,
            @NonNull final LedgerConfig ledgerConfig) {
        final var meta = fileStore.getFileMetadata(fileID);
        if (meta == null) {
            return Optional.empty();
        } else {
            final var info = FileInfo.newBuilder();
            info.memo(meta.memo() == null ? "" : meta.memo());
            info.fileID(fileID);
            info.size(meta.contents().length());
            info.expirationTime(meta.expirationTimestamp());
            info.deleted(meta.deleted());
            info.keys(meta.keys());
            info.ledgerId(ledgerConfig.id());
            return Optional.of(info.build());
        }
    }
}
