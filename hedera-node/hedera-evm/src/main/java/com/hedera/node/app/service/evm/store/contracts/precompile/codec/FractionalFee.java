/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;

public class FractionalFee {

    private final long numerator;
    private final long denominator;
    private final long getMinimumAmount;
    private final long getMaximumAmount;
    private final boolean netOfTransfers;
    private final Address feeCollector;

    public FractionalFee(
            long numerator,
            long denominator,
            long getMinimumAmount,
            long getMaximumAmount,
            boolean netOfTransfers,
            Address feeCollector) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.getMinimumAmount = getMinimumAmount;
        this.getMaximumAmount = getMaximumAmount;
        this.netOfTransfers = netOfTransfers;
        this.feeCollector = feeCollector;
    }

    public long getMinimumAmount() {
        return getMinimumAmount;
    }

    public long getMaximumAmount() {
        return getMaximumAmount;
    }

    public long getNumerator() {
        return numerator;
    }

    public long getDenominator() {
        return denominator;
    }

    public boolean getNetOfTransfers() {
        return netOfTransfers;
    }

    public Address getFeeCollector() {
        return feeCollector != null ? feeCollector : Address.ZERO;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                numerator,
                denominator,
                getMinimumAmount,
                getMaximumAmount,
                netOfTransfers,
                feeCollector);
    }

    @Override
    public boolean equals(Object o) {
        FractionalFee other = (FractionalFee) o;

        return this.numerator == other.numerator
                && this.denominator == other.denominator
                && this.getMinimumAmount == other.getMinimumAmount
                && this.getMaximumAmount == other.getMaximumAmount
                && this.netOfTransfers == other.netOfTransfers;
    }
}
