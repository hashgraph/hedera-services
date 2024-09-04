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

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.blocks.translators.BaseTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionPartsTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Defines a translator for transactions that can remove pending airdrops.
 */
public enum AirdropRemovalTranslator implements BlockTransactionPartsTranslator {
    AIRDROP_REMOVAL_TRANSLATOR;

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            if (parts.status() == SUCCESS) {
                for (final var stateChange : remainingStateChanges) {
                    if (stateChange.hasMapDelete()
                            && stateChange.mapDeleteOrThrow().keyOrThrow().hasPendingAirdropIdKey()) {
                        baseTranslator.remove(
                                stateChange.mapDeleteOrThrow().keyOrThrow().pendingAirdropIdKeyOrThrow());
                    }
                }
            }
        });
    }
}
