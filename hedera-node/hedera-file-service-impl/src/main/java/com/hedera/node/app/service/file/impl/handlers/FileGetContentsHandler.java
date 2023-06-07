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
import com.hedera.hapi.node.file.FileContents;
import com.hedera.hapi.node.file.FileGetContentsQuery;
import com.hedera.hapi.node.file.FileGetContentsResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.base.FileQueryBase;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_GET_CONTENTS}.
 */
@Singleton
public class FileGetContentsHandler extends FileQueryBase {

    @Inject
    public FileGetContentsHandler() {}

    @Override
    public @NonNull QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.fileGetContentsOrThrow().header();
    }

    @Override
    public @NonNull Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = FileGetContentsResponse.newBuilder().header(header);
        return Response.newBuilder().fileGetContents(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        final var query = context.query();
        final FileGetContentsQuery op = query.fileGetContentsOrThrow();
        if (op.hasFileID()) {
            validateFileExistence(op.fileID(), context);
        }
    }

    @Override
    public @NonNull Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var query = context.query();
        final var fileStore = context.createStore(ReadableFileStore.class);
        final var op = query.fileGetContentsOrThrow();
        final var responseBuilder = FileGetContentsResponse.newBuilder();
        final var file = op.fileIDOrElse(FileID.DEFAULT);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        responseBuilder.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = contentFile(file, fileStore);
            optionalInfo.ifPresent(responseBuilder::fileContents);
        }

        return Response.newBuilder().fileGetContents(responseBuilder).build();
    }

    /**
     * Provides file content about a file.
     * @param fileID the file to get information about
     * @param fileStore the file store
     * @return the content about the file
     */
    private @Nullable Optional<FileContents> contentFile(
            @NonNull final FileID fileID, @NonNull final ReadableFileStore fileStore) {
        final var meta = fileStore.getFileMetadata(fileID);
        if (meta == null) {
            return Optional.empty();
        } else {
            final var info = FileContents.newBuilder();
            info.fileID(fileID);
            info.contents(meta.contents());
            return Optional.of(info.build());
        }
    }
}
