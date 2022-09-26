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

import static com.hedera.services.files.interceptors.PureRatesValidation.isNormalIntradayChange;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import java.time.Instant;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PureRatesValidationTest {
    private static int currentHbarEquiv = 30000, nextHbarEquiv = 30000;
    private static int currentCentEquiv = 120000, nextCentEquiv = 120000;
    private static long ratesLifetime = 1_000_000;
    private static long ratesExpiry = Instant.now().getEpochSecond() + ratesLifetime;

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
        ExchangeRateSet smallChangeIncrease = newRates(midnightRates, true, true, true);
        Assertions.assertTrue(isNormalIntradayChange(midnightRates, smallChangeIncrease, bound));

        ExchangeRateSet smallChangeDecrease = newRates(midnightRates, true, true, false);
        Assertions.assertTrue(isNormalIntradayChange(midnightRates, smallChangeDecrease, bound));

        ExchangeRateSet bigChangeInCurrent = newRates(midnightRates, false, true, false);
        Assertions.assertFalse(isNormalIntradayChange(midnightRates, bigChangeInCurrent, bound));

        ExchangeRateSet increasingBigChangeInCurrent = newRates(midnightRates, false, true, true);
        Assertions.assertFalse(
                isNormalIntradayChange(midnightRates, increasingBigChangeInCurrent, bound));

        ExchangeRateSet bigChangeInNext = newRates(midnightRates, true, false, true);
        Assertions.assertFalse(isNormalIntradayChange(midnightRates, bigChangeInNext, bound));

        ExchangeRateSet decreasingBigChangeInCurrent = newRates(midnightRates, true, false, false);
        Assertions.assertFalse(
                isNormalIntradayChange(midnightRates, decreasingBigChangeInCurrent, bound));

        ExchangeRateSet bigChangeInBoth = newRates(midnightRates, false, false, true);
        Assertions.assertFalse(isNormalIntradayChange(midnightRates, bigChangeInBoth, bound));

        ExchangeRateSet decreasingBigChangeInBoth = newRates(midnightRates, false, false, false);
        Assertions.assertFalse(
                isNormalIntradayChange(midnightRates, decreasingBigChangeInBoth, bound));
    }

    private ExchangeRateSet newRates(
            ExchangeRates exchangeRates,
            boolean smallChangeToCurrentRate,
            boolean smallChangeToNextRate,
            boolean increaseRates) {
        Pair<Integer, Integer> currentPair =
                getNewHandC(
                        exchangeRates.getCurrHbarEquiv(),
                        exchangeRates.getCurrCentEquiv(),
                        smallChangeToCurrentRate,
                        increaseRates);

        ExchangeRate.Builder currentRate =
                ExchangeRate.newBuilder()
                        .setHbarEquiv(currentPair.getLeft())
                        .setCentEquiv(currentPair.getRight());

        Pair<Integer, Integer> nextPair =
                getNewHandC(
                        exchangeRates.getNextHbarEquiv(),
                        exchangeRates.getNextCentEquiv(),
                        smallChangeToNextRate,
                        increaseRates);
        ExchangeRate.Builder nextRate =
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
            int oldH, int oldC, boolean smallChange, boolean increase) {
        int newH = oldH;

        long newC;
        int percentage = smallChange ? bound : bound + 100;
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
