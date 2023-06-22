/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

public class EnsureAliasesStep implements TransferStep {
    final CryptoTransferTransactionBody op;
    public EnsureAliasesStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }
    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return Set.of();
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        resolveHbarAdjusts(hbarTransfers, transferContext);
        resolveTokenAdjusts(tokenTransfers, transferContext);
    }

    private void resolveTokenAdjusts(final List<TokenTransferList> tokenTransfers,
                                     final TransferContext transferContext) {
        for(final var tt : tokenTransfers) {
            for( final var adjust : tt.transfersOrElse(emptyList())) {
                final var accountId = adjust.accountIDOrThrow();
                if(isAlias(accountId)) {
                    if(adjust.amount() < 0){
                        transferContext.getOrCreateFromAlias(accountId);
                    } else {
                        transferContext.getFromAlias(accountId);
                    }
                }
            }

            for(final var nftAdjust : tt.nftTransfersOrElse(emptyList())) {
                final var receiverId = nftAdjust.receiverAccountIDOrThrow();
                final var senderId = nftAdjust.senderAccountIDOrThrow();
                // sender can't be a missing accountId. It will fail if the alias doesn't exist
                if(isAlias(senderId)) {
                    transferContext.getFromAlias(senderId);
                }
                // receiver can be a missing accountId. It will be created if it doesn't exist
                if(isAlias(receiverId)) {
                    transferContext.getOrCreateFromAlias(receiverId);
                }
            }
        }
    }

    private void resolveHbarAdjusts(final List<AccountAmount> hbarTransfers,
                                    final TransferContext transferContext) {
        for(final var aa : hbarTransfers) {
            final var accountId = aa.accountIDOrThrow();
            if(isAlias(accountId)) {
                if(aa.amount() < 0){
                    transferContext.getOrCreateFromAlias(accountId);
                } else {
                    transferContext.getFromAlias(accountId);
                }
            }
        }
    }

    private AccountID resolveHbarAdjusts(){

    }

    public static boolean isAlias(AccountID accountID){
        return accountID.hasAlias() && accountID.accountNum() != 0L;
    }
}
