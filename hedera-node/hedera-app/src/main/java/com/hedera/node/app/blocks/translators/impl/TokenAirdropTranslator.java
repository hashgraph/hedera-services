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
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.PENDING_AIRDROP_ID_COMPARATOR;
import static java.util.Comparator.comparing;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.blocks.translators.BaseTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionPartsTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a translator for a token airdrop into a {@link SingleTransactionRecord}.
 */
public class TokenAirdropTranslator implements BlockTransactionPartsTranslator {
    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            if (parts.status() == SUCCESS) {
                parts.outputIfPresent(TransactionOutput.TransactionOneOfType.TOKEN_AIRDROP)
                        .ifPresent(output -> recordBuilder.assessedCustomFees(
                                output.tokenAirdropOrThrow().assessedCustomFees()));
                final List<PendingAirdropRecord> pendingAirdrops = new ArrayList<>();
                // Note we assume token airdrops are only top-level transactions, which is currently true
                for (final var stateChange : remainingStateChanges) {
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasPendingAirdropIdKey()) {
                        final var pendingAirdropId =
                                stateChange.mapUpdateOrThrow().keyOrThrow().pendingAirdropIdKeyOrThrow();
                        final var pendingAirdropValue = stateChange
                                .mapUpdateOrThrow()
                                .valueOrThrow()
                                .accountPendingAirdropValueOrThrow()
                                .pendingAirdropValue();
                        final var pendingAirdropRecord =
                                new PendingAirdropRecord(pendingAirdropId, pendingAirdropValue);
                        if (baseTranslator.track(pendingAirdropRecord)) {
                            pendingAirdrops.add(pendingAirdropRecord);
                        }
                    }
                }
                // Pending airdrop ids are unique, so we don't need to worry about duplicates
                pendingAirdrops.sort(
                        comparing(PendingAirdropRecord::pendingAirdropIdOrThrow, PENDING_AIRDROP_ID_COMPARATOR));
                recordBuilder.newPendingAirdrops(pendingAirdrops);
            }
        });
    }
}
