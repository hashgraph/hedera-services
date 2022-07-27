/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.metadata;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.FcCustomFee;

public final class TokenMetaUtils {
    private TokenMetaUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static TokenSigningMetadata signingMetaFrom(final MerkleToken token) {
        var hasRoyaltyWithFallback = false;
        final var customFees = token.customFeeSchedule();
        if (!customFees.isEmpty()) {
            for (final var customFee : customFees) {
                if (isRoyaltyWithFallback(customFee)) {
                    hasRoyaltyWithFallback = true;
                    break;
                }
            }
        }
        return new TokenSigningMetadata(
                token.adminKey(),
                token.kycKey(),
                token.wipeKey(),
                token.freezeKey(),
                token.supplyKey(),
                token.feeScheduleKey(),
                token.pauseKey(),
                hasRoyaltyWithFallback,
                token.treasury());
    }

    private static boolean isRoyaltyWithFallback(final FcCustomFee fee) {
        return fee.getFeeType() == FcCustomFee.FeeType.ROYALTY_FEE
                && fee.getRoyaltyFeeSpec().fallbackFee() != null;
    }
}
