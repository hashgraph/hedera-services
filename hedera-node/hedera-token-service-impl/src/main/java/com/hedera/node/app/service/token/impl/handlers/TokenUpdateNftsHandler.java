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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_METADATA_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator.isKeyRemoval;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateValidator;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
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
 * Provides the state transition for an NFT collection update.
 */
@Singleton
public class TokenUpdateNftsHandler implements TransactionHandler {

    private final TokenUpdateValidator tokenUpdateValidator;

    @Inject
    public TokenUpdateNftsHandler(@NonNull final TokenUpdateValidator tokenUpdateValidator) {
        this.tokenUpdateValidator = tokenUpdateValidator;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenUpdateOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);
        if (op.hasFeeScheduleKey()) {
            validateTruePreCheck(isValid(op.feeScheduleKey()), INVALID_CUSTOM_FEE_SCHEDULE_KEY);
        }
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
        if (op.hasMetadataKey()) {
            context.requireKeyOrThrow(op.metadataKeyOrThrow(), INVALID_METADATA_KEY);
        }
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenUpdateOrThrow();
        final var tokenId = op.tokenOrThrow();
        final var recordBuilder = context.recordBuilder(TokenUpdateRecordBuilder.class);

        // validate fields that involve config or state
        // FUTURE - NFT VALIDATOR ?
        final var validationResult = tokenUpdateValidator.validateSemantics(context, op);
        // get the resolved expiry meta and token
        final var token = validationResult.token();
        final var resolvedExpiry = validationResult.resolvedExpiryMeta();

        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var config = context.configuration();
        final var tokensConfig = config.getConfigData(TokensConfig.class);

        // FUTURE - Assume the operation has no change to the treasury ?
        final var tokenBuilder = customizeToken(token, resolvedExpiry, op);
        tokenStore.put(tokenBuilder.build());
        recordBuilder.tokenType(token.tokenType());
    }

    /**
     * Build a Token based on the given token update NFT transaction body.
     * @param token token to be updated
     * @param resolvedExpiry resolved expiry
     * @param op token update transaction body
     * @return updated token builder
     */
    private Token.Builder customizeToken(
            @NonNull final Token token,
            @NonNull final ExpiryMeta resolvedExpiry,
            @NonNull final TokenUpdateTransactionBody op) {
        final var copyToken = token.copyBuilder();
        // All these keys are validated in validateSemantics
        // If these keys did not exist on the token already, they can't be changed on update
        updateKeys(op, token, copyToken);
        updateExpiryFields(op, resolvedExpiry, copyToken);
        updateNameSymbolMetadataMemoAndTreasury(op, copyToken, token);
        return copyToken;
    }

    /**
     * Updates keys of the token if they are present in the token update transaction body.
     * All keys can be updates only if they had already existed on the token.
     * These keys can't be updated if they were not added during creation.
     * @param op token update transaction body
     * @param originalToken original token
     * @param builder token builder
     */
    private void updateKeys(
            final TokenUpdateTransactionBody op, final Token originalToken, final Token.Builder builder) {
        if (op.hasKycKey()) {
            validateTrue(originalToken.hasKycKey(), TOKEN_HAS_NO_KYC_KEY);
            builder.kycKey(op.kycKey());
        }
        if (op.hasFreezeKey()) {
            validateTrue(originalToken.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);
            builder.freezeKey(op.freezeKey());
        }
        if (op.hasWipeKey()) {
            validateTrue(originalToken.hasWipeKey(), TOKEN_HAS_NO_WIPE_KEY);
            builder.wipeKey(op.wipeKey());
        }
        if (op.hasSupplyKey()) {
            validateTrue(originalToken.hasSupplyKey(), TOKEN_HAS_NO_SUPPLY_KEY);
            builder.supplyKey(op.supplyKey());
        }
        if (op.hasFeeScheduleKey()) {
            validateTrue(originalToken.hasFeeScheduleKey(), TOKEN_HAS_NO_FEE_SCHEDULE_KEY);
            builder.feeScheduleKey(op.feeScheduleKey());
        }
        if (op.hasPauseKey()) {
            validateTrue(originalToken.hasPauseKey(), TOKEN_HAS_NO_PAUSE_KEY);
            builder.pauseKey(op.pauseKey());
        }
        //        if (!isExpiryOnlyUpdateOp(op)) {
        //            validateTrue(originalToken.hasAdminKey(), TOKEN_IS_IMMUTABLE);
        //        }
        if (op.hasAdminKey()) {
            final var newAdminKey = op.adminKey();
            if (isKeyRemoval(newAdminKey)) {
                builder.adminKey((Key) null);
            } else {
                builder.adminKey(newAdminKey);
            }
        }
    }
    /**
     * Updates expiry fields of the token if they are present in the token update transaction body.
     * @param op token update transaction body
     * @param resolvedExpiry resolved expiry
     * @param builder token builder
     */
    private void updateExpiryFields(
            final TokenUpdateTransactionBody op, final ExpiryMeta resolvedExpiry, final Token.Builder builder) {
        if (op.hasExpiry()) {
            builder.expirationSecond(resolvedExpiry.expiry());
        }
        if (op.hasAutoRenewPeriod()) {
            builder.autoRenewSeconds(resolvedExpiry.autoRenewPeriod());
        }
        if (op.hasAutoRenewAccount()) {
            builder.autoRenewAccountId(resolvedExpiry.autoRenewAccountId());
        }
    }
    /**
     * Updates token name, token symbol, token memo and token treasury if they are present in the
     * token update transaction body.
     * @param op token update transaction body
     * @param builder token builder
     * @param originalToken original token
     */
    private void updateNameSymbolMetadataMemoAndTreasury(
            final TokenUpdateTransactionBody op, final Token.Builder builder, final Token originalToken) {
        if (op.symbol() != null && op.symbol().length() > 0) {
            builder.symbol(op.symbol());
        }
        if (op.name() != null && op.name().length() > 0) {
            builder.name(op.name());
        }
        if (op.hasMemo()) {
            builder.memo(op.memo());
        }
        if (op.hasTreasury() && !op.treasuryOrThrow().equals(originalToken.treasuryAccountId())) {
            builder.treasuryAccountId(op.treasuryOrThrow());
        }
        if (op.hasMetadata()) {
            builder.metadata(op.metadata());
        }
    }
}
