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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;

public class RoyaltyFee {

    private final long numerator;
    private final long denominator;
    private final long amount;
    private final Address denominatingTokenId;
    private final boolean useHbarsForPayment;
    private final Address feeCollector;

    public RoyaltyFee(
            long numerator,
            long denominator,
            long amount,
            Address denominatingTokenId,
            boolean useHbarsForPayment,
            Address feeCollector) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.amount = amount;
        this.denominatingTokenId = denominatingTokenId;
        this.useHbarsForPayment = useHbarsForPayment;
        this.feeCollector = feeCollector;
    }

    public long getNumerator() {
        return numerator;
    }

    public long getDenominator() {
        return denominator;
    }

    public long getAmount() {
        return amount;
    }

    public Address getDenominatingTokenId() {
        return denominatingTokenId != null ? denominatingTokenId : Address.ZERO;
    }

    public boolean isUseHbarsForPayment() {
        return useHbarsForPayment;
    }

    public Address getFeeCollector() {
        return feeCollector != null ? feeCollector : Address.ZERO;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator, amount, denominatingTokenId, useHbarsForPayment,
            feeCollector);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || RoyaltyFee.class != o.getClass()) {
            return false;
        }
        RoyaltyFee other = (RoyaltyFee) o;

        if (getNumerator() != other.getNumerator()) {
            return false;
        }
        if (getDenominator() != other.getDenominator()) {
            return false;
        }
        if (getAmount() != other.getAmount()) {
            return false;
        }
        if (getDenominatingTokenId() != other.getDenominatingTokenId()) {
            return false;
        }
        if (isUseHbarsForPayment() != other.isUseHbarsForPayment()) {
            return false;
        }
        if (getFeeCollector() != other.getFeeCollector()) {
            return false;
        }

        return true;
    }
}
