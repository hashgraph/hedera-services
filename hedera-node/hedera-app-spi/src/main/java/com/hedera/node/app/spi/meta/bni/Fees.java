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

package com.hedera.node.app.spi.meta.bni;

import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides access to the cost of gas and other operations within a given {@link Scope}.
 */
public interface Fees {
    /**
     * Returns the gas price in tinybars for this scope.
     *
     * @return the gas price in tinybars
     */
    long gasPrice();

    /**
     * Returns the lazy creation cost within this scope.
     *
     * @return the lazy creation cost in tinybars
     */
    long lazyCreationCostInGas();

    /**
     * Returns the cost to dispatch a {@code TransactionBody} transaction within this scope.
     *
     * @param syntheticTransaction the transaction to dispatch
     * @return the cost in tinybars
     */
    long transactionFeeInGas(@NonNull TransactionBody syntheticTransaction);

    /**
     * Given a fee in tinycents, return the equivalent cost in tinybars at the active exchange rate.
     *
     * @param feeInTinycents the fee in tinycents
     * @return the equivalent cost in tinybars
     */
    long costInTinybars(long feeInTinycents);
}
