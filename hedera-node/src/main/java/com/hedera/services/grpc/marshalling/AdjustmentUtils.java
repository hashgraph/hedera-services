/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static com.hedera.services.store.models.Id.MISSING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import java.math.BigInteger;

public final class AdjustmentUtils {
    private AdjustmentUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static BalanceChange adjustedChange(
            final Id account,
            final Id chargingToken,
            final Id denom,
            final long amount,
            final BalanceChangeManager manager) {
        /* Always append a new change for an HTS debit since it could trigger another assessed fee */
        if (denom != MISSING_ID && amount < 0) {
            final var htsDebit = includedHtsChange(account, denom, amount, manager);
            /* But self-denominated fees are exempt from further custom fee charging,
            c.f. https://github.com/hashgraph/hedera-services/issues/1925 */
            if (chargingToken.equals(denom)) {
                htsDebit.setExemptFromCustomFees(true);
            }
            return htsDebit;
        }

        /* Otherwise, just update the existing change for this account denomination if present */
        final var extantChange = manager.changeFor(account, denom);
        if (extantChange == null) {
            if (denom == MISSING_ID) {
                final var newHbarChange = BalanceChange.hbarAdjust(account, amount);
                manager.includeChange(newHbarChange);
                return newHbarChange;
            } else {
                return includedHtsChange(account, denom, amount, manager);
            }
        } else {
            extantChange.aggregateUnits(amount);
            return extantChange;
        }
    }

    public static long safeFractionMultiply(final long n, final long d, final long v) {
        if (v != 0 && n > Long.MAX_VALUE / v) {
            return BigInteger.valueOf(v)
                    .multiply(BigInteger.valueOf(n))
                    .divide(BigInteger.valueOf(d))
                    .longValueExact();
        } else {
            return n * v / d;
        }
    }

    static void adjustForAssessedHbar(
            final Id payer,
            final Id collector,
            final long amount,
            final BalanceChangeManager manager) {
        adjustForAssessed(payer, MISSING_ID, collector, MISSING_ID, amount, manager);
    }

    static void adjustForAssessed(
            final Id payer,
            final Id chargingToken,
            final Id collector,
            final Id denom,
            final long amount,
            final BalanceChangeManager manager) {
        final var payerChange = adjustedChange(payer, chargingToken, denom, -amount, manager);
        payerChange.setCodeForInsufficientBalance(
                INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
        adjustedChange(collector, chargingToken, denom, +amount, manager);
    }

    static BalanceChange adjustedFractionalChange(
            final Id account,
            final Id denom,
            final long amount,
            final BalanceChangeManager manager) {
        return adjustedChange(account, MISSING_ID, denom, amount, manager);
    }

    private static BalanceChange includedHtsChange(
            final Id account,
            final Id denom,
            final long amount,
            final BalanceChangeManager manager) {
        final var newHtsChange = BalanceChange.tokenAdjust(account, denom, amount);
        manager.includeChange(newHtsChange);
        return newHtsChange;
    }
}
