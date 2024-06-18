/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.fees;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A calculator for determining the resource prices for a given {@link HederaFunctionality} and {@link SubType}.
 */
public interface ResourcePriceCalculator {

    /**
     * Returns the Hedera resource prices (in thousandths of a tinycent) for the given {@link SubType} of
     * the given {@link HederaFunctionality}. The contract service needs this information to determine both the
     * gas price and the cost of storing logs (a function of the {@code rbh} price, which may itself vary by
     * contract operation type).
     *
     * @param functionality the {@link HederaFunctionality} of interest
     * @param subType the {@link SubType} of interest
     * @return the corresponding Hedera resource prices
     */
    @NonNull
    FunctionalityResourcePrices resourcePricesFor(
            @NonNull final HederaFunctionality functionality, @NonNull final SubType subType);
}
