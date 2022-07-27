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
package com.hedera.services.state.submerkle;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class FractionalFeeSpec {
    private final long numerator;
    private final long denominator;
    private final long minimumUnitsToCollect;
    private final long maximumUnitsToCollect;
    private final boolean netOfTransfers;

    public FractionalFeeSpec(
            long numerator,
            long denominator,
            long minimumUnitsToCollect,
            long maximumUnitsToCollect,
            boolean netOfTransfers) {
        validateTrue(denominator != 0, FRACTION_DIVIDES_BY_ZERO);
        validateTrue(bothPositive(numerator, denominator), CUSTOM_FEE_MUST_BE_POSITIVE);
        validateTrue(
                bothNonnegative(minimumUnitsToCollect, maximumUnitsToCollect),
                CUSTOM_FEE_MUST_BE_POSITIVE);
        validateTrue(
                maximumUnitsToCollect >= minimumUnitsToCollect,
                FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT);

        this.numerator = numerator;
        this.denominator = denominator;
        this.minimumUnitsToCollect = minimumUnitsToCollect;
        this.maximumUnitsToCollect = maximumUnitsToCollect;
        this.netOfTransfers = netOfTransfers;
    }

    public long getNumerator() {
        return numerator;
    }

    public long getDenominator() {
        return denominator;
    }

    public long getMinimumAmount() {
        return minimumUnitsToCollect;
    }

    public long getMaximumUnitsToCollect() {
        return maximumUnitsToCollect;
    }

    public boolean isNetOfTransfers() {
        return netOfTransfers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(FractionalFeeSpec.class)) {
            return false;
        }

        final var that = (FractionalFeeSpec) obj;
        return this.numerator == that.numerator
                && this.denominator == that.denominator
                && this.minimumUnitsToCollect == that.minimumUnitsToCollect
                && this.maximumUnitsToCollect == that.maximumUnitsToCollect
                && this.netOfTransfers == that.netOfTransfers;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FractionalFeeSpec.class)
                .add("numerator", numerator)
                .add("denominator", denominator)
                .add("minimumUnitsToCollect", minimumUnitsToCollect)
                .add("maximumUnitsToCollect", maximumUnitsToCollect)
                .add("netOfTransfers", netOfTransfers)
                .toString();
    }

    private boolean bothPositive(long a, long b) {
        return a > 0 && b > 0;
    }

    private boolean bothNonnegative(long a, long b) {
        return a >= 0 && b >= 0;
    }
}
