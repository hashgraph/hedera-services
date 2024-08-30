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

package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Translates a token mint transaction into a {@link SingleTransactionRecord}.
 */
public class TokenMintTranslator implements BlockTransactionPartsTranslator {
    private static final Logger log = LogManager.getLogger(TokenMintTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder, sidecarRecords, involvedTokenId) -> {
            if (parts.status() == SUCCESS) {
                final var op = parts.body().tokenMintOrThrow();
                final var tokenId = op.tokenOrThrow();
                final var numMints = op.metadata().size();
                if (numMints > 0) {
                    final var mintedSerialNos = baseTranslator.nextNMints(tokenId, numMints);
                    receiptBuilder.serialNumbers(List.copyOf(mintedSerialNos));
                    final var iter = remainingStateChanges.listIterator();
                    while (iter.hasNext()) {
                        final var stateChange = iter.next();
                        if (stateChange.hasMapUpdate()
                                && stateChange.mapUpdateOrThrow().keyOrThrow().hasNftIdKey()) {
                            final var nftId =
                                    stateChange.mapUpdateOrThrow().keyOrThrow().nftIdKeyOrThrow();
                            if (!nftId.tokenIdOrThrow().equals(tokenId)) {
                                continue;
                            }
                            final var serialNo = nftId.serialNumber();
                            if (mintedSerialNos.remove(serialNo)) {
                                iter.remove();
                                if (mintedSerialNos.isEmpty()) {
                                    final var newTotalSupply = baseTranslator.newTotalSupply(tokenId, numMints);
                                    receiptBuilder.newTotalSupply(newTotalSupply);
                                    return;
                                }
                            }
                        }
                    }
                    log.error(
                            "Not all mints had matching state changes found for successful mint with id {}",
                            parts.transactionIdOrThrow());
                } else {
                    final var amountMinted = op.amount();
                    final var newTotalSupply = baseTranslator.newTotalSupply(tokenId, amountMinted);
                    receiptBuilder.newTotalSupply(newTotalSupply);
                }
            }
        });
    }
}
