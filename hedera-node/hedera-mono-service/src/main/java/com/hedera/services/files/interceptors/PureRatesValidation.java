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
package com.hedera.services.files.interceptors;

import static java.math.BigInteger.valueOf;

import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import java.math.BigInteger;
import java.util.stream.LongStream;

public class PureRatesValidation {
    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

    PureRatesValidation() {
        throw new IllegalStateException();
    }

    public static boolean isNormalIntradayChange(
            final ExchangeRates midnightRates,
            final ExchangeRateSet proposedRates,
            final int limitPercent) {
        return canonicalTest(
                        limitPercent,
                        midnightRates.getCurrCentEquiv(),
                        midnightRates.getCurrHbarEquiv(),
                        proposedRates.getCurrentRate().getCentEquiv(),
                        proposedRates.getCurrentRate().getHbarEquiv())
                && canonicalTest(
                        limitPercent,
                        midnightRates.getNextCentEquiv(),
                        midnightRates.getNextHbarEquiv(),
                        proposedRates.getNextRate().getCentEquiv(),
                        proposedRates.getNextRate().getHbarEquiv());
    }

    private static boolean canonicalTest(
            final long bound, final long oldC, final long oldH, final long newC, final long newH) {
        final var b100 = valueOf(bound).add(ONE_HUNDRED);

        final var oC = valueOf(oldC);
        final var oH = valueOf(oldH);
        final var nC = valueOf(newC);
        final var nH = valueOf(newH);

        return LongStream.of(bound, oldC, oldH, newC, newH).allMatch(i -> i > 0)
                && oC.multiply(nH)
                                .multiply(b100)
                                .subtract(nC.multiply(oH).multiply(ONE_HUNDRED))
                                .signum()
                        >= 0
                && oH.multiply(nC)
                                .multiply(b100)
                                .subtract(nH.multiply(oC).multiply(ONE_HUNDRED))
                                .signum()
                        >= 0;
    }
}
