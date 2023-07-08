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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AdjustmentUtils {
    private AdjustmentUtils() {
        throw new UnsupportedOperationException("Utility Class");
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

    public static CustomFee asFixedFee(
            final long unitsToCollect,
            final TokenID tokenDenomination,
            final AccountID feeCollector,
            final boolean allCollectorsAreExempt) {
        Objects.requireNonNull(feeCollector);
        final var spec = FixedFee.newBuilder()
                .denominatingTokenId(tokenDenomination)
                .amount(unitsToCollect)
                .build();
        return CustomFee.newBuilder()
                .fixedFee(spec)
                .feeCollectorAccountId(feeCollector)
                .allCollectorsAreExempt(allCollectorsAreExempt)
                .build();
    }

    public static void addOrMergeHtsDebit(
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final AccountID sender,
            final AccountID collector,
            final CustomFeeMeta chargingTokenMeta,
            final long amount,
            final TokenID denominatingToken,
            final Set<TokenID> exemptDenoms) {
        if (amount < 0) {
            // TODO: Is this correct to aggregate change here ? In mono-service its added
            //  as new balance change
            addHtsAdjustment(htsAdjustments, sender, collector, amount, denominatingToken);
            // self denominated fees are exempt from further fee charging
            if (chargingTokenMeta.tokenId().equals(denominatingToken)) {
                exemptDenoms.add(denominatingToken);
            }
        } else {
            addHtsAdjustment(htsAdjustments, sender, collector, amount, denominatingToken);
        }
    }

    private static void addHtsAdjustment(
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final AccountID sender,
            final AccountID collector,
            final long amount,
            final TokenID denominatingToken) {
        final var denominatingTokenMap = htsAdjustments.get(denominatingToken);
        denominatingTokenMap.merge(sender, -amount, Long::sum);
        denominatingTokenMap.merge(collector, amount, Long::sum);
        htsAdjustments.put(denominatingToken, denominatingTokenMap);
    }
}
