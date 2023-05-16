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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Provides validation for custom fees. This class is used by the {@link com.hedera.node.app.service.token.TokenService}
 * to validate custom fees for token creation and fee schedule updates.
 */
public class CustomFeesValidator {
    @Inject
    public CustomFeesValidator() {}

    /**
     * Validates custom fees for {@code TokenCreate} operation.This returns list of custom
     * fees that need to be auto associated with the collector account. This is required
     * for fixed fees with denominating token id set to sentinel value of 0.0.0.
     * NOTE: This logic is subject to change in future PR for TokenCreate
     *
     * @param createdToken The token being created.
     * @param accountStore The account store.
     * @param tokenRelationStore The token relation store.
     * @param tokenStore The token store.
     * @param customFees The custom fees to validate.
     * @return The set of custom fees that need to auto associate collector accounts.
     */
    public Set<CustomFee> validateCreation(
            @NonNull final Token createdToken,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final List<CustomFee> customFees) {
        // It is possible that denominating tokenId is set to sentinel value of 0.0.0.
        // In that scenario, the created token should be used as the denominating token.
        // This is a valid scenario for fungible common tokens.
        // For these custom fees we need to associate the collector with the token.
        final Set<CustomFee> fees = new HashSet<>();
        final var tokenType = createdToken.tokenType();
        for (final var fee : customFees) {
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountId());
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch (fee.fee().kind()) {
                case FIXED_FEE -> validateFixedFeeForCreation(
                        tokenType, fee, createdToken, tokenRelationStore, tokenStore, fees);
                case FRACTIONAL_FEE -> {
                    validateTrue(isFungibleCommon(tokenType), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                    fees.add(fee);
                }
                case ROYALTY_FEE -> validateRoyaltyFeeForCreation(tokenType, fee, tokenRelationStore, tokenStore);
            }
        }
        return fees;
    }

    /**
     * Validates custom fees for {@code TokenFeeScheduleUpdate} operation.
     * @param token  The token being updated.
     * @param accountStore The account store.
     * @param tokenRelationStore The token relation store.
     * @param tokenStore The token store.
     * @param customFees The custom fees to validate.
     */
    public void validateForFeeScheduleUpdate(
            @NonNull final Token token,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final List<CustomFee> customFees) {
        final var tokenType = token.tokenType();
        for (final var fee : customFees) {
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountIdOrElse(AccountID.DEFAULT));
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch (fee.fee().kind()) {
                case FIXED_FEE -> {
                    final var fixedFee = fee.fixedFee();
                    if (fixedFee.hasDenominatingTokenId()) {
                        validateExplicitTokenDenomination(
                                collector.accountNumber(),
                                fixedFee.denominatingTokenId().tokenNum(),
                                tokenRelationStore,
                                tokenStore);
                    }
                }
                case FRACTIONAL_FEE -> {
                    validateTrue(isFungibleCommon(tokenType), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                    final var relation = tokenRelationStore.get(token.tokenNumber(), collector.accountNumber());
                    validateTrue(relation.isPresent(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
                }
                case ROYALTY_FEE -> {
                    validateTrue(
                            isNonFungibleUnique(tokenType), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                    if (fee.royaltyFee().hasFallbackFee()
                            && fee.royaltyFee().fallbackFee().hasDenominatingTokenId()) {
                        final var tokenNum = fee.royaltyFee()
                                .fallbackFee()
                                .denominatingTokenId()
                                .tokenNum();
                        validateExplicitTokenDenomination(
                                collector.accountNumber(), tokenNum, tokenRelationStore, tokenStore);
                    }
                }
            }
        }
    }

    private void validateExplicitTokenDenomination(
            long feeCollectorNum,
            long tokenNum,
            ReadableTokenRelationStore tokenRelationStore,
            WritableTokenStore tokenStore) {
        final var denomToken = tokenStore.get(tokenNum);
        validateTrue(denomToken.isPresent(), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
        validateTrue(isFungibleCommon(denomToken.get().tokenType()), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
        validateTrue(
                tokenRelationStore.get(tokenNum, feeCollectorNum).isPresent(), TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }

    private boolean isFungibleCommon(final TokenType tokenType) {
        return tokenType.equals(TokenType.FUNGIBLE_COMMON);
    }

    private boolean isNonFungibleUnique(final TokenType tokenType) {
        return tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE);
    }

    private void validateFixedFeeForCreation(
            @NonNull final TokenType tokenType,
            @NonNull final CustomFee fee,
            @NonNull final Token createdToken,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final Set<CustomFee> fees) {
        final var fixedFee = fee.fixedFee();
        if (fixedFee.hasDenominatingTokenId()) {
            // If the denominating token id is set to sentinel value 0.0.0, then the fee is
            // denominated in the same token as the token being created.
            if (fixedFee.denominatingTokenId().tokenNum() == 0L) {
                validateTrue(isFungibleCommon(tokenType), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                fee.copyBuilder()
                        .fixedFee(fixedFee.copyBuilder()
                                .denominatingTokenId(TokenID.newBuilder()
                                        .tokenNum(createdToken.tokenNumber())
                                        .build()))
                        .build();
                fees.add(fee);
            } else {
                validateExplicitTokenDenomination(
                        fee.feeCollectorAccountId().accountNum(),
                        fixedFee.denominatingTokenId().tokenNum(),
                        tokenRelationStore,
                        tokenStore);
            }
        }
    }

    private void validateRoyaltyFeeForCreation(
            final TokenType tokenType,
            @NonNull final CustomFee fee,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore) {
        validateTrue(isNonFungibleUnique(tokenType), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
        if (fee.royaltyFee().hasFallbackFee()) {
            final var fallbackFee = fee.royaltyFee().fallbackFee();
            if (fallbackFee.hasDenominatingTokenId()) {
                final var denominatingTokenNum =
                        fallbackFee.denominatingTokenId().tokenNum();
                validateTrue(denominatingTokenNum != 0, CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                validateExplicitTokenDenomination(
                        fee.feeCollectorAccountId().accountNum(), denominatingTokenNum, tokenRelationStore, tokenStore);
            }
        }
    }
}
