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
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenCreateValidator;
import com.hedera.node.app.service.token.impl.validators.TokenTypeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CREATE}.
 */
@Singleton
public class TokenCreateHandler extends BaseTokenHandler implements TransactionHandler {
    private final CustomFeesValidator customFeesValidator;
    private final TokenTypeValidator tokenTypeValidator;
    private final TokenCreateValidator tokenCreateValidator;
    @Inject
    public TokenCreateHandler(@NonNull CustomFeesValidator customFeesValidator,
                              @NonNull TokenTypeValidator tokenTypeValidator,
                              @NonNull TokenCreateValidator tokenCreateValidator) {
        this.customFeesValidator = customFeesValidator;
        this.tokenTypeValidator = tokenTypeValidator;
        this.tokenCreateValidator = tokenCreateValidator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        pureChecks(txn);

        final var tokenCreateTxnBody = txn.tokenCreationOrThrow();
        if (tokenCreateTxnBody.hasTreasury()) {
            final var treasuryId = tokenCreateTxnBody.treasuryOrThrow();
            context.requireKeyOrThrow(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        }
        if (tokenCreateTxnBody.hasAutoRenewAccount()) {
            final var autoRenewalAccountId = tokenCreateTxnBody.autoRenewAccountOrThrow();
            context.requireKeyOrThrow(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        }
        if (tokenCreateTxnBody.hasAdminKey()) {
            context.requireKey(tokenCreateTxnBody.adminKeyOrThrow());
        }
        final var customFees = tokenCreateTxnBody.customFeesOrElse(emptyList());
        addCustomFeeCollectorKeys(context, customFees);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenCreation();
        final var initialSupply = op.initialSupply();
        final var maxSupply = op.maxSupply();
        final var decimals = op.decimals();
        final var supplyType = op.supplyType();
        final var tokenType = op.tokenType();

        var validity = tokenTypeValidator.validateType(tokenType, initialSupply, decimals);
        validateTrue(validity == OK, validity);

        validity = tokenTypeValidator.validateSupplyType(supplyType, maxSupply);
        validateTrue(validity == OK, validity);

        validateFalsePreCheck(maxSupply > 0 && initialSupply > maxSupply, INVALID_TOKEN_INITIAL_SUPPLY);)
        validateTruePreCheck(op.hasTreasury(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        if (tokenType == NON_FUNGIBLE_UNIQUE) {
            validateTruePreCheck(op.hasSupplyKey(), TOKEN_HAS_NO_SUPPLY_KEY);
        }
        if (op.freezeDefault()) {
            validateTruePreCheck(op.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenCreationOrThrow();
        final var config = context.configuration().getConfigData(TokensConfig.class);
        final var readableAccountStore = context.readableStore(ReadableAccountStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var tokenRelationStore = context.writableStore(WritableTokenRelationStore.class);

        // validate fields in the transaction body that involves checking with
        // dynamic properties or state. Also validate custom fees
        validateSemantics(readableAccountStore, tokenStore, op, config);

        // validate expiration and auto-renew account if present
        final var givenExpiryMeta = getExpiryMeta(context.consensusNow().getEpochSecond(), op);
        final var resolvedExpiryMeta = context.expiryValidator().resolveCreationAttempt(false, givenExpiryMeta);

        // validate auto-renew account exists
        if (resolvedExpiryMeta.autoRenewNum() != 0) {
            final var id = AccountID.newBuilder()
                    .accountNum(resolvedExpiryMeta.autoRenewNum())
                    .build();
            final var autoRenewAccount = readableAccountStore.getAccountById(id);
            validateTrue(autoRenewAccount != null, INVALID_AUTORENEW_ACCOUNT);
        }
        // build a token object
        final var newTokenId = context.newEntityNum();
        final var newToken = buildToken(newTokenId, op, resolvedExpiryMeta);

        // validate custom fees and get back list of fees with created token denomination
        final var requireCollectorAutoAssociation = customFeesValidator.validateCreation(
                newToken, readableAccountStore, tokenRelationStore, tokenStore, op.customFees());

        // associate token with treasury and collector ids of custom fees whose token denomination
        // is set to sentinel value
        final var newRels = associateNeededAccounts(
                newToken, readableAccountStore, tokenRelationStore, requireCollectorAutoAssociation);
        if(op.initialSupply() > 0){
            final var treasuryRel = newRels.get(0);
            mint(treasuryRel, op.initialSupply(), newToken);
        }

        // Keep token into modifications in token store
        tokenStore.put(newToken);
        newRels.forEach(rel -> tokenRelationStore.put(rel));
    }

    private List<TokenRelation> associateNeededAccounts(Token newToken,
                                                        ReadableAccountStore readableAccountStore,
                                                        WritableTokenRelationStore writableTokenRelStore,
                                                        Set<CustomFee> requireCollectorAutoAssociation) {
        final var treasury = newToken.treasuryAccountNumber();
        final Set<Long> associatedSoFar = new HashSet<>();
        final List<TokenRelation> newRels = new ArrayList<>();
        associate(newToken, treasury, writableTokenRelStore);
        associatedSoFar.add(treasury);

        for (final var customFee : requireCollectorAutoAssociation) {
            final var collector = customFee.feeCollectorAccountId();
            associate(newToken, collector, writableTokenRelStore);
        }
        return newRels;
    }


    private Token buildToken(long newTokenNum, TokenCreateTransactionBody op, ExpiryMeta resolvedExpiryMeta) {
        return new Token(
                newTokenNum,
                op.name(),
                op.symbol(),
                op.decimals(),
                0, // is this correct ?
                op.treasury().accountNum(),
                op.adminKey(),
                op.kycKey(),
                op.freezeKey(),
                op.wipeKey(),
                op.supplyKey(),
                op.feeScheduleKey(),
                op.pauseKey(),
                0,
                false,
                op.tokenType(),
                op.supplyType(),
                resolvedExpiryMeta.autoRenewNum(),
                resolvedExpiryMeta.autoRenewPeriod(),
                resolvedExpiryMeta.expiry(),
                op.memo(),
                op.maxSupply(),
                false,
                op.freezeDefault(),
                false,
                op.customFees());
    }

    private ExpiryMeta getExpiryMeta(final long consensusTime, final TokenCreateTransactionBody op) {
        final var impliedExpiry =
                consensusTime + op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();
        return new ExpiryMeta(
                impliedExpiry,
                op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds(),
                // Shard and realm will be ignored if num is NA
                op.hasAutoRenewAccount() ? op.autoRenewAccount().shardNum() : NA,
                op.hasAutoRenewAccount() ? op.autoRenewAccount().realmNum() : NA,
                op.hasAutoRenewAccount() ? op.autoRenewAccount().accountNumOrElse(NA) : NA);
    }

    private void validateSemantics(
            final ReadableAccountStore accountStore,
            final WritableTokenStore tokenStore,
            final TokenCreateTransactionBody op,
            final TokensConfig config) {
        // validate treasury exists
        final var treasury = accountStore.getAccountById(op.treasuryOrElse(AccountID.DEFAULT));
        validateTrue(treasury != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        tokenCreateValidator.validate(accountStore, tokenStore, op, config);
        // FUTURE : Need to do areNftsEnabled, memoCheck, tokenSymbolCheck, tokenNameCheck, checkKeys,
        // validateAutoRenewAccount
        validateTrue(op.customFees().size() <= config.maxCustomFeesAllowed(), CUSTOM_FEES_LIST_TOO_LONG);
        customFeesValidator.validateCreation(op.customFees(), accountStore, tokenStore, true);
    }

    /* --------------- Helper methods --------------- */

    /**
     * Validates the collector key from the custom fees.
     *
     * @param context given context
     * @param customFeesList list with the custom fees
     */
    private void addCustomFeeCollectorKeys(
            @NonNull final PreHandleContext context, @NonNull final List<CustomFee> customFeesList)
            throws PreCheckException {

        for (final var customFee : customFeesList) {
            final var collector = customFee.feeCollectorAccountIdOrElse(AccountID.DEFAULT);

            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.fixedFeeOrThrow();
                final var alwaysAdd = fixedFee.hasDenominatingTokenId()
                        && fixedFee.denominatingTokenIdOrThrow().tokenNum() == 0L;
                addAccount(context, collector, alwaysAdd);
            } else if (customFee.hasFractionalFee()) {
                context.requireKeyOrThrow(collector, INVALID_CUSTOM_FEE_COLLECTOR);
            } else {
                // TODO: Need to validate if this is actually needed
                final var royaltyFee = customFee.royaltyFeeOrThrow();
                var alwaysAdd = false;
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.fallbackFeeOrThrow();
                    alwaysAdd = fFee.hasDenominatingTokenId()
                            && fFee.denominatingTokenIdOrThrow().tokenNum() == 0;
                }
                addAccount(context, collector, alwaysAdd);
            }
        }
    }

    /**
     * Signs the metadata or adds failure status.
     *
     * @param context given context
     * @param collector the ID of the collector
     * @param alwaysAdd if true, will always add the key
     */
    private void addAccount(final PreHandleContext context, final AccountID collector, final boolean alwaysAdd)
            throws PreCheckException {
        if (alwaysAdd) {
            context.requireKeyOrThrow(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        } else {
            context.requireKeyIfReceiverSigRequired(collector, INVALID_CUSTOM_FEE_COLLECTOR);
        }
    }
}
