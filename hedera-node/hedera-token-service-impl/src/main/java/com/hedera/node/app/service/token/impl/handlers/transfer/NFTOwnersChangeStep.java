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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

public class NFTOwnersChangeStep extends BaseTokenHandler implements TransferStep {
    private final CryptoTransferTransactionBody op;
    private final AccountID topLevelPayer;

    public NFTOwnersChangeStep(final CryptoTransferTransactionBody op, final AccountID topLevelPayer) {
        this.op = op;
        this.topLevelPayer = topLevelPayer;
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
            // Expected decimals are already validated in AdjustFungibleTokenChangesStep.
            // So not doing same check again here

            for (final var nftTransfer : xfers.nftTransfersOrElse(emptyList())) {
                final var senderId = nftTransfer.senderAccountID();
                final var receiverId = nftTransfer.receiverAccountID();
                final var serial = nftTransfer.serialNumber();

                final var senderAccount = getIfUsable(senderId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                final var receiverAccount = getIfUsable(receiverId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                final var senderRel = getIfUsable(senderId, tokenId, tokenRelStore);
                final var receiverRel = getIfUsable(receiverId, tokenId, tokenRelStore);

                validateNotFrozenAndKycOnRelation(senderRel);
                validateNotFrozenAndKycOnRelation(receiverRel);

                final var treasury = token.treasuryAccountId();
                getIfUsable(treasury, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
                final var nft = nftStore.get(tokenId, serial);
                validateTrue(nft != null, INVALID_NFT_ID);

                if (nftTransfer.isApproval()) {
                    // If isApproval flag is set then the spender account must have paid for the transaction.
                    // The transfer list specifies the owner who granted allowance as sender
                    // check if the allowances from the sender account has the payer account as spender
                    validateSpenderHasAllowance(senderAccount, topLevelPayer, tokenId, nft);
                }

                // owner of nft should match the sender in transfer list
                if (nft.hasOwnerId()) {
                    validateTrue(nft.ownerId().equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                } else {
                    validateTrue(treasury.equals(senderId), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                }

                // Update the ownership of the nft
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
            }
        }
    }

    /**
     * Validate if the spender has allowance to transfer the nft, if the nft is being transferred
     * with an isApproval flag set to true.
     *
     * @param owner owner of the nft
     * @param spender spender of the nft
     * @param tokenId token id of the nft
     * @param nft nft to be transferred
     */
    static void validateSpenderHasAllowance(
            final Account owner, final AccountID spender, final TokenID tokenId, final Nft nft) {
        final var approveForAllAllowances = owner.approveForAllNftAllowancesOrElse(emptyList());
        final var allowance = AccountApprovalForAllAllowance.newBuilder()
                .spenderId(spender)
                .tokenId(tokenId)
                .build();
        if (!approveForAllAllowances.contains(allowance)) {
            final var approvedSpender = nft.spenderId();
            validateTrue(approvedSpender != null && approvedSpender.equals(spender), SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        }
    }

    /**
     * Update the ownership of the nft. It updates the owner of NFT to {@link AccountID#DEFAULT}
     * if it is being transferred back to treasury account. It also updates the number of NFTs
     * owned and number of positive balances by the sender and receiver accounts.
     * Also updates the token relations balance for both sender and receiver accounts.
     * It also wipes the spender on the nft if the nft is being transferred, since
     * owner no more owns the NFT.
     *
     * @param nft - NFT to be transferred
     * @param treasuryId - Treasury account of the token
     * @param senderAccount - Sender account
     * @param receiverAccount - Receiver account
     * @param senderRel - Token relation of sender account
     * @param receiverRel - Token relation of receiver account
     * @param accountStore - Account store
     * @param tokenRelStore - Token relation store
     * @param nftStore - NFT store
     */
    private void updateOwnership(
            @NonNull final Nft nft,
            @NonNull final AccountID treasuryId,
            @NonNull final Account senderAccount,
            @NonNull final Account receiverAccount,
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
        final var isTreasuryReturn = treasuryId.equals(receiverAccount.accountId());

        // If the token is being returned back to treasury null out the owner
        if (isTreasuryReturn) {
            nftCopy.ownerId((AccountID) null);
        } else {
            nftCopy.ownerId(receiverAccount.accountId());
        }
        // wipe the spender on this NFT
        nftCopy.spenderId((AccountID) null);

        // adjust number of positive balances
        final var updatedFromPositiveBalances =
                fromTokenRelBalance - 1 == 0 ? fromNumPositiveBalances - 1 : fromNumPositiveBalances;
        final var updatedToPositiveBalances =
                toTokenRelBalance == 0 ? toNumPositiveBalances + 1 : toNumPositiveBalances;

        // Make copies of the objects to be updated
        final var senderAccountCopy = senderAccount.copyBuilder();
        final var receiverAccountCopy = receiverAccount.copyBuilder();
        final var senderRelCopy = senderRel.copyBuilder();
        final var receiverRelCopy = receiverRel.copyBuilder();

        accountStore.put(senderAccountCopy
                .numberOwnedNfts(fromNftsOwned - 1)
                .numberPositiveBalances(updatedFromPositiveBalances)
                .build());
        accountStore.put(receiverAccountCopy
                .numberOwnedNfts(toNftsOwned + 1)
                .numberPositiveBalances(updatedToPositiveBalances)
                .build());
        tokenRelStore.put(senderRelCopy.balance(fromTokenRelBalance - 1).build());
        tokenRelStore.put(receiverRelCopy.balance(toTokenRelBalance + 1).build());
        nftStore.put(nftCopy.build());
        // TODO: make sure finalize is capturing all these in transfer list
    }
}
