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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep.isAlias;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces aliases with IDs in the crypto transfer operation. This is needed to make rest of the steps in the
 * transfer handler to process easily .
 */
public class ReplaceAliasesWithIDsInOp {
    /**
     * Replaces aliases with IDs in the crypto transfer operation.It looks at the resolutions happened in {@link EnsureAliasesStep}
     * which are stored in {@link TransferContextImpl} and replaces aliases with IDs.
     * @param op the crypto transfer operation
     * @param transferContext the transfer context
     * @return the crypto transfer operation with aliases replaced with IDs
     */
    public CryptoTransferTransactionBody replaceAliasesWithIds(
            @NonNull final CryptoTransferTransactionBody op, @NonNull final TransferContextImpl transferContext) {
        requireNonNull(op);
        requireNonNull(transferContext);

        final var resolutions = transferContext.resolutions();
        final var replacedAliasesOp = CryptoTransferTransactionBody.newBuilder();
        final var transferList = TransferList.newBuilder();
        final var tokenTransfersList = new ArrayList<TokenTransferList>();
        final var accountAmounts = new ArrayList<AccountAmount>();
        // replace all aliases in hbar transfers
        for (final var aa : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList())) {
            if (isAlias(aa.accountIDOrThrow())) {
                final var resolvedId = resolutions.get(aa.accountID().alias());
                accountAmounts.add(aa.copyBuilder().accountID(resolvedId).build());
            } else {
                accountAmounts.add(aa);
            }
        }
        transferList.accountAmounts(accountAmounts);
        replacedAliasesOp.transfers(transferList);

        // replace all aliases in token transfers
        for (final var adjust : op.tokenTransfersOrElse(emptyList())) {
            final var tokenTransferList = TokenTransferList.newBuilder().token(adjust.token());
            if (adjust.hasExpectedDecimals()) {
                tokenTransferList.expectedDecimals(adjust.expectedDecimals());
            }
            final List<AccountAmount> replacedTokenAdjusts = new ArrayList<>();
            for (final var tokenAdjust : adjust.transfersOrElse(emptyList())) {
                if (isAlias(tokenAdjust.accountIDOrThrow())) {
                    final var resolvedId =
                            resolutions.get(tokenAdjust.accountID().alias());
                    replacedTokenAdjusts.add(
                            tokenAdjust.copyBuilder().accountID(resolvedId).build());
                } else {
                    replacedTokenAdjusts.add(tokenAdjust);
                }
            }
            if (!replacedTokenAdjusts.isEmpty()) {
                tokenTransferList.transfers(replacedTokenAdjusts);
            }
            // replace aliases in nft adjusts
            final List<NftTransfer> replacedNftAdjusts = new ArrayList<>();
            for (final var nftAdjust : adjust.nftTransfersOrElse(emptyList())) {
                final var nftAdjustCopy = nftAdjust.copyBuilder();
                final var isReceiverAlias = isAlias(nftAdjust.receiverAccountIDOrThrow());
                final var isSenderAlias = isAlias(nftAdjust.senderAccountIDOrThrow());
                if (isReceiverAlias || isSenderAlias) {
                    if (isReceiverAlias) {
                        final var resolvedId = resolutions.get(
                                nftAdjust.receiverAccountIDOrThrow().alias());
                        nftAdjustCopy.receiverAccountID(resolvedId);
                    }
                    if (isSenderAlias) {
                        final var resolvedId = resolutions.get(
                                nftAdjust.senderAccountIDOrThrow().alias());
                        nftAdjustCopy.senderAccountID(resolvedId);
                    }
                    replacedNftAdjusts.add(nftAdjustCopy.build());
                } else {
                    replacedNftAdjusts.add(nftAdjust);
                }
            }
            // if there are any transfers or nft adjusts, add them to the token transfer list
            if (!replacedNftAdjusts.isEmpty()) {
                tokenTransferList.nftTransfers(replacedNftAdjusts);
            }
            tokenTransfersList.add(tokenTransferList.build());
        }
        replacedAliasesOp.tokenTransfers(tokenTransfersList);
        return replacedAliasesOp.build();
    }
}
