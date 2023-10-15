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

package com.hedera.node.app.service.contract.impl.exec.gas;

import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Computes the gas requirements for dispatching transactions or queries.
 */
public class SystemContractGasCalculator {
    private static final long FIXED_VIEW_GAS_COST = 100L;

    private final TinybarValues tinybarValues;
    private final CanonicalDispatchPrices dispatchPrices;
    private final ToLongFunction<TransactionBody> feeCalculator;

    public SystemContractGasCalculator(
            @NonNull final TinybarValues tinybarValues,
            @NonNull final CanonicalDispatchPrices dispatchPrices,
            @NonNull final ToLongFunction<TransactionBody> feeCalculator) {
        this.tinybarValues = Objects.requireNonNull(tinybarValues);
        this.dispatchPrices = Objects.requireNonNull(dispatchPrices);
        this.feeCalculator = Objects.requireNonNull(feeCalculator);
    }

    /**
     * Convenience method that, given a transaction body whose cost can be directly compared to the minimum
     * cost of a dispatch type, returns the gas requirement for the transaction to be dispatched.
     *
     * @param body the transaction body to be dispatched
     * @param dispatchType the type of dispatch whose minimum cost should be respected
     * @return the gas requirement for the transaction to be dispatched
     */
    public long gasRequirement(@NonNull final TransactionBody body, @NonNull final DispatchType dispatchType) {
        return gasRequirement(body, canonicalPriceInTinybars(dispatchType));
    }

    /**
     * Given a transaction body and a minimum price in tinybars, returns the gas requirement for the transaction
     * to be dispatched.
     *
     * @param body the transaction body to be dispatched
     * @param minimumPriceInTinybars the minimum price in tinybars
     * @return the gas requirement for the transaction to be dispatched
     */
    public long gasRequirement(@NonNull final TransactionBody body, final long minimumPriceInTinybars) {
        final var nominalPriceInTinybars = feeCalculator.applyAsLong(body);
        final var priceInTinybars = Math.max(minimumPriceInTinybars, nominalPriceInTinybars);
        final var gasPrice = tinybarValues.childTransactionServiceGasPrice();
        final var gasRequirement = (priceInTinybars + gasPrice - 1) / gasPrice;
        return gasRequirement + (gasRequirement / 5);
    }

    /**
     * Given a dispatch type, returns the canonical price in tinybars for that dispatch type.
     *
     * @param dispatchType the dispatch type
     * @return the canonical price in tinybars for that dispatch type
     */
    public long canonicalPriceInTinybars(@NonNull final DispatchType dispatchType) {
        return tinybarValues.asTinybars(dispatchPrices.canonicalPriceInTinycents(dispatchType));
    }

    /**
     * Although mono-service compares the fixed {@code 100 gas} cost to the implied gas requirement of a
     * stand-in {@link com.hedera.hapi.node.transaction.TransactionGetRecordQuery}, this stand-in query's
     * cost will be less than {@code 100 gas} at any exchange rate that sustains the existence of the network.
     * So for simplicity, we drop that comparison and just return the fixed {@code 100 gas} cost.
     *
     * @return the minimum gas requirement for a view query
     */
    public long viewGasRequirement() {
        return FIXED_VIEW_GAS_COST;
    }
}
