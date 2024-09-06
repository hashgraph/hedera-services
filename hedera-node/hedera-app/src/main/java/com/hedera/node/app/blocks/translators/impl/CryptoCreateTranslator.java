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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.blocks.translators.BaseTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionParts;
import com.hedera.node.app.blocks.translators.BlockTransactionPartsTranslator;
import com.hedera.node.app.service.token.AliasUtils;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.types.EntityType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Translates a crypto create transaction into a {@link SingleTransactionRecord}.
 */
public class CryptoCreateTranslator implements BlockTransactionPartsTranslator {
    private static final Logger log = LogManager.getLogger(CryptoCreateTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            if (parts.status() == ResponseCodeEnum.SUCCESS) {
                final var createdNum = baseTranslator.nextCreatedNum(EntityType.ACCOUNT);
                final var iter = remainingStateChanges.listIterator();
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasAccountIdKey()) {
                        final var accountId =
                                stateChange.mapUpdateOrThrow().keyOrThrow().accountIdKeyOrThrow();
                        if (accountId.accountNumOrThrow() == createdNum) {
                            final var account = stateChange
                                    .mapUpdateOrThrow()
                                    .valueOrThrow()
                                    .accountValueOrThrow();
                            receiptBuilder.accountID(accountId);
                            if (!AliasUtils.isOfEvmAddressSize(account.alias())) {
                                final var maybeEvmAddress = AliasUtils.extractEvmAddress(account.alias());
                                if (maybeEvmAddress != null) {
                                    recordBuilder.evmAddress(maybeEvmAddress);
                                }
                            }
                            iter.remove();
                            return;
                        }
                    }
                }
                log.error(
                        "No matching state change found for successful crypto create with id {}",
                        parts.transactionIdOrThrow());
            }
        });
    }
}
