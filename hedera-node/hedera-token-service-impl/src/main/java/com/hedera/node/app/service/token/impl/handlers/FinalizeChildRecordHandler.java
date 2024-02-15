/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.RecordFinalizerBase;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.records.ChildFinalizeContext;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This is a special handler that is used to "finalize" hbar and token transfers for the child transaction record.
 */
@Singleton
public class FinalizeChildRecordHandler extends RecordFinalizerBase implements ChildRecordFinalizer {
    @Inject
    public FinalizeChildRecordHandler() {
        // For Dagger Injection
    }

    @Override
    public void finalizeChildRecord(@NonNull final ChildFinalizeContext context, final HederaFunctionality function) {
        final var recordBuilder = context.userTransactionRecordBuilder(CryptoTransferRecordBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the original value from writable state, and based on changes made during this
        // transaction via any relevant writable stores
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);
        final var readableTokenStore = context.readableStore(ReadableTokenStore.class);
        final var writableTokenStore = context.writableStore(WritableTokenStore.class);

        /* ------------------------- Hbar changes from child transaction  ------------------------- */
        final var hbarChanges = hbarChangesFrom(writableAccountStore);
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(TransferList.newBuilder()
                    .accountAmounts(asAccountAmounts(hbarChanges))
                    .build());
        } else if (recordBuilder.status() == ResponseCodeEnum.SUCCESS) {
            // set an empty transfer list even if there are no hbar changes but the parent record succeeded
            // to be compatible with mono-service
            recordBuilder.transferList(TransferList.DEFAULT);
        } else if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            // set null to transfer list if the child record failed to be compatible with mono-service
            recordBuilder.transferList(null);
        }

        // Declare the top-level token transfer list, which list will include BOTH fungible and non-fungible token
        // transfers
        final ArrayList<TokenTransferList> tokenTransferLists;

        // ---------- fungible token transfers -------------------------
        // If the function is not a crypto transfer, then we filter all zero amounts from token transfer list.
        // To be compatible with mono-service records, we _don't_ filter zero token transfers in the record
        final var isCryptoTransfer = function == HederaFunctionality.CRYPTO_TRANSFER;
        final var fungibleChanges = tokenRelChangesFrom(
                writableTokenRelStore, readableTokenStore, TokenType.FUNGIBLE_COMMON, !isCryptoTransfer);
        final var fungibleTokenTransferLists = asTokenTransferListFrom(fungibleChanges, !isCryptoTransfer);
        tokenTransferLists = new ArrayList<>(fungibleTokenTransferLists);

        // ---------- nft transfers -------------------------
        final var nftChanges = nftChangesFrom(writableNftStore, writableTokenStore);
        final var nftTokenTransferLists = asTokenTransferListFromNftChanges(nftChanges);
        tokenTransferLists.addAll(nftTokenTransferLists);

        // Record the modified fungible and non-fungible changes so records can be written
        if (!tokenTransferLists.isEmpty()) {
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }
}
