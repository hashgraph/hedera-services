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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.base.FeeData;

/**
 * Summarizes the base resource prices and active congestion multiplier for a
 * {@link com.hedera.hapi.node.base.HederaFunctionality}.
 *
 * @param basePrices the base resource prices
 * @param congestionMultiplier the active congestion multiplier
 */
public record FunctionalityResourcePrices(FeeData basePrices, long congestionMultiplier) {
    /**
     * The all-zero prices of resources that have been pre-paid via a query header CryptoTransfer.
     */
    public static final FunctionalityResourcePrices PREPAID_RESOURCE_PRICES =
            new FunctionalityResourcePrices(FeeData.DEFAULT, 1);
}
