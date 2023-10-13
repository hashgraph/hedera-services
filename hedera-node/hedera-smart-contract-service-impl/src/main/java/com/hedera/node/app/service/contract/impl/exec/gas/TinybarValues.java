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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.FEE_SCHEDULE_UNITS_PER_TINYCENT;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromTinycentsToTinybars;

import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides tinybar-denominated resource prices for the current operation.
 */
public class TinybarValues {
    private final ExchangeRate exchangeRate;
    private final FunctionalityResourcePrices resourcePrices;

    public TinybarValues(
            @NonNull final ExchangeRate exchangeRate, @NonNull final FunctionalityResourcePrices resourcePrices) {
        this.exchangeRate = exchangeRate;
        this.resourcePrices = resourcePrices;
    }

    /**
     * Given an amount in tinycents, returns the amount in tinybars at the current exchange rate.
     *
     * @param tinycents the amount in tinycents
     * @return the amount in tinybars
     */
    public long asTinybars(final long tinycents) {
        return fromTinycentsToTinybars(exchangeRate, tinycents);
    }

    /**
     * Returns the tinybar-denominated price of a unit of gas for the current operation based on the current exchange
     * rate, the current congestion multiplier, and the tinycent-denominated price of gas in the {@code service} fee
     * component.
     *
     * @return the tinybar-denominated price of a unit of gas for the current operation
     */
    public long serviceGasPrice() {
        return asTinybars(resourcePrices.basePrices().servicedataOrThrow().gas() / FEE_SCHEDULE_UNITS_PER_TINYCENT);
    }

    /**
     * Returns the tinybar-denominated price of a RAM-byte-hour (rbh) for the current operation based on the current
     * exchange rate, the current congestion multiplier, and the tinycent-denominated price of a rbh in the
     * {@code service} fee component.
     *
     * @return the tinybar-denominated price of a rbh for the current operation
     */
    public long serviceRbhPrice() {
        return asTinybars(resourcePrices.basePrices().servicedataOrThrow().rbh() / FEE_SCHEDULE_UNITS_PER_TINYCENT);
    }
}
