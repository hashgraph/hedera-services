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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.evm.utils.ValidationUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.records.CreateTokenRecordBuilder;
import com.hedera.node.app.service.token.impl.records.TokenCreateRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_CREATE}.
 */
@Singleton
public class TokenCreateHandler implements TransactionHandler {
    @Inject
    public TokenCreateHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenCreationOrThrow();
        final var validationResult = pureChecks(op);
        if (validationResult != OK) {
            throw new PreCheckException(validationResult);
        }
        if (op.hasTreasury()) {
            final var treasuryId = op.treasuryOrThrow();
            context.requireKeyOrThrow(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        }
        if (op.hasAutoRenewAccount()) {
            final var autoRenewalAccountId = op.autoRenewAccountOrThrow();
            context.requireKeyOrThrow(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        }
        if (op.hasAdminKey()) {
            context.requireKey(op.adminKeyOrThrow());
        }
        final var customFees = op.customFeesOrElse(emptyList());
        addCustomFeeCollectorKeys(context, customFees);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final HandleContext handleContext,
            @NonNull final TransactionBody txn,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenStore tokenStore) {
        final var op = txn.tokenCreationOrThrow();

        // validate fields in the transaction body that involves checking with
        // dynamic properties or state
        final ResponseCodeEnum validationResult = validateSemantics();
        if (validationResult != OK) {
            throw new HandleException(validationResult);
        }
//        final var impliedExpiry = handleContext.consensusNow().getEpochSecond()
//                + op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds();
//        final var entityExpiryMeta = new ExpiryMeta(
//                impliedExpiry,
//                op.autoRenewPeriodOrElse(Duration.DEFAULT).seconds(),
//                // Shard and realm will be ignored if num is NA
//                op.hasAutoRenewAccount() ? op.autoRenewAccount().shardNum() : NA,
//                op.hasAutoRenewAccount() ? op.autoRenewAccount().realmNum() : NA,
//                op.hasAutoRenewAccount() ? op.autoRenewAccount().accountNumOrElse(NA) : NA);
//        final var hasValidOrNoExplicitExpiry = !op.hasExpiry() || handleContext.expiryValidator().resolveCreationAttempt(false, entityExpiryMeta);
//        validateTrue(hasValidOrNoExplicitExpiry, INVALID_EXPIRATION_TIME);
//
//        final var treasuryId = Id.fromGrpcAccount(op.getTreasury());
//        treasury = accountStore.loadAccountOrFailWith(treasuryId, com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
//        autoRenew = null;
//        if (op.hasAutoRenewAccount()) {
//            final var autoRenewId = Id.fromGrpcAccount(op.getAutoRenewAccount());
//            autoRenew = accountStore.loadAccountOrFailWith(autoRenewId, com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
//        }
//
//        provisionalId = Id.fromGrpcToken(ids.newTokenId(sponsor));
//
//        // --- Do the business logic ---
//        creation.doProvisionallyWith(now, MODEL_FACTORY, RELS_LISTING);
//
//        // --- Persist the created model ---
//        creation.persist();
    }

    private void pureChecks(@NonNull final TokenCreateTransactionBody op) {
        final var initialSupply = op.initialSupply();
        final var maxSupply = op.maxSupply();
        final var decimals = op.decimals();
        final var supplyType = op.supplyType();
        final var tokenType = op.tokenType();

        var validity = typeCheck(tokenType, initialSupply, decimals);
        validateTrue(validity == OK, validity);

        validity = supplyTypeCheck(supplyType, maxSupply);
        validateTrue(validity == OK, validity);

        if (maxSupply > 0 && initialSupply > maxSupply) {
            throw new HandleException(INVALID_TOKEN_INITIAL_SUPPLY);
        }

        if (tokenType == NON_FUNGIBLE_UNIQUE && !op.hasSupplyKey()) {
            throw new HandleException(TOKEN_HAS_NO_SUPPLY_KEY);
        }

        if (!op.hasTreasury()) {
            throw new HandleException(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
        }
        
        if (op.freezeDefault() && !op.hasFreezeKey()) {
            throw new HandleException(TOKEN_HAS_NO_FREEZE_KEY);
        }
    }

    private ResponseCodeEnum validateSemantics() {
        return OK;
        // FUTURE : Need to do areNftsEnabled, memoCheck, tokenSymbolCheck, tokenNameCheck, checkKeys,
        // validateAutoRenewAccount
    }


    @Override
    public TokenCreateRecordBuilder newRecordBuilder() {
        return new CreateTokenRecordBuilder();
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

    private ResponseCodeEnum typeCheck(final TokenType type, final long initialSupply, final int decimals) {
        switch (type) {
            case FUNGIBLE_COMMON -> {
                return initialSupply < 0
                        ? INVALID_TOKEN_INITIAL_SUPPLY
                        : (decimals < 0 ? INVALID_TOKEN_DECIMALS : OK);
                    }
            case NON_FUNGIBLE_UNIQUE -> {
                return initialSupply != 0
                        ? INVALID_TOKEN_INITIAL_SUPPLY
                        : (decimals != 0 ? INVALID_TOKEN_DECIMALS : OK);
            }
        }
        return NOT_SUPPORTED;
    }

    public ResponseCodeEnum supplyTypeCheck(final TokenSupplyType supplyType, final long maxSupply) {
        switch (supplyType) {
            case INFINITE:
                return maxSupply != 0 ? INVALID_TOKEN_MAX_SUPPLY : ResponseCodeEnum.OK;
            case FINITE:
                return maxSupply <= 0 ? INVALID_TOKEN_MAX_SUPPLY : ResponseCodeEnum.OK;
            default:
                return NOT_SUPPORTED;
        }
    }
}
