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

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import java.util.ArrayList;
import java.util.List;

public class ReplaceAliasesWithIDsInOp {
    public ReplaceAliasesWithIDsInOp() {}

    public CryptoTransferTransactionBody replaceAliasesWithIds(
            final CryptoTransferTransactionBody op, final TransferContextImpl transferContext) {
        final var resolutions = transferContext.resolutions();
        final var replacedAliasesOp = CryptoTransferTransactionBody.newBuilder();
        final var transferList = TransferList.newBuilder();
        final var tokenTransfersList = new ArrayList<TokenTransferList>();
        for (final var aa : op.transfers().accountAmountsOrElse(emptyList())) {
            if (isAlias(aa.accountIDOrThrow())) {
                final var resolvedId = resolutions.get(aa.accountID().alias());
                transferList.accountAmounts(
                        aa.copyBuilder().accountID(resolvedId).build());
            } else {
                transferList.accountAmounts(aa);
            }
        }
        replacedAliasesOp.transfers(transferList);

        for (final var adjust : op.tokenTransfersOrElse(emptyList())) {
            final var tokenTransferList = TokenTransferList.newBuilder();
            List<AccountAmount> replacedTokenAdjusts = new ArrayList<>();
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
            tokenTransferList.transfers(replacedTokenAdjusts);

            List<NftTransfer> replacedNftAdjusts = new ArrayList<>();
            for (final var nftAdjust : adjust.nftTransfersOrElse(emptyList())) {
                if (isAlias(nftAdjust.receiverAccountIDOrThrow()) || isAlias(nftAdjust.senderAccountIDOrThrow())) {
                    if (isAlias(nftAdjust.receiverAccountIDOrThrow())) {
                        final var resolvedId = resolutions.get(
                                nftAdjust.receiverAccountIDOrThrow().alias());
                        replacedNftAdjusts.add(nftAdjust
                                .copyBuilder()
                                .receiverAccountID(resolvedId)
                                .build());
                    }
                    if (isAlias(nftAdjust.senderAccountIDOrThrow())) {
                        final var resolvedId = resolutions.get(
                                nftAdjust.senderAccountIDOrThrow().alias());
                        replacedNftAdjusts.add(nftAdjust
                                .copyBuilder()
                                .senderAccountID(resolvedId)
                                .build());
                    }
                } else {
                    replacedNftAdjusts.add(nftAdjust);
                }
            }
            tokenTransferList.nftTransfers(replacedNftAdjusts);
            tokenTransfersList.add(tokenTransferList.build());
        }
        replacedAliasesOp.transfers(transferList);
        replacedAliasesOp.tokenTransfers(tokenTransfersList);
        return replacedAliasesOp.build();
    }
}
