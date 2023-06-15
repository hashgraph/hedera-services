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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.isKeyRemoval;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateValidator;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_UPDATE}.
 *
 * <p><b>NOTE:</b> this class intentionally changes the following error response codes relative to
 * SigRequirements:
 *
 * <ol>
 *   <li>When a missing account is used as a token treasuryNum, fails with {@code INVALID_ACCOUNT_ID}
 *       rather than {@code ACCOUNT_ID_DOES_NOT_EXIST}.
 * </ol>
 *
 * * EET expectations may need to be updated accordingly
 */
@Singleton
public class TokenUpdateHandler extends BaseTokenHandler implements TransactionHandler {
    private final TokenUpdateValidator tokenUpdateValidator;

    @Inject
    public TokenUpdateHandler(@NonNull final TokenUpdateValidator tokenUpdateValidator) {
        this.tokenUpdateValidator = tokenUpdateValidator;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenUpdateOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenUpdateOrThrow();
        pureChecks(context.body());

        final var tokenId = op.tokenOrThrow();

        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMetadata = tokenStore.getTokenMeta(tokenId);
        if (tokenMetadata == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (tokenMetadata.hasAdminKey()) {
            context.requireKey(tokenMetadata.adminKey());
        }
        if (op.hasAutoRenewAccount()) {
            context.requireKeyOrThrow(op.autoRenewAccountOrThrow(), INVALID_AUTORENEW_ACCOUNT);
        }
        if (op.hasTreasury()) {
            context.requireKeyOrThrow(op.treasuryOrThrow(), INVALID_ACCOUNT_ID);
        }
        if (op.hasAdminKey()) {
            context.requireKey(op.adminKeyOrThrow());
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenUpdateOrThrow();

        final var validationResult = tokenUpdateValidator.validateSemantics(context, op);
        final var token = validationResult.token();
        final var resolvedExpiry = validationResult.resolvedExpiryMeta();

        final var tokenId = op.token();

        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        final var existingTreasury = asAccount(token.treasuryAccountNumber());

        if (op.hasTreasury()) {
            final var newTreasury = op.treasuryOrThrow();
            final var newTreasuryAccount = getIfUsable(
                    newTreasury, accountStore, context.expiryValidator(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
            final var newTreasuryRel = tokenRelStore.get(newTreasury, tokenId);
            // If there is no treasury relationship, then we need to create one if auto associations are available.
            // If not fail
            if (newTreasuryRel == null) {
                autoAssociate(newTreasuryAccount, token, accountStore, tokenRelStore, context);
            }
            // Treasury can be modified when it owns NFTs when the property "tokens.nfts.useTreasuryWildcards"
            // is enabled.
            if (!tokensConfig.nftsUseTreasuryWildcards() && token.tokenType().equals(NON_FUNGIBLE_UNIQUE)) {
                final var existingTreasuryRel = tokenRelStore.get(existingTreasury, tokenId);
                final var tokenRelBalance = existingTreasuryRel.balance();
                validateTrue(tokenRelBalance == 0, CURRENT_TREASURY_STILL_OWNS_NFTS);
            }

            if (!newTreasury.equals(existingTreasury)) {
                // FUTURE : Not sure why we are checking existing treasury account here
                final var existingTreasuryAccount = accountStore.getAccountById(existingTreasury);
                final var isDetached = context.expiryValidator()
                        .isDetached(
                                EntityType.ACCOUNT,
                                existingTreasuryAccount.expiredAndPendingRemoval(),
                                existingTreasuryAccount.tinybarBalance());
                validateFalse(isDetached, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
                updateTreasuryTitles(existingTreasuryAccount, newTreasuryAccount, token, accountStore, tokenRelStore);
            }
            tokenUpdateValidator.validateNftBalances(token, tokenRelStore);
            transferTokensToNewTreasury(existingTreasury, newTreasury, token, tokenRelStore, accountStore, context);
        }

        final var tokenBuilder = customizeToken(token, resolvedExpiry, op, context);
        tokenStore.put(tokenBuilder.build());
    }

    private void transferTokensToNewTreasury(
            final AccountID oldTreasury,
            final AccountID newTreasury,
            final Token token,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore,
            final HandleContext context) {
        final var tokenId = asToken(token.tokenNumber());
        final var oldTreasuryRel = getIfUsable(oldTreasury, tokenId, tokenRelStore);
        final var newTreasuryRel = getIfUsable(newTreasury, tokenId, tokenRelStore);
        if (oldTreasuryRel.balance() > 0) {
            validateFrozenAndKey(oldTreasuryRel);
            validateFrozenAndKey(newTreasuryRel);

            if (token.tokenType().equals(FUNGIBLE_COMMON)) {
                transferFungibleTokens(oldTreasuryRel, newTreasuryRel, tokenRelStore, accountStore);
            } else {
                changeOwner(oldTreasuryRel, newTreasuryRel, tokenRelStore, accountStore);
            }
        }
    }

    private void changeOwner(
            final TokenRelation fromTreasuryRel,
            final TokenRelation toTreasuryRel,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore) {
        final var fromTreasury = accountStore.getAccountById(asAccount(fromTreasuryRel.accountNumber()));
        final var toTreasury = accountStore.getAccountById(asAccount(toTreasuryRel.accountNumber()));

        final var fromRelBalance = fromTreasuryRel.balance();
        final var toRelBalance = toTreasuryRel.balance();

        final var fromNftsOwned = fromTreasury.numberOwnedNfts();
        final var toNftsWOwned = toTreasury.numberOwnedNfts();

        final var fromTreasuryCopy = fromTreasury.copyBuilder();
        final var toTreasuryCopy = toTreasury.copyBuilder();
        final var fromRelCopy = fromTreasuryRel.copyBuilder();
        final var toRelCopy = toTreasuryRel.copyBuilder();

        accountStore.put(
                fromTreasuryCopy.numberOwnedNfts(fromNftsOwned - fromRelBalance).build());
        accountStore.put(
                toTreasuryCopy.numberOwnedNfts(toNftsWOwned + toRelBalance).build());
        tokenRelStore.put(fromRelCopy.balance(0).build());
        tokenRelStore.put(toRelCopy.balance(toRelBalance + fromRelBalance).build());
        // TODO : Need to build record transfer list for this case. Not needed for this PR.
        // Need to do in finalize
    }

    private void validateFrozenAndKey(final TokenRelation fromTreasuryRel) {
        validateTrue(!fromTreasuryRel.frozen(), ACCOUNT_FROZEN_FOR_TOKEN);
        validateTrue(fromTreasuryRel.kycGranted(), TOKEN_HAS_NO_KYC_KEY);
    }

    private void transferFungibleTokens(
            final TokenRelation fromTreasuryRel,
            final TokenRelation toTreasuryRel,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore) {
        final var adjustment = fromTreasuryRel.balance();

        final var fromTreasury = accountStore.getAccountById(asAccount(fromTreasuryRel.accountNumber()));
        final var toTreasury = accountStore.getAccountById(asAccount(toTreasuryRel.accountNumber()));

        adjustBalance(fromTreasuryRel, fromTreasury, -adjustment, tokenRelStore, accountStore);
        adjustBalance(toTreasuryRel, toTreasury, adjustment, tokenRelStore, accountStore);
        // TODO: If any of the above fail, need to rollback only token transfer balances for record.
        // Not sure how it will be done yet
    }

    private void adjustBalance(
            final TokenRelation tokenRel,
            final Account account,
            final long adjustment,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore) {
        final var originalBalance = tokenRel.balance();
        final var newBalance = originalBalance + adjustment;
        validateTrue(newBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);

        final var copyRel = tokenRel.copyBuilder();
        tokenRelStore.put(copyRel.balance(newBalance).build());

        var numPositiveBalances = account.numberPositiveBalances();
        // If the original balance is zero, then the receiving account's numPositiveBalances has to
        // be increased
        // and if the newBalance is zero, then the sending account's numPositiveBalances has to be
        // decreased
        if (newBalance == 0 && adjustment < 0) {
            numPositiveBalances--;
        } else if (originalBalance == 0 && adjustment > 0) {
            numPositiveBalances++;
        }
        final var copyAccount = account.copyBuilder();
        accountStore.put(copyAccount.numberPositiveBalances(numPositiveBalances).build());
        // TODO: Need to track units change in record in finalize method for this
    }

    private Token.Builder customizeToken(
            @NonNull final Token token,
            @NonNull final ExpiryMeta resolvedExpiry,
            @NonNull final TokenUpdateTransactionBody op,
            @NonNull final HandleContext context) {
        final var copyToken = token.copyBuilder();
        // All these keys are validated in validateSemantics
        // If these keys did not exist on the token already, they can't be changed on update
        updateKeys(op, token, copyToken);
        updateExpiryFields(op, resolvedExpiry, copyToken);
        updateNameSymbolMemoAndTreasury(op, copyToken, token);
        return copyToken;
    }

    private void updateNameSymbolMemoAndTreasury(
            final TokenUpdateTransactionBody op, final Token.Builder copyToken, final Token originalToken) {
        if (op.symbol().length() > 0) {
            copyToken.symbol(op.symbol());
        }
        if (op.name().length() > 0) {
            copyToken.name(op.name());
        }
        if (op.hasMemo() && op.memo().length() > 0) {
            copyToken.memo(op.memo());
        }
        if (op.hasTreasury() && op.treasuryOrThrow().accountNum() != originalToken.treasuryAccountNumber()) {
            copyToken.treasuryAccountNumber(op.treasuryOrThrow().accountNum());
        }
    }

    private void updateExpiryFields(
            final TokenUpdateTransactionBody op, final ExpiryMeta resolvedExpiry, final Token.Builder copyToken) {
        if (op.hasExpiry()) {
            copyToken.expiry(resolvedExpiry.expiry());
        }
        if (op.hasAutoRenewPeriod()) {
            copyToken.autoRenewSecs(resolvedExpiry.autoRenewPeriod());
        }
        if (op.hasAutoRenewAccount()) {
            copyToken.autoRenewAccountNumber(resolvedExpiry.autoRenewNum());
        }
    }

    private void updateKeys(final TokenUpdateTransactionBody op, final Token token, final Token.Builder copyToken) {
        if (op.hasKycKey()) {
            validateTrue(token.hasKycKey(), TOKEN_HAS_NO_KYC_KEY);
            copyToken.kycKey(op.kycKey());
        }
        if (op.hasFreezeKey()) {
            validateTrue(token.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);
            copyToken.freezeKey(op.freezeKey());
        }
        if (op.hasWipeKey()) {
            validateTrue(token.hasWipeKey(), TOKEN_HAS_NO_WIPE_KEY);
            copyToken.wipeKey(op.wipeKey());
        }
        if (op.hasSupplyKey()) {
            validateTrue(token.hasSupplyKey(), TOKEN_HAS_NO_SUPPLY_KEY);
            copyToken.supplyKey(op.supplyKey());
        }
        if (op.hasFeeScheduleKey()) {
            validateTrue(token.hasFeeScheduleKey(), TOKEN_HAS_NO_FEE_SCHEDULE_KEY);
            copyToken.feeScheduleKey(op.feeScheduleKey());
        }
        if (op.hasPauseKey()) {
            validateTrue(token.hasPauseKey(), TOKEN_HAS_NO_PAUSE_KEY);
            copyToken.pauseKey(op.pauseKey());
        }
        if (!isExpiryOnlyUpdateOp(op)) {
            validateTrue(token.hasAdminKey(), TOKEN_IS_IMMUTABLE);
        }
        if (op.hasAdminKey()) {
            final var newAdminKey = op.adminKey();
            if (isKeyRemoval(newAdminKey)) {
                copyToken.adminKey((Key) null);
            } else {
                copyToken.adminKey(newAdminKey);
            }
        }
    }

    private void updateTreasuryTitles(
            @NonNull final Account existingTreasuryAccount,
            @NonNull final Account newTreasuryAccount,
            @NonNull final Token token,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        final var newTokenRelation =
                tokenRelStore.get(asAccount(newTreasuryAccount.accountNumber()), asToken(token.tokenNumber()));
        final var newRelCopy = newTokenRelation.copyBuilder();

        if (token.hasFreezeKey()) {
            newRelCopy.frozen(false);
        }
        if (token.hasKycKey()) {
            newRelCopy.kycGranted(true);
        }

        final var existingTreasuryTitles = existingTreasuryAccount.numberTreasuryTitles();
        final var newTreasuryAccountTitles = newTreasuryAccount.numberTreasuryTitles();
        final var copyOldTreasury =
                existingTreasuryAccount.copyBuilder().numberTreasuryTitles(existingTreasuryTitles - 1);
        final var copyNewTreasury = newTreasuryAccount.copyBuilder().numberTreasuryTitles(newTreasuryAccountTitles + 1);

        accountStore.put(copyOldTreasury.build());
        accountStore.put(copyNewTreasury.build());
        tokenRelStore.put(newRelCopy.build());
    }
}
