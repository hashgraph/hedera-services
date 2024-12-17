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
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines a translator for a token wipe transaction into a {@link SingleTransactionRecord}.
 */
public class TokenWipeTranslator implements BlockTransactionPartsTranslator {
    private static final Logger log = LogManager.getLogger(TokenWipeTranslator.class);

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
                final var op = parts.body().tokenWipeOrThrow();
                final var tokenId = op.tokenOrThrow();
                final var wipedSerialNos = new ArrayList<>(Set.copyOf(op.serialNumbers()));
                final var numSerials = wipedSerialNos.size();
                if (numSerials > 0 && baseTranslator.tokenTypeOrThrow(tokenId) == NON_FUNGIBLE_UNIQUE) {
                    final var iter = remainingStateChanges.listIterator();
                    while (iter.hasNext()) {
                        final var stateChange = iter.next();
                        if (stateChange.hasMapDelete()
                                && stateChange.mapDeleteOrThrow().keyOrThrow().hasNftIdKey()) {
                            final var nftId =
                                    stateChange.mapDeleteOrThrow().keyOrThrow().nftIdKeyOrThrow();
                            if (!nftId.tokenIdOrThrow().equals(tokenId)) {
                                continue;
                            }
                            final var serialNo = nftId.serialNumber();
                            if (wipedSerialNos.remove(serialNo)) {
                                iter.remove();
                                if (wipedSerialNos.isEmpty()) {
                                    final var newTotalSupply = baseTranslator.newTotalSupply(tokenId, -numSerials);
                                    receiptBuilder.newTotalSupply(newTotalSupply);
                                    return;
                                }
                            }
                        }
                    }
                    log.error(
                            "Wiped serials {} did not have matching state changes for successful wipe {}",
                            wipedSerialNos,
                            parts.body());
                } else {
                    final var amountWiped = op.amount();
                    final var newTotalSupply = baseTranslator.newTotalSupply(tokenId, -amountWiped);
                    receiptBuilder.newTotalSupply(newTotalSupply);
                }
            }
        });
    }
}
