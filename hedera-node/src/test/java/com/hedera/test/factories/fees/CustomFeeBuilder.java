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
package com.hedera.test.factories.fees;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenID;

public class CustomFeeBuilder {
    private AccountID feeCollector;

    public CustomFeeBuilder(final AccountID feeCollector) {
        setFeeCollector(feeCollector);
    }

    public void setFeeCollector(final AccountID feeCollector) {
        this.feeCollector = feeCollector;
    }

    private CustomFee.Builder builder() {
        return CustomFee.newBuilder().setFeeCollectorAccountId(feeCollector);
    }

    public CustomFee withOnlyFeeCollector() {
        return builder().build();
    }

    public CustomFee withRoyaltyFee(RoyaltyFee.Builder fee) {
        return builder().setRoyaltyFee(fee).build();
    }

    public CustomFee withFixedFee(final FixedFee.Builder fee) {
        return builder().setFixedFee(fee).build();
    }

    public CustomFee withFractionalFee(final FractionalFee.Builder fee) {
        return builder().setFractionalFee(fee).build();
    }

    public static FixedFee.Builder fixedHbar(final long units) {
        return FixedFee.newBuilder().setAmount(units);
    }

    public static FixedFee.Builder fixedHts(final TokenID denom, final long units) {
        return fixedHbar(units).setDenominatingTokenId(denom);
    }

    public static RoyaltyFee.Builder royaltyNoFallback(long numerator, long denominator) {
        return RoyaltyFee.newBuilder()
                .setExchangeValueFraction(
                        Fraction.newBuilder().setNumerator(numerator).setDenominator(denominator));
    }

    public static RoyaltyFee.Builder royaltyWithFallback(
            long numerator, long denominator, FixedFee.Builder fallback) {
        return RoyaltyFee.newBuilder()
                .setExchangeValueFraction(
                        Fraction.newBuilder().setNumerator(numerator).setDenominator(denominator))
                .setFallbackFee(fallback);
    }

    public static FixedFee.Builder fixedHts(final long units) {
        return fixedHts(TokenID.getDefaultInstance(), units);
    }

    public static FractionalFee.Builder fractional(final long numerator, final long denominator) {
        return FractionalFee.newBuilder()
                .setFractionalAmount(
                        Fraction.newBuilder().setNumerator(numerator).setDenominator(denominator));
    }
}
