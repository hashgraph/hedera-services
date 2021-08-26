package com.hedera.services.txns.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */


import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.fees.CustomFee;
import com.hedera.services.store.models.fees.FixedFee;
import com.hedera.services.store.models.fees.RoyaltyFee;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;

/**
 * Provides methods for validating {@link FixedFee} and {@link RoyaltyFee} types of {@link CustomFee}
 */
public class CustomFeeValidator {

    public static void validateFixedFee(
            com.hederahashgraph.api.proto.java.CustomFee grpcFee,
            CustomFee fee,
            Token denominatingToken,
            Account collector
    ) {
        final var amount = fee.getFixedFee().getAmount();
        validateFalse(amount <= 0, CUSTOM_FEE_MUST_BE_POSITIVE);
        if(grpcFee.getFixedFee().hasDenominatingTokenId()) {
            final var grpcDenomId = grpcFee.getFixedFee().getDenominatingTokenId();
            if (grpcDenomId.getTokenNum() != 0) {
                final var isDenomFungible = denominatingToken.getType() == TokenType.FUNGIBLE_COMMON;
                validateTrue(isDenomFungible, CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
                final var isCollectorAssociated = collector.getAssociatedTokens().contains(denominatingToken.getId());
                validateTrue(isCollectorAssociated, TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
            } else {
                validateFalse(denominatingToken.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE), CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON);
            }
        }
    }

    public static void validateRoyaltyFee(
            com.hederahashgraph.api.proto.java.CustomFee grpcFee,
            TokenType tokenType,
            Account collector
    ) {
        final var grpcRoyaltyFee = grpcFee.getRoyaltyFee();
        final var exchangeValueFraction = grpcRoyaltyFee.getExchangeValueFraction();

        validateFalse(exchangeValueFraction.getDenominator() == 0, FRACTION_DIVIDES_BY_ZERO);
        validateTrue(areAllPositiveNumbers(exchangeValueFraction.getNumerator(), exchangeValueFraction.getDenominator()), CUSTOM_FEE_MUST_BE_POSITIVE);

        if (grpcRoyaltyFee.hasFallbackFee()) {
            final var fallbackFee = grpcRoyaltyFee.getFallbackFee();
            final var amount = fallbackFee.getAmount();
            validateFalse(amount <= 0, CUSTOM_FEE_MUST_BE_POSITIVE);
        }

        final var isUnique = tokenType == TokenType.NON_FUNGIBLE_UNIQUE;
        validateTrue(isUnique, CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);

        final var isGreaterThanOne = exchangeValueFraction.getNumerator() > exchangeValueFraction.getDenominator();
        validateFalse(isGreaterThanOne, ROYALTY_FRACTION_CANNOT_EXCEED_ONE);

        final var fallbackGrpc = grpcRoyaltyFee.getFallbackFee();
        if (fallbackGrpc.hasDenominatingTokenId()) {
            final var denomTokenId = fallbackGrpc.getDenominatingTokenId();
            if (denomTokenId.getTokenNum() != 0) {
                validateTrue(
                        collector.getAssociatedTokens().contains(Id.fromGrpcToken(denomTokenId)),
                        TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR);
            }
        }
    }

    public static void initFixedFee(
            com.hederahashgraph.api.proto.java.CustomFee grpcFee,
            CustomFee fee,
            Id denominatingTokenId
    ) {
        final var grpcDenomId = grpcFee.getFixedFee().getDenominatingTokenId();
        if (grpcDenomId.getTokenNum() == 0) {
            fee.getFixedFee().setDenominatingTokenId(denominatingTokenId);
        }
    }

    private static boolean areAllPositiveNumbers(long... numbers) {
        boolean positive = true;
        for (long n : numbers) {
            positive &= n >= 0;
        }
        return positive;
    }
}
