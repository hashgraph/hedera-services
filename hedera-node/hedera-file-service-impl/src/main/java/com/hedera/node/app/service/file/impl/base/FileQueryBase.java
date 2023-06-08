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

package com.hedera.node.app.service.file.impl.base;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.spi.validation.Validations.mustExist;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * Base class for file queries.
 */
public abstract class FileQueryBase extends PaidQueryHandler {

    @Override
    public boolean requiresNodePayment(@NonNull ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull ResponseType responseType) {
        return COST_ANSWER == responseType;
    }

    protected void validateFileExistence(@Nullable FileID fileID, @NonNull final QueryContext context)
            throws PreCheckException {
        final var fileStore = Objects.requireNonNull(context).createStore(ReadableFileStore.class);
        final var file = fileStore.getFileMetadata(fileID);
        mustExist(file, INVALID_FILE_ID);
        if (file.deleted()) {
            throw new PreCheckException(FILE_DELETED);
        }
    }
}
