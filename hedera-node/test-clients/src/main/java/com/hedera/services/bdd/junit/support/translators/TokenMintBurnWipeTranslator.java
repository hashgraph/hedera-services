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

package com.hedera.services.bdd.junit.support.translators;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Note: this class is used for token mint, burn, and wipe translations
 */
class TokenMintBurnWipeTranslator implements TransactionRecordTranslator<SingleTransactionBlockItems> {

    @Override
    public SingleTransactionRecord translate(
            @NonNull final SingleTransactionBlockItems transaction, @Nullable final StateChanges stateChanges) {
        final var receiptBuilder = TransactionReceipt.newBuilder();
        final var recordBuilder = TransactionRecord.newBuilder();

        if (stateChanges != null) {
            maybeAssignNewSupplyAndSerialNums(stateChanges, receiptBuilder);
        }

        return new SingleTransactionRecord(
                transaction.txn(),
                recordBuilder.receipt(receiptBuilder.build()).build(),
                List.of(),
                new SingleTransactionRecord.TransactionOutputs(null));
    }

    private void maybeAssignNewSupplyAndSerialNums(
            final StateChanges stateChanges, final TransactionReceipt.Builder receiptBuilder) {
        final var updatedToken = stateChanges.stateChanges().stream()
                .filter(StateChange::hasMapUpdate)
                .filter(stateChange -> stateChange.mapUpdate().hasValue()
                        && stateChange.mapUpdate().value().hasTokenValue())
                .findFirst()
                .map(stateChange -> stateChange.mapUpdate().value().tokenValue())
                .orElse(null);

        if (updatedToken != null) {
            receiptBuilder.newTotalSupply(updatedToken.totalSupply());

            if (updatedToken.tokenType() == TokenType.NON_FUNGIBLE_UNIQUE) {
                final var serialNums = new ArrayList<Long>();
                stateChanges.stateChanges().stream()
                        .filter(StateChange::hasMapUpdate)
                        .filter(stateChange -> stateChange.mapUpdate().hasKey()
                                && stateChange.mapUpdate().key().hasNftIdKey())
                        .map(stateChange -> stateChange.mapUpdate().key().nftIdKey())
                        .forEach(nftId -> serialNums.add(nftId.serialNumber()));

                receiptBuilder.serialNumbers(serialNums);
            }
        }
    }
}
