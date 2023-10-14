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
public class DispatchGasCalculator {
    private static final long MINIMUM_VIEW_GAS_COST = 100L;

    private final TinybarValues tinybarValues;
    private final CanonicalDispatchPrices canonicalDispatchPrices;
    private final ToLongFunction<TransactionBody> feeCalculator;

    public DispatchGasCalculator(
            @NonNull final TinybarValues tinybarValues,
            @NonNull final CanonicalDispatchPrices canonicalDispatchPrices,
            @NonNull final ToLongFunction<TransactionBody> feeCalculator) {
        this.tinybarValues = Objects.requireNonNull(tinybarValues);
        this.canonicalDispatchPrices = Objects.requireNonNull(canonicalDispatchPrices);
        this.feeCalculator = Objects.requireNonNull(feeCalculator);
    }

    /**
     * Given a transaction body and a minimum equivalent price in tinybars, returns the gas requirement
     * for the transaction to be dispatched.
     *
     * @param body the transaction body to be dispatched
     * @param dispatchType the type of dispatch to be performed
     * @return the gas requirement for the transaction to be dispatched
     */
    public long gasRequirement(@NonNull final TransactionBody body, @NonNull final DispatchType dispatchType) {
        final var nominalFeeInTinybars = feeCalculator.applyAsLong(body);
        final var minimumCostInTinyents = canonicalDispatchPrices.canonicalPriceInTinycents(dispatchType);
        final var minimumCostInTinybars = tinybarValues.asTinybars(minimumCostInTinyents);
        final var feeInTinybars = Math.max(minimumCostInTinybars, nominalFeeInTinybars);

        final var gasCost = tinybarValues.childTransactionServiceGasPrice();
        final var gasRequirement = (feeInTinybars + gasCost - 1) / gasCost;

        return gasRequirement + (gasRequirement / 5);
    }

    /**
     * Although mono-service compares the minimum {@code 100} gas cost to the implied gas requirement of a
     * stand-in {@link com.hedera.hapi.node.transaction.TransactionGetRecordQuery}, that implied requirement
     * will be below the minimum at any exchange rate that sustains the existence of the network. So for
     * simplicity we drop that comparison and just return the minimum.
     *
     * @return the minimum gas requirement for a view query
     */
    public long viewGasRequirement() {
        return MINIMUM_VIEW_GAS_COST;
    }
}
