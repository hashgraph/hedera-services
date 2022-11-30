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
package com.hedera.node.app.service.mono.files.interceptors;

import static com.hedera.node.app.service.mono.files.interceptors.PureRatesValidation.isNormalIntradayChange;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import java.time.Instant;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PureRatesValidationTest {
    private static final int currentHbarEquiv = 30000;
    private static final int nextHbarEquiv = 30000;
    private static final int currentCentEquiv = 120000;
    private static final int nextCentEquiv = 120000;
    private static final long ratesLifetime = 1_000_000;
    private static final long ratesExpiry = Instant.now().getEpochSecond() + ratesLifetime;

    private static ExchangeRates midnightRates;

    int bound = 1;

    @BeforeAll
    public static void setUp() {
        midnightRates =
                new ExchangeRates(
                        currentHbarEquiv,
                        currentCentEquiv,
                        ratesExpiry,
                        nextHbarEquiv,
                        nextCentEquiv,
                        ratesExpiry);
    }

    @Test
    void isSmallChangeTest() {
        final ExchangeRateSet smallChangeIncrease = newRates(midnightRates, true, true, true);
        Assertions.assertTrue(isNormalIntradayChange(midnightRates, smallChangeIncrease, bound));

        final ExchangeRateSet smallChangeDecrease = newRates(midnightRates, true, true, false);
        Assertions.assertTrue(isNormalIntradayChange(midnightRates, smallChangeDecrease, bound));

        final ExchangeRateSet bigChangeInCurrent = newRates(midnightRates, false, true, false);
        Assertions.assertFalse(isNormalIntradayChange(midnightRates, bigChangeInCurrent, bound));

        final ExchangeRateSet increasingBigChangeInCurrent =
                newRates(midnightRates, false, true, true);
        Assertions.assertFalse(
                isNormalIntradayChange(midnightRates, increasingBigChangeInCurrent, bound));

        final ExchangeRateSet bigChangeInNext = newRates(midnightRates, true, false, true);
        Assertions.assertFalse(isNormalIntradayChange(midnightRates, bigChangeInNext, bound));

        final ExchangeRateSet decreasingBigChangeInCurrent =
                newRates(midnightRates, true, false, false);
        Assertions.assertFalse(
                isNormalIntradayChange(midnightRates, decreasingBigChangeInCurrent, bound));

        final ExchangeRateSet bigChangeInBoth = newRates(midnightRates, false, false, true);
        Assertions.assertFalse(isNormalIntradayChange(midnightRates, bigChangeInBoth, bound));

        final ExchangeRateSet decreasingBigChangeInBoth =
                newRates(midnightRates, false, false, false);
        Assertions.assertFalse(
                isNormalIntradayChange(midnightRates, decreasingBigChangeInBoth, bound));
    }

    private ExchangeRateSet newRates(
            final ExchangeRates exchangeRates,
            final boolean smallChangeToCurrentRate,
            final boolean smallChangeToNextRate,
            final boolean increaseRates) {
        final Pair<Integer, Integer> currentPair =
                getNewHandC(
                        exchangeRates.getCurrHbarEquiv(),
                        exchangeRates.getCurrCentEquiv(),
                        smallChangeToCurrentRate,
                        increaseRates);

        final ExchangeRate.Builder currentRate =
                ExchangeRate.newBuilder()
                        .setHbarEquiv(currentPair.getLeft())
                        .setCentEquiv(currentPair.getRight());

        final Pair<Integer, Integer> nextPair =
                getNewHandC(
                        exchangeRates.getNextHbarEquiv(),
                        exchangeRates.getNextCentEquiv(),
                        smallChangeToNextRate,
                        increaseRates);
        final ExchangeRate.Builder nextRate =
                ExchangeRate.newBuilder()
                        .setHbarEquiv(nextPair.getLeft())
                        .setCentEquiv(nextPair.getRight());

        return ExchangeRateSet.newBuilder()
                .setCurrentRate(currentRate)
                .setNextRate(nextRate)
                .build();
    }

    /**
     * Generate a pair of values (newH, newC), which is or is not a small change comparing to the
     * values in the given oldC and oldH A change is a small change iff for arbitrary-precision
     * numbers:
     *
     * <pre>
     * 	oldC/oldH * (1 + bound/100) >= newC/newH >= oldC/oldH * 1/(1 + bound/100)
     * </pre>
     *
     * Equivalently, it is a small change iff both of the following are true:
     *
     * <pre>
     * 	oldC * newH * (100 + bound) - newC * oldH * 100 >= 0
     * 	oldH * newC * (100 + bound) - newH * oldC * 100 >= 0
     * </pre>
     */
    private Pair<Integer, Integer> getNewHandC(
            final int oldH, final int oldC, final boolean smallChange, final boolean increase) {
        final int newH = oldH;

        final long newC;
        final int percentage = smallChange ? bound : bound + 100;
        if (increase) {
            newC = (long) (oldC * (100l + percentage) / 100.0);
        } else {
            newC = (long) Math.ceil(oldC * 100.0 / (100l + percentage));
        }

        if (newC > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("newC overflowed generate new rate!");
        }

        return Pair.of(newH, (int) newC);
    }

    @Test
    void cannotBeConstructed() {
        // expect:
        assertThrows(IllegalStateException.class, PureRatesValidation::new);
    }
}
