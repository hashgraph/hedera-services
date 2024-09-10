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

package com.hedera.node.app.service.consensus.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsableForAliasedId;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.ConsensusCustomFee;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;

public class ConsensusCustomFeesValidator {
    /**
     * Constructs a {@link ConsensusCustomFeesValidator} instance.
     */
    @Inject
    public ConsensusCustomFeesValidator() {
        // Needed for Dagger injection
    }

    /**
     * Validates custom fees for {@code ConsensusCreateTopic} operation.
     *
     * @param accountStore       The account store.
     * @param tokenRelationStore The token relation store.
     * @param tokenStore         The token store.
     * @param customFees         The custom fees to validate.
     * @param expiryValidator    The expiry validator to use (for fee collector accounts)
     */
    public void validateForCreation(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final List<ConsensusCustomFee> customFees,
            @NonNull final ExpiryValidator expiryValidator) {
        requireNonNull(accountStore);
        requireNonNull(tokenRelationStore);
        requireNonNull(tokenStore);
        requireNonNull(customFees);
        requireNonNull(expiryValidator);

        for (final var fee : customFees) {
            // Validate the fee collector account is in a usable state
            getIfUsableForAliasedId(
                    fee.feeCollectorAccountIdOrElse(AccountID.DEFAULT),
                    accountStore,
                    expiryValidator,
                    INVALID_CUSTOM_FEE_COLLECTOR);

            final var isSpecified = fee.hasFixedFee();
            validateTrue(isSpecified, CUSTOM_FEE_NOT_FULLY_SPECIFIED);
            validateFixedFeeForCreation(fee, tokenRelationStore, tokenStore);
        }
    }

    private void validateFixedFeeForCreation(
            @NonNull final ConsensusCustomFee fee,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final ReadableTokenStore tokenStore) {
        final var fixedFee = fee.fixedFeeOrThrow();
        validateTrue(fixedFee.amount() > 0, CUSTOM_FEE_MUST_BE_POSITIVE);
        if (fixedFee.hasDenominatingTokenId()) {
            validateExplicitTokenDenomination(
                    fee.feeCollectorAccountId(), fixedFee.denominatingTokenId(), tokenRelationStore, tokenStore);
        }
    }

    /**
     * Validate explicitly set token denomination for custom fees.
     *
     * @param feeCollectorNum The fee collector account number.
     * @param tokenNum The token number used for token denomination.
     * @param tokenRelationStore The token relation store.
     * @param tokenStore The token store.
     */
    private void validateExplicitTokenDenomination(
            @NonNull final AccountID feeCollectorNum,
            @NonNull final TokenID tokenNum,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final ReadableTokenStore tokenStore) {
        final var denomToken = tokenStore.get(tokenNum);
        validateTrue(denomToken != null, INVALID_TOKEN_ID_IN_CUSTOM_FEES);
        validateFalse(denomToken.paused(), INVALID_TOKEN_ID_IN_CUSTOM_FEES);
        validateTrue(isFungibleCommon(denomToken.tokenType()), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
        validateTrue(tokenRelationStore.get(feeCollectorNum, tokenNum) != null, TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
    }

    /**
     * Validates that the given token type is fungible common.
     *
     * @param tokenType The token type to validate.
     * @return {@code true} if the token type is fungible common, otherwise {@code false}
     */
    private boolean isFungibleCommon(@NonNull final TokenType tokenType) {
        return tokenType.equals(TokenType.FUNGIBLE_COMMON);
    }
}
