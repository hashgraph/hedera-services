/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;

import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.node.app.service.token.impl.RecordFinalizer;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This is a special handler that is used to "finalize" hbar and token transfers for the child transaction record.
 * Finalization in this context means summing the net changes to make to each account's hbar balance and token
 * balances, and assigning the final owner of an nft after an arbitrary number of ownership changes.
 * Based on issue https://github.com/hashgraph/hedera-services/issues/7084 the modularized
 * transaction record for NFT transfer chain A -> B -> C, will look different from mono-service record.
 * This is because mono-service will record both ownership changes from A -> b and then B-> C.
 * NOTE: This record doesn't calculate any staking rewards. Staking rewards are calculated only for parent transaction record.
 *
 * In this finalizer, we will:
 * 1. Iterate through all modifications in writableAccountStore, writableTokenRelationStore.
 * 2. For each modification we look at the same entity's original value
 * 3. Calculate the difference between the two, and then construct a TransferList and TokenTransferList
 * for the child record
 */
@Singleton
public class FinalizeChildRecordHandler extends RecordFinalizer implements ChildRecordFinalizer {

    @Inject
    public FinalizeChildRecordHandler() {}

    @Override
    public void finalizeChildRecord(@NonNull final HandleContext context) {
        final var recordBuilder = context.recordBuilder(CryptoTransferRecordBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the original value from writable state, and based on changes made during this
        // transaction via any relevant writable stores
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);

        /* ------------------------- Hbar changes from child transaction  ------------------------- */
        final var hbarChanges = hbarChangesFrom(writableAccountStore);
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(
                    TransferList.newBuilder().accountAmounts(hbarChanges).build());
        }

        // Declare the top-level token transfer list, which list will include BOTH fungible and non-fungible token
        // transfers
        final ArrayList<TokenTransferList> tokenTransferLists;

        // ---------- fungible token transfers -------------------------
        final var fungibleChanges = fungibleChangesFrom(writableTokenRelStore);
        final var fungibleTokenTransferLists = asTokenTransferListFrom(fungibleChanges);
        tokenTransferLists = new ArrayList<>(fungibleTokenTransferLists);

        // ---------- nft transfers -------------------------
        final var nftTokenTransferLists = nftChangesFrom(writableNftStore);
        tokenTransferLists.addAll(nftTokenTransferLists);

        // Record the modified fungible and non-fungible changes so records can be written
        if (!tokenTransferLists.isEmpty()) {
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }
}
