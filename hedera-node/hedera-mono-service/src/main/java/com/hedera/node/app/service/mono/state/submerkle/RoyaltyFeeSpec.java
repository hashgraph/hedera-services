/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.submerkle;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;

import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Token;

public record RoyaltyFeeSpec(long numerator, long denominator, FixedFeeSpec fallbackFee) {
    public RoyaltyFeeSpec {
        validateTrue(denominator != 0, FRACTION_DIVIDES_BY_ZERO);
        validateTrue(bothPositive(numerator, denominator), CUSTOM_FEE_MUST_BE_POSITIVE);
        validateTrue(numerator <= denominator, ROYALTY_FRACTION_CANNOT_EXCEED_ONE);
    }

    public void validateWith(
            final Token owningToken, final Account feeCollector, final TypedTokenStore tokenStore) {
        validateInternal(owningToken, false, feeCollector, tokenStore);
    }

    public void validateAndFinalizeWith(
            final Token provisionalToken,
            final Account feeCollector,
            final TypedTokenStore tokenStore) {
        validateInternal(provisionalToken, true, feeCollector, tokenStore);
    }

    private void validateInternal(
            final Token token,
            final boolean beingCreated,
            final Account feeCollector,
            final TypedTokenStore tokenStore) {
        validateTrue(
                token.isNonFungibleUnique(),
                CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE);
        if (fallbackFee != null) {
            if (beingCreated) {
                fallbackFee.validateAndFinalizeWith(token, feeCollector, tokenStore);
            } else {
                fallbackFee.validateWith(feeCollector, tokenStore);
            }
        }
    }

    public boolean hasFallbackFee() {
        return fallbackFee != null;
    }

    private boolean bothPositive(long a, long b) {
        return a > 0 && b > 0;
    }
}
