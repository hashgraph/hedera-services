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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenKeyValidation;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class TokenUpdateValidator {
    private final TokenAttributesValidator validator;

    @Inject
    public TokenUpdateValidator(@NonNull final TokenAttributesValidator validator) {
        this.validator = validator;
    }

    public record ValidationResult(@NonNull Token token, @NonNull ExpiryMeta resolvedExpiryMeta) {}

    @NonNull
    public ValidationResult validateSemantics(
            @NonNull final HandleContext context, @NonNull final TokenUpdateTransactionBody op) {
        final var readableAccountStore = context.readableStore(ReadableAccountStore.class);
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenId = op.tokenOrThrow();
        final var token = getIfUsable(tokenId, tokenStore);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        // If the token has an empty admin key it can't be updated
        if (isEmpty(token.adminKey())) {
            validateTrue(BaseTokenHandler.isExpiryOnlyUpdateOp(op), TOKEN_IS_IMMUTABLE);
            validateTrue(BaseTokenHandler.isLowPriorityKeyUpdate(op), TOKEN_IS_IMMUTABLE);
        }
        // validate memo
        if (op.hasMemo()) {
            context.attributeValidator().validateMemo(op.memo());
        }
        // validate metadata
        if (op.hasMetadata()) {
            validator.validateTokenMetadata(op.metadataOrThrow(), tokensConfig);
        }
        // validate token symbol, if being changed
        if (op.symbol() != null && !op.symbol().isEmpty()) {
            validator.validateTokenSymbol(op.symbol(), tokensConfig);
        }
        // validate token name, if being changed
        if (op.name() != null && !op.name().isEmpty()) {
            validator.validateTokenName(op.name(), tokensConfig);
        }
        // validate token keys, if any being changed
        if (op.keyVerificationMode() == TokenKeyValidation.FULL_VALIDATION) {
            validator.validateTokenKeys(
                    op.hasAdminKey(), op.adminKey(),
                    op.hasKycKey(), op.kycKey(),
                    op.hasWipeKey(), op.wipeKey(),
                    op.hasSupplyKey(), op.supplyKey(),
                    op.hasFreezeKey(), op.freezeKey(),
                    op.hasFeeScheduleKey(), op.feeScheduleKey(),
                    op.hasPauseKey(), op.pauseKey(),
                    op.hasMetadataKey(), op.metadataKey());
        }

        // Check whether there is change on the following properties in the transaction body
        // If no change occurred, no need to change them or validate them
        if (!(op.hasExpiry() || op.hasAutoRenewPeriod() || op.hasAutoRenewAccount())) {
            return new ValidationResult(
                    token,
                    new ExpiryMeta(token.expirationSecond(), token.autoRenewSeconds(), token.autoRenewAccountId()));
        }
        final var resolvedExpiryMeta = resolveExpiry(token, op, context.expiryValidator());
        if (op.hasAutoRenewAccount()) {
            validateNewAndExistingAutoRenewAccount(
                    resolvedExpiryMeta.autoRenewAccountId(),
                    token.autoRenewAccountId(),
                    readableAccountStore,
                    context.expiryValidator());
        }
        return new ValidationResult(token, resolvedExpiryMeta);
    }

    private void validateNewAndExistingAutoRenewAccount(
            final AccountID resolvedAutoRenewId,
            final AccountID existingAutoRenewId,
            final ReadableAccountStore readableAccountStore,
            final ExpiryValidator expiryValidator) {
        // Get resolved auto-renewal account
        getIfUsable(resolvedAutoRenewId, readableAccountStore, expiryValidator, INVALID_AUTORENEW_ACCOUNT);
        // If token has an existing auto-renewal account, validate its expiration
        // FUTURE : Not sure why we should validate existing auto-renew account. Retained as in mono-service
        if (!resolvedAutoRenewId.equals(AccountID.DEFAULT) && existingAutoRenewId != null) {
            getIfUsable(existingAutoRenewId, readableAccountStore, expiryValidator, INVALID_AUTORENEW_ACCOUNT);
        }
    }

    private ExpiryMeta resolveExpiry(
            @NonNull final Token token,
            @NonNull final TokenUpdateTransactionBody op,
            @NonNull final ExpiryValidator expiryValidator) {
        final var givenExpiryMeta =
                new ExpiryMeta(token.expirationSecond(), token.autoRenewSeconds(), token.autoRenewAccountId());
        final var updateExpiryMeta = new ExpiryMeta(
                op.hasExpiry() ? op.expiryOrThrow().seconds() : NA,
                op.hasAutoRenewPeriod() ? op.autoRenewPeriodOrThrow().seconds() : NA,
                op.autoRenewAccount());
        return expiryValidator.resolveUpdateAttempt(givenExpiryMeta, updateExpiryMeta, true);
    }
}
