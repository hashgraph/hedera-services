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
import static com.hedera.node.app.service.token.AliasUtils.extractEvmAddress;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractDeleteTranslator implements BlockTransactionPartsTranslator {
    private static final Logger log = LogManager.getLogger(ContractDeleteTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder, involvedTokenId) -> {
            if (parts.status() == SUCCESS) {
                final var targetId =
                        parts.body().contractDeleteInstanceOrThrow().contractIDOrThrow();
                final var iter = remainingStateChanges.listIterator();
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasAccountIdKey()) {
                        final var account =
                                stateChange.mapUpdateOrThrow().valueOrThrow().accountValueOrThrow();
                        if (account.deleted()) {
                            var isTarget = false;
                            final long accountNum = account.accountIdOrThrow().accountNumOrThrow();
                            switch (targetId.contract().kind()) {
                                case UNSET -> throw new IllegalStateException("Contract kind should be set");
                                case CONTRACT_NUM -> isTarget = accountNum == targetId.contractNumOrThrow();
                                case EVM_ADDRESS -> isTarget =
                                        targetId.evmAddressOrThrow().equals(extractEvmAddress(account.alias()));
                            }
                            if (isTarget) {
                                receiptBuilder.contractID(ContractID.newBuilder()
                                        .contractNum(accountNum)
                                        .build());
                                iter.remove();
                                return;
                            }
                        }
                    }
                }
                log.error(
                        "No matching state change found for successful crypto delete with id {}",
                        parts.transactionIdOrThrow());
            }
        });
    }
}
