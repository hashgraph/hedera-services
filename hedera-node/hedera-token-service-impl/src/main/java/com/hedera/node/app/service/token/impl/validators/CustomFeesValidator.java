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
import static java.util.Objects.requireNonNull;

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
    public CustomFeesValidator() {
        // Needed for Dagger injection
    }

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
    public Set<CustomFee> validateForCreation(
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
            final var collector = accountStore.getAccountById(fee.feeCollectorAccountIdOrElse(AccountID.DEFAULT));
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch (fee.fee().kind()) {
                case FIXED_FEE -> validateFixedFeeForCreation(
                        tokenType, fee, createdToken, tokenRelationStore, tokenStore, fees);
                case FRACTIONAL_FEE -> validateTrue(
                        isFungibleCommon(tokenType), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                case ROYALTY_FEE -> validateRoyaltyFeeForCreation(tokenType, fee, tokenRelationStore, tokenStore);
                default -> throw new IllegalArgumentException(
                        "Unexpected value for custom fee type: " + fee.fee().kind());
            }
        }
        return fees;
    }

    /**
     * Validates custom fees for {@code TokenFeeScheduleUpdate} operation.
     * This method validates the following:
     * 1. Fixed fee with denominating token id set to sentinel value of 0.0.0 is not allowed.
     * 2. Fractional fee can be only applied to fungible common tokens.
     * 3. Royalty fee can be only applied to non-fungible unique tokens.
     * 4. The token must be associated to the fee collector account and fee collector account must exist.
     * 5. The token denomination must be a valid token
     *
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
        requireNonNull(token);
        requireNonNull(accountStore);
        requireNonNull(tokenRelationStore);
        requireNonNull(tokenStore);
        requireNonNull(customFees);

        final var tokenType = token.tokenType();
        for (final var fee : customFees) {
            final var collectorId = fee.feeCollectorAccountIdOrElse(AccountID.DEFAULT);
            final var collector = accountStore.getAccountById(collectorId);
            validateTrue(collector != null, INVALID_CUSTOM_FEE_COLLECTOR);

            switch (fee.fee().kind()) {
                case FIXED_FEE -> {
                    final var fixedFee = fee.fixedFeeOrThrow();
                    // validate any explicit token denomination set
                    if (fixedFee.hasDenominatingTokenId()) {
                        validateExplicitTokenDenomination(
                                collectorId, fixedFee.denominatingTokenId(), tokenRelationStore, tokenStore);
                    }
                }
                case FRACTIONAL_FEE -> {
                    // fractional fee can be only applied to fungible common tokens
                    validateTrue(isFungibleCommon(tokenType), CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);
                    final var tokenId =
                            TokenID.newBuilder().tokenNum(token.tokenNumber()).build();
                    final var relation = tokenRelationStore.get(collectorId, tokenId);
                    validateTrue(relation != null, TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
                }
                case ROYALTY_FEE -> {
                    // royalty fee can be only applied to non-fungible unique tokens
                    validateTrue(
                            isNonFungibleUnique(tokenType), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
                    final var royaltyFee = fee.royaltyFeeOrThrow();
                    if (royaltyFee.hasFallbackFee()
                            && royaltyFee.fallbackFeeOrThrow().hasDenominatingTokenId()) {
                        final var tokenNum = royaltyFee
                                .fallbackFeeOrThrow()
                                .denominatingTokenId()
                                .tokenNum();
                        final var tokenId =
                                TokenID.newBuilder().tokenNum(tokenNum).build();
                        validateExplicitTokenDenomination(collectorId, tokenId, tokenRelationStore, tokenStore);
                    }
                }
                default -> throw new IllegalArgumentException(
                        "Unexpected value for custom fee type: " + fee.fee().kind());
            }
        }
    }

    /**
     * Validate explicitly set token denomination for custom fees.
     * @param feeCollectorNum The fee collector account number.
     * @param tokenNum The token number used for token denomination.
     * @param tokenRelationStore The token relation store.
     * @param tokenStore The token store.
     */
    private void validateExplicitTokenDenomination(
            @NonNull final AccountID feeCollectorNum,
            @NonNull final TokenID tokenNum,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore) {
        final var denomToken = tokenStore.get(tokenNum);
        validateTrue(denomToken != null, INVALID_TOKEN_ID_IN_CUSTOM_FEES);
        validateTrue(isFungibleCommon(denomToken.tokenType()), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
        validateTrue(tokenRelationStore.get(feeCollectorNum, tokenNum) != null, TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }

    /**
     * Validates that the given token type is fungible common.
     * @param tokenType The token type to validate.
     * @return {@code true} if the token type is fungible common, otherwise {@code false}.
     */
    private boolean isFungibleCommon(@NonNull final TokenType tokenType) {
        return tokenType.equals(TokenType.FUNGIBLE_COMMON);
    }

    /**
     * Validates that the given token type is non-fungible unique.
     * @param tokenType The token type to validate.
     * @return {@code true} if the token type is non-fungible unique, otherwise {@code false}.
     */
    private boolean isNonFungibleUnique(@NonNull final TokenType tokenType) {
        return tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE);
    }

    private void validateFixedFeeForCreation(
            @NonNull final TokenType tokenType,
            @NonNull final CustomFee fee,
            @NonNull final Token createdToken,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final Set<CustomFee> feesWithCollectorsToAutoAssociate) {
        final var fixedFee = fee.fixedFeeOrThrow();
        if (fixedFee.hasDenominatingTokenId()) {
            // If the denominating token id is set to sentinel value 0.0.0, then the fee is
            // denominated in the same token as the token being created.
            // For these fees the collector should be auto-associated to the token.
            if (fixedFee.denominatingTokenIdOrThrow().tokenNum() == 0L) {
                validateTrue(isFungibleCommon(tokenType), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                final var copy = fee.copyBuilder();
                copy.fixedFee(fixedFee.copyBuilder()
                        .denominatingTokenId(TokenID.newBuilder()
                                .tokenNum(createdToken.tokenNumber())
                                .build()));
                feesWithCollectorsToAutoAssociate.add(copy.build());
            } else {
                validateExplicitTokenDenomination(
                        fee.feeCollectorAccountId(), fixedFee.denominatingTokenId(), tokenRelationStore, tokenStore);
            }
        }
    }

    private void validateRoyaltyFeeForCreation(
            @NonNull final TokenType tokenType,
            @NonNull final CustomFee fee,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final WritableTokenStore tokenStore) {
        validateTrue(isNonFungibleUnique(tokenType), CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
        final var royaltyFee = fee.royaltyFeeOrThrow();
        if (royaltyFee.hasFallbackFee()) {
            final var fallbackFee = royaltyFee.fallbackFeeOrThrow();
            if (fallbackFee.hasDenominatingTokenId()) {
                final var denominatingTokenId = fallbackFee.denominatingTokenIdOrThrow();
                validateTrue(denominatingTokenId.tokenNum() != 0, CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                validateExplicitTokenDenomination(
                        fee.feeCollectorAccountId(), denominatingTokenId, tokenRelationStore, tokenStore);
            }
        }
    }
}
