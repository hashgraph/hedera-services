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
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.blocks.translators.BaseTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionParts;
import com.hedera.node.app.blocks.translators.BlockTransactionPartsTranslator;
import com.hedera.node.app.state.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public class CryptoUpdateTranslator implements BlockTransactionPartsTranslator {
    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> {
            if (parts.status() == SUCCESS) {
                final var op = parts.body().cryptoUpdateAccountOrThrow();
                final var targetId = op.accountIDToUpdateOrThrow();
                final var iter = remainingStateChanges.listIterator();
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasAccountIdKey()) {
                        final var account =
                                stateChange.mapUpdateOrThrow().valueOrThrow().accountValueOrThrow();
                        if (matches(targetId, account)) {
                            iter.remove();
                            receiptBuilder.accountID(account.accountIdOrThrow());
                            return;
                        }
                    }
                }
            }
        });
    }

    private boolean matches(@NonNull final AccountID accountId, @NonNull final Account account) {
        return switch (accountId.account().kind()) {
            case UNSET -> throw new IllegalStateException("Account ID has no kind");
            case ACCOUNT_NUM -> account.accountIdOrThrow().equals(accountId);
            case ALIAS -> account.alias().equals(accountId.aliasOrThrow());
        };
    }
}
