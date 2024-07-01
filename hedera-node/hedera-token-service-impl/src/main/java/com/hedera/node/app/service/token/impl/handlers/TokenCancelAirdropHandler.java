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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_REPEATED;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#TOKEN_CANCEL_AIRDROP}.
 * This transaction type is used to cancel an airdrop which is in pending state.
 */
@Singleton
public class TokenCancelAirdropHandler extends BaseTokenHandler implements TransactionHandler {

    @Inject
    public TokenCancelAirdropHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenCancelAirdropOrThrow();
        final var allPendingAirdrops = op.pendingAirdrops();
        final var accountStore = context.createStore(ReadableAccountStore.class);

        for (final var airdrop : allPendingAirdrops) {
            validateTrue(airdrop.hasSenderId(), INVALID_ACCOUNT_ID);
            verifyAndRequireKeyForSender(airdrop.senderIdOrThrow(), context, accountStore);
        }
    }

    /**
     * Verifies that the sender account exists and ensures the account's key is valid and required for the transaction.
     *
     * @param senderId The AccountID of the specified sender whose key needs to be validated.
     * @param context The PreHandleContext providing transaction context.
     * @param accountStore The store to access readable account information.
     * @throws PreCheckException If the sender's account is immutable or the sender's account ID is invalid.
     */
    private void verifyAndRequireKeyForSender(
            @NonNull final AccountID senderId,
            @NonNull final PreHandleContext context,
            @NonNull final ReadableAccountStore accountStore)
            throws PreCheckException {
        final var senderAccount = accountStore.getAliasedAccountById(senderId);
        validateTruePreCheck(senderAccount != null, INVALID_ACCOUNT_ID);

        // If the sender account is immutable, then we throw an exception.
        final var key = senderAccount.key();
        if (key == null || !isValid(key)) {
            throw new PreCheckException(ACCOUNT_IS_IMMUTABLE);
        }
        context.requireKey(key);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn, "Transaction body cannot be null");
        final var op = txn.tokenCancelAirdropOrThrow();

        validateFalsePreCheck(op.pendingAirdrops().isEmpty(), EMPTY_PENDING_AIRDROP_ID_LIST);

        final var uniqueTokenReferences = new HashSet<PendingAirdropId>();
        for (final var airdrop : op.pendingAirdrops()) {
            if (!uniqueTokenReferences.add(airdrop)) {
                throw new PreCheckException(PENDING_AIRDROP_ID_REPEATED);
            }
            validateAccountID(airdrop.receiverId(), null);
            validateAccountID(airdrop.senderId(), null);

            if (airdrop.hasFungibleTokenType()) {
                final var tokenID = airdrop.fungibleTokenType();
                validateTruePreCheck(tokenID != null && !tokenID.equals(TokenID.DEFAULT), INVALID_TOKEN_ID);
            }
            if (airdrop.hasNonFungibleToken()) {
                final var nftID = airdrop.nonFungibleTokenOrThrow();
                validateTruePreCheck(nftID.tokenId() != null, INVALID_NFT_ID);
                validateTruePreCheck(nftID.serialNumber() > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
            }
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.cancelTokenAirdropEnabled(), NOT_SUPPORTED);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        return Fees.FREE;
    }
}
