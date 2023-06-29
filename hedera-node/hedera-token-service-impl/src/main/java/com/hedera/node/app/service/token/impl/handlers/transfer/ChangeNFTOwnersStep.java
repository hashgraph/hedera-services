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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;

public class ChangeNFTOwnersStep extends BaseTokenHandler implements TransferStep {
    private final CryptoTransferTransactionBody op;

    public ChangeNFTOwnersStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var handleContext = transferContext.getHandleContext();
        final var nftStore = handleContext.writableStore(WritableNftStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var expiryValidator = handleContext.expiryValidator();

        for (var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.token();
            final var token = getIfUsable(tokenId, tokenStore);

            for (var oc : xfers.nftTransfers()) {
                final var senderId = oc.senderAccountID();
                final var receiverId = oc.receiverAccountID();
                final var serial = oc.serialNumber();

                final var senderAccount = getIfUsable(senderId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                final var receiverAccount = getIfUsable(receiverId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                final var senderRel = getIfUsable(senderId, tokenId, tokenRelStore);
                final var receiverRel = getIfUsable(receiverId, tokenId, tokenRelStore);

                validateFrozenAndKycOnRelation(senderRel);
                validateFrozenAndKycOnRelation(receiverRel);

                final var treasury = asAccount(token.treasuryAccountNumber());
                getIfUsable(treasury, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                final var nft = nftStore.get(tokenId, serial);
                validateTrue(nft != null, INVALID_NFT_ID);

                final var copyNft = nft.copyBuilder();
                if (nft.hasOwnerId() && nft.ownerId().equals(AccountID.DEFAULT)) {
                    copyNft.ownerId(treasury);
                }
                validateTrue(!nft.ownerId().equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                nftStore.put(copyNft.build());

                updateOwnership(
                        nft,
                        treasury,
                        senderAccount,
                        receiverAccount,
                        senderRel,
                        receiverRel,
                        accountStore,
                        tokenRelStore,
                        nftStore);
                if (oc.isApproval()) {
                    final var uniqueToken = nftStore.get(tokenId, serial);
                    final var copy = uniqueToken.copyBuilder();
                    nftStore.put(copy.spenderId(AccountID.DEFAULT).build());
                }
            }
        }
    }

    private void updateOwnership(
            final Nft nft,
            final AccountID treasuryId,
            final Account senderAccount,
            final Account receiverAccount,
            final TokenRelation senderRel,
            final TokenRelation receiverRel,
            final WritableAccountStore accountStore,
            final WritableTokenRelationStore tokenRelStore,
            final WritableNftStore nftStore) {
        final var nftCopy = nft.copyBuilder();
        final var fromNftsOwned = senderAccount.numberOwnedNfts();
        final var toNftsOwned = receiverAccount.numberOwnedNfts();
        final var fromTokenRelBalance = senderRel.balance();
        final var toTokenRelBalance = receiverRel.balance();
        final var fromNumPositiveBalances = senderAccount.numberPositiveBalances();
        final var toNumPositiveBalances = receiverAccount.numberPositiveBalances();
        final var isTreasuryReturn = treasuryId.equals(asAccount(receiverAccount.accountNumber()));
        if (isTreasuryReturn) {
            nftCopy.ownerId(AccountID.DEFAULT);
        } else {
            nftCopy.ownerId(asAccount(receiverAccount.accountNumber()));
        }

        final var updatedFromPositiveBalances =
                fromTokenRelBalance - 1 == 0 ? fromNumPositiveBalances - 1 : fromNumPositiveBalances;
        final var updatedToPositiveBalances =
                toTokenRelBalance == 0 ? toNumPositiveBalances + 1 : toNumPositiveBalances;

        final var senderAccountcopy = senderAccount.copyBuilder();
        final var receiverAccountcopy = receiverAccount.copyBuilder();
        final var senderRelCopy = senderRel.copyBuilder();
        final var receiverRelCopy = receiverRel.copyBuilder();

        accountStore.put(senderAccountcopy
                .numberOwnedNfts(fromNftsOwned - 1)
                .numberPositiveBalances(updatedFromPositiveBalances)
                .build());
        accountStore.put(receiverAccountcopy
                .numberOwnedNfts(toNftsOwned + 1)
                .numberPositiveBalances(updatedToPositiveBalances)
                .build());
        tokenRelStore.put(senderRelCopy.balance(fromTokenRelBalance - 1).build());
        tokenRelStore.put(receiverRelCopy.balance(toTokenRelBalance + 1).build());
        nftStore.put(nftCopy.build());
    }
}
