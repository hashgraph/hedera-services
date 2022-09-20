/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.evm.usage.token;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

import com.hederahashgraph.api.proto.java.CustomFee;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenOpsUsage {
    private static final int FIXED_HBAR_REPR_SIZE = LONG_SIZE;
    private static final int FIXED_HTS_REPR_SIZE = LONG_SIZE + BASIC_ENTITY_ID_SIZE;
    private static final int FRACTIONAL_REPR_SIZE = 4 * LONG_SIZE;
    private static final int ROYALTY_NO_FALLBACK_REPR_SIZE = 2 * LONG_SIZE;
    private static final int ROYALTY_HBAR_FALLBACK_REPR_SIZE =
            ROYALTY_NO_FALLBACK_REPR_SIZE + FIXED_HBAR_REPR_SIZE;
    private static final int ROYALTY_HTS_FALLBACK_REPR_SIZE =
            ROYALTY_NO_FALLBACK_REPR_SIZE + FIXED_HTS_REPR_SIZE;

    @Inject
    public TokenOpsUsage() {
        /* No-op */
    }

    public int bytesNeededToRepr(final List<CustomFee> feeSchedule) {
        int numFixedHbarFees = 0;
        int numFixedHtsFees = 0;
        int numFractionalFees = 0;
        int numRoyaltyNoFallbackFees = 0;
        int numRoyaltyHtsFallbackFees = 0;
        int numRoyaltyHbarFallbackFees = 0;
        for (final var fee : feeSchedule) {
            if (fee.hasFixedFee()) {
                if (fee.getFixedFee().hasDenominatingTokenId()) {
                    numFixedHtsFees++;
                } else {
                    numFixedHbarFees++;
                }
            } else if (fee.hasFractionalFee()) {
                numFractionalFees++;
            } else {
                final var royaltyFee = fee.getRoyaltyFee();
                if (royaltyFee.hasFallbackFee()) {
                    if (royaltyFee.getFallbackFee().hasDenominatingTokenId()) {
                        numRoyaltyHtsFallbackFees++;
                    } else {
                        numRoyaltyHbarFallbackFees++;
                    }
                } else {
                    numRoyaltyNoFallbackFees++;
                }
            }
        }
        return bytesNeededToRepr(
                numFixedHbarFees,
                numFixedHtsFees,
                numFractionalFees,
                numRoyaltyNoFallbackFees,
                numRoyaltyHtsFallbackFees,
                numRoyaltyHbarFallbackFees);
    }

    public int bytesNeededToRepr(
            final int numFixedHbarFees,
            final int numFixedHtsFees,
            final int numFractionalFees,
            final int numRoyaltyNoFallbackFees,
            final int numRoyaltyHtsFallbackFees,
            final int numRoyaltyHbarFallbackFees) {
        return numFixedHbarFees * plusCollectorSize(FIXED_HBAR_REPR_SIZE)
                + numFixedHtsFees * plusCollectorSize(FIXED_HTS_REPR_SIZE)
                + numFractionalFees * plusCollectorSize(FRACTIONAL_REPR_SIZE)
                + numRoyaltyNoFallbackFees * plusCollectorSize(ROYALTY_NO_FALLBACK_REPR_SIZE)
                + numRoyaltyHtsFallbackFees * plusCollectorSize(ROYALTY_HTS_FALLBACK_REPR_SIZE)
                + numRoyaltyHbarFallbackFees * plusCollectorSize(ROYALTY_HBAR_FALLBACK_REPR_SIZE);
    }

    private int plusCollectorSize(final int feeReprSize) {
        return feeReprSize + BASIC_ENTITY_ID_SIZE;
    }
}
