/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromTinybarsToTinycents;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromTinycentsToTinybars;
import static com.hedera.node.app.spi.workflows.FunctionalityResourcePrices.PREPAID_RESOURCE_PRICES;

import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * Provides tinybar-denominated resource prices for the current operation (could be either transaction or query).
 */
public class TinybarValues {
    private final ExchangeRate exchangeRate;
    private final boolean isGasPrecisionLossFixEnabled;
    private final boolean isCanonicalViewGasEnabled;
    private final FunctionalityResourcePrices topLevelResourcePrices;
    // Only non-null for a top-level transaction, since queries cannot have child transactions
    @Nullable
    private final FunctionalityResourcePrices childTransactionResourcePrices;

    /**
     * Creates a new instance of {@link TinybarValues} for a query; this throws {@link IllegalStateException}
     * if {@link #childTransactionTinybarGasPrice()} is called; and returns a zero top-level gas price because
     * queries have their gas "pre-paid" via the query payment in the query header, and we don't want to try
     * to charge it again when answering a {@code ContractCallLocal}.
     *
     * @param exchangeRate the current exchange rate
     * @return a query-appropriate instance of {@link TinybarValues}
     */
    public static TinybarValues forQueryWith(
            @NonNull final ExchangeRate exchangeRate, @NonNull final ContractsConfig contractsConfig) {
        return new TinybarValues(exchangeRate, contractsConfig, PREPAID_RESOURCE_PRICES, null);
    }

    /**
     * Creates a new instance of {@link TinybarValues} for a transaction; this is capable of computing
     * gas costs for dispatching child transactions.
     *
     * @param exchangeRate the current exchange rate
     * @param topLevelResourcePrices the current resource prices for the top-level transaction
     * @param childTransactionResourcePrices the current resource prices for child transactions
     * @return a transaction-appropriate instance of {@link TinybarValues}
     */
    public static TinybarValues forTransactionWith(
            @NonNull final ExchangeRate exchangeRate,
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final FunctionalityResourcePrices topLevelResourcePrices,
            @Nullable final FunctionalityResourcePrices childTransactionResourcePrices) {
        return new TinybarValues(exchangeRate, contractsConfig, topLevelResourcePrices, childTransactionResourcePrices);
    }

    private TinybarValues(
            @NonNull final ExchangeRate exchangeRate,
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final FunctionalityResourcePrices topLevelResourcePrices,
            @Nullable final FunctionalityResourcePrices childTransactionResourcePrices) {
        this.exchangeRate = Objects.requireNonNull(exchangeRate);
        this.topLevelResourcePrices = Objects.requireNonNull(topLevelResourcePrices);
        this.childTransactionResourcePrices = childTransactionResourcePrices;
        this.isGasPrecisionLossFixEnabled = contractsConfig.isGasPrecisionLossFixEnabled();
        this.isCanonicalViewGasEnabled = contractsConfig.isCanonicalViewGasEnabled();
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

    public long asTinycents(final long tinyBars) {
        return fromTinybarsToTinycents(exchangeRate, tinyBars);
    }

    /**
     * Returns the tinybar-denominated price of a unit of gas for the current operation based on the current exchange
     * rate, the current congestion multiplier, and the tinycent-denominated price of gas in the {@code service} fee
     * component.
     *
     * @return the tinybar-denominated price of a unit of gas for the current operation
     */
    public long topLevelTinybarGasPrice() {
        return asTinybars(
                topLevelResourcePrices.basePrices().servicedataOrThrow().gas()
                        / FEE_SCHEDULE_UNITS_PER_TINYCENT
                        * topLevelResourcePrices.congestionMultiplier());
    }

    /**
     * Returns the tinyBar price of a unit of gas for the current operation based on the current exchange
     * rate, the current congestion multiplier without being denominated in tinycents units.
     *
     * @return the full precision tinybar price of a unit of gas for the current operation
     */
    public long topLevelTinybarGasPriceFullPrecision() {
        return asTinybars(
                topLevelResourcePrices.basePrices().servicedataOrThrow().gas()
                        * topLevelResourcePrices.congestionMultiplier());
    }

    /**
     * Returns the topLevel gas price cost in tinycents, without denomination, but with congestion multiplier.
     * @return the tinycents gas price
     */
    public long topLevelTinycentGasPrice() {
        if (!isGasPrecisionLossFixEnabled) {
            return topLevelTinybarGasPrice();
        }
        return topLevelResourcePrices.basePrices().servicedataOrThrow().gas()
                * topLevelResourcePrices.congestionMultiplier();
    }

    /**
     * Returns the tinybar-denominated price of a unit of gas for dispatching a child transaction based on the current exchange
     * rate, the current congestion multiplier, and the tinycent-denominated price of gas in the {@code service} fee
     * component.
     *
     * @return the tinybar-denominated price of a unit of gas for dispatching a child transaction
     */
    public long childTransactionTinybarGasPrice() {
        if (childTransactionResourcePrices == null) {
            throw new IllegalStateException("Cannot dispatch a child transaction from a query");
        }
        return asTinybars(
                childTransactionResourcePrices.basePrices().servicedataOrThrow().gas()
                        / FEE_SCHEDULE_UNITS_PER_TINYCENT
                        * childTransactionResourcePrices.congestionMultiplier());
    }

    /**
     * Returns the tinycent gas price for dispatching a child transaction based on the current exchange rate,
     * Without denomination, but with congestion multiplier, saving the precision.
     * @return the tinycent gas price
     */
    public long childTransactionTinycentGasPrice() {
        if (childTransactionResourcePrices == null) {
            throw new IllegalStateException("Cannot dispatch a child transaction from a query");
        }
        return childTransactionResourcePrices.basePrices().servicedataOrThrow().gas()
                * childTransactionResourcePrices.congestionMultiplier();
    }

    /**
     * Note: this part of the javadoc will be removed after {@code isGasPrecisionLossFixEnabled} flag is removed.
     * Returns the tinybar-denominated price of a RAM-byte-hour (rbh) for the current operation based on the current
     * exchange rate, the current congestion multiplier.
     *
     * Or return the tinycent-denominated price of a RAM-byte-hour (rbh) in the {@code service} fee component
     * with the current congestion multiplier.
     *
     * @return the tinybar/tinycent-denominated price of a rbh for the current operation
     */
    public long topLevelTinycentRbhPrice() {
        if (!isGasPrecisionLossFixEnabled) {
            return asTinybars(
                    topLevelResourcePrices.basePrices().servicedataOrThrow().rbh()
                            / FEE_SCHEDULE_UNITS_PER_TINYCENT
                            * topLevelResourcePrices.congestionMultiplier());
        }
        return topLevelResourcePrices.basePrices().servicedataOrThrow().rbh()
                * topLevelResourcePrices.congestionMultiplier();
    }

    /**
     * This can be removed after integrity of the fix is confirmed.
     * We have it as a temporary measure to allow for easy rollback in case of issues.
     */
    public boolean isGasPrecisionLossFixEnabled() {
        return isGasPrecisionLossFixEnabled;
    }

    /**
     * This can be removed after the dynamic gas for view operations is confirmed.
     * We have it as a temporary measure to allow for easy rollback in case of issues.
     */
    public boolean isCanonicalViewGasEnabled() {
        return isCanonicalViewGasEnabled;
    }
}
