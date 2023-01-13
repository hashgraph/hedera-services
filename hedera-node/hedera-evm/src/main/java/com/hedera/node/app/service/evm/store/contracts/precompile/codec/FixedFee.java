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

public class FixedFee {

    private final long amount;
    private final Address denominatingTokenId;
    private final boolean useHbarsForPayment;
    private final boolean useCurrentTokenForPayment;
    private final Address feeCollector;

    public FixedFee(
            long amount,
            Address denominatingTokenId,
            boolean useHbarsForPayment,
            boolean useCurrentTokenForPayment,
            Address feeCollector) {
        this.amount = amount;
        this.denominatingTokenId = denominatingTokenId;
        this.useHbarsForPayment = useHbarsForPayment;
        this.useCurrentTokenForPayment = useCurrentTokenForPayment;
        this.feeCollector = feeCollector;
    }

    public long getAmount() {
        return amount;
    }

    public Address getDenominatingTokenId() {
        return denominatingTokenId != null ? denominatingTokenId : Address.ZERO;
    }

    public Address getFeeCollector() {
        return feeCollector != null ? feeCollector : Address.ZERO;
    }

    public boolean isUseHbarsForPayment() {
        return useHbarsForPayment;
    }

    public boolean isUseCurrentTokenForPayment() {
        return useCurrentTokenForPayment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                amount,
                denominatingTokenId,
                useHbarsForPayment,
                useCurrentTokenForPayment,
                feeCollector);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || FixedFee.class != o.getClass()) {
            return false;
        }
        FixedFee other = (FixedFee) o;

        return this.amount == other.amount
                && this.useHbarsForPayment == other.useHbarsForPayment
                && this.useCurrentTokenForPayment == other.useCurrentTokenForPayment;
    }

    @Override
    public String toString() {
        return "FixedFee{"
                + "amount="
                + amount
                + ", denominatingTokenId="
                + denominatingTokenId
                + ", useHbarsForPayment="
                + useHbarsForPayment
                + ", useCurrentTokenForPayment="
                + useCurrentTokenForPayment
                + ", feeCollector="
                + feeCollector
                + '}';
    }
}
