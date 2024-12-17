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
import java.util.List;
import java.util.Set;

public class TokenBurnTranslator implements BlockTransactionPartsTranslator {
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
                final var op = parts.body().tokenBurnOrThrow();
                final var tokenId = op.tokenOrThrow();
                final var serialsBurned = Set.copyOf(op.serialNumbers());
                final var numSerialsBurned = serialsBurned.size();
                final long newTotalSupply;
                if (numSerialsBurned > 0) {
                    newTotalSupply = baseTranslator.tokenTypeOrThrow(tokenId) == NON_FUNGIBLE_UNIQUE
                            ? baseTranslator.newTotalSupply(tokenId, -numSerialsBurned)
                            : baseTranslator.newTotalSupply(tokenId, 0);
                } else {
                    final var amountBurned = op.amount();
                    newTotalSupply = baseTranslator.newTotalSupply(tokenId, -amountBurned);
                }
                receiptBuilder.newTotalSupply(newTotalSupply);
            }
        });
    }
}
