/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.token.impl.validators.AllowanceValidator.isValidOwner;
import static com.hedera.node.app.service.token.impl.validators.ApproveAllowanceValidator.getEffectiveOwner;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.NftRemoveAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.validators.DeleteAllowanceValidator;
import com.hedera.node.app.spi.workflows.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_DELETE_ALLOWANCE}. Crypto delete allowance is used to
 * delete an existing allowance for a given NFT serial number.
 */
@Singleton
public class CryptoDeleteAllowanceHandler implements TransactionHandler {
    private final DeleteAllowanceValidator deleteAllowanceValidator;

    @Inject
    public CryptoDeleteAllowanceHandler(@NonNull final DeleteAllowanceValidator validator) {
        this.deleteAllowanceValidator = validator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().cryptoDeleteAllowanceOrThrow();
        // Every owner whose allowances are being removed should sign, if the owner is not payer
        for (final var allowance : op.nftAllowancesOrElse(emptyList())) {
            context.requireKeyOrThrow(allowance.ownerOrElse(AccountID.DEFAULT), INVALID_ALLOWANCE_OWNER_ID);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var txn = context.body();
        final var payer = txn.transactionIDOrThrow().accountIDOrThrow();

        final var accountStore = context.writableStore(WritableAccountStore.class);
        // validate payer account exists
        final var payerAccount = accountStore.getAccountById(payer);
        validateTrue(payerAccount != null, INVALID_PAYER_ACCOUNT_ID);

        // validate the transaction body fields that include state or configuration
        // We can use payerAccount for validations since it's not mutated in validateSemantics
        validateSemantics(context, payerAccount, accountStore);

        // Apply all changes to the state modifications
        deleteAllowance(context, payerAccount, accountStore);
    }

    private void deleteAllowance(
            @NonNull final HandleContext context,
            @NonNull final Account payer,
            @NonNull final WritableAccountStore accountStore)
            throws HandleException {
        requireNonNull(context);
        requireNonNull(payer);
        requireNonNull(accountStore);

        final var op = context.body().cryptoDeleteAllowanceOrThrow();
        final var nftAllowances = op.nftAllowancesOrElse(emptyList());

        final var nftStore = context.writableStore(WritableNftStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);

        deleteNftSerials(nftAllowances, payer, accountStore, tokenStore, nftStore);
    }

    /**
     * Clear spender on the provided nft serials. If the owner is not provided in any allowance,
     * considers payer of the transaction as owner while checking if nft is owned by owner.
     *
     * @param nftAllowances given nftAllowances
     * @param payerAccount payer for the transaction
     */
    private void deleteNftSerials(
            final List<NftRemoveAllowance> nftAllowances,
            final Account payerAccount,
            final WritableAccountStore accountStore,
            final WritableTokenStore tokenStore,
            final WritableNftStore nftStore) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : nftAllowances) {
            final var serialNums = allowance.serialNumbers();
            final var tokenId = allowance.tokenIdOrElse(TokenID.DEFAULT);
            final var owner = getEffectiveOwner(allowance.owner(), payerAccount, accountStore);
            final var token = tokenStore.get(tokenId);
            for (final var serial : serialNums) {
                final var nft = nftStore.get(tokenId, serial);

                validateTrue(nft != null, INVALID_NFT_ID);
                validateTrue(isValidOwner(nft, owner.accountNumber(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);

                final var copy = nft.copyBuilder().spenderNumber(0L).build();
                nftStore.put(copy);
            }
        }
    }

    private void validateSemantics(
            @NonNull final HandleContext context, final Account payerAccount, final ReadableAccountStore accountStore) {
        requireNonNull(context);
        requireNonNull(payerAccount);
        requireNonNull(accountStore);

        final var op = context.body().cryptoDeleteAllowanceOrThrow();
        final var nftAllowances = op.nftAllowancesOrElse(emptyList());

        deleteAllowanceValidator.validate(context, nftAllowances, payerAccount, accountStore);
    }
}
