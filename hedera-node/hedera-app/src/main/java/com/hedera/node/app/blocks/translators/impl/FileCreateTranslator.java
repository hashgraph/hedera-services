/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.translators.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.config.types.EntityType.FILE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.blocks.translators.BaseTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionParts;
import com.hedera.node.app.blocks.translators.BlockTransactionPartsTranslator;
import com.hedera.node.app.state.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Translates a file create transaction into a {@link SingleTransactionRecord}.
 */
public class FileCreateTranslator implements BlockTransactionPartsTranslator {
    private static final Logger log = LogManager.getLogger(FileCreateTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            if (parts.status() == SUCCESS) {
                final var createdNum = baseTranslator.nextCreatedNum(FILE);
                final var iter = remainingStateChanges.listIterator();
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasFileIdKey()) {
                        final var fileId =
                                stateChange.mapUpdateOrThrow().keyOrThrow().fileIdKeyOrThrow();
                        if (fileId.fileNum() == createdNum) {
                            receiptBuilder.fileID(fileId);
                            iter.remove();
                            return;
                        }
                    }
                }
                log.error(
                        "No matching state change found for successful file create with id {}",
                        parts.transactionIdOrThrow());
            }
        });
    }
}
