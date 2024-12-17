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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.ToLongBiFunction;

/**
 * Computes the gas requirements for dispatching transactions or queries.
 */
public class SystemContractGasCalculator {
    private static final long FIXED_VIEW_GAS_COST = 100L;

    // This represents the predefined gas price which is $0.000_000_0852 per unit of gas.
    // 852_000 = $0.000_000_0852 * 100(cents per dollars) * 100_000_000 (tiny cents per cents) * 1000 (Fee schedule
    // units
    // per tiny cents). For more info -> https://hedera.com/blog/rolling-smart-contract-hedera-api-fees-into-gas-fees
    private static final long FIXED_TINY_CENT_GAS_PRICE_COST = 852_000L;

    private final TinybarValues tinybarValues;
    private final CanonicalDispatchPrices dispatchPrices;
    private final ToLongBiFunction<TransactionBody, AccountID> feeCalculator;

    public SystemContractGasCalculator(
            @NonNull final TinybarValues tinybarValues,
            @NonNull final CanonicalDispatchPrices dispatchPrices,
            @NonNull final ToLongBiFunction<TransactionBody, AccountID> feeCalculator) {
        this.tinybarValues = requireNonNull(tinybarValues);
        this.dispatchPrices = requireNonNull(dispatchPrices);
        this.feeCalculator = requireNonNull(feeCalculator);
    }

    /**
     * Convenience method that, given a transaction body whose cost can be directly compared to the minimum
     * cost of a dispatch type, returns the gas requirement for the transaction to be dispatched.
     *
     * @param body the transaction body to be dispatched
     * @param dispatchType the type of dispatch whose minimum cost should be respected
     * @param payer the payer of the transaction
     * @return the gas requirement for the transaction to be dispatched
     */
    public long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final DispatchType dispatchType,
            @NonNull final AccountID payer) {
        requireNonNull(body);
        requireNonNull(dispatchType);
        requireNonNull(payer);
        // isGasPrecisionLossFixEnabled is a temporary feature flag that will be removed in the future.
        if (!tinybarValues.isGasPrecisionLossFixEnabled()) {
            return gasRequirementOldWithPrecisionLoss(body, payer, canonicalPriceInTinybars(dispatchType));
        }
        return gasRequirementWithTinycents(body, payer, dispatchPrices.canonicalPriceInTinycents(dispatchType));
    }

    /**
     * Compares the canonical price and the feeCalculator's calculated price and uses the maximum of the two to
     * calculate the gas requirement and returns it.
     * @param body the transaction body
     * @param payer the payer of the transaction
     * @param minimumPriceInTinycents the minimum price in tiny cents
     * @return the gas requirement for the transaction
     */
    public long gasRequirementWithTinycents(
            @NonNull final TransactionBody body, @NonNull final AccountID payer, final long minimumPriceInTinycents) {
        // If not enabled, make the calculation using the old method.
        if (!tinybarValues.isGasPrecisionLossFixEnabled()) {
            return gasRequirementOldWithPrecisionLoss(body, payer, minimumPriceInTinycents);
        }
        final var computedPriceInTinybars = feeCalculator.applyAsLong(body, payer);
        final var priceInTinycents =
                Math.max(minimumPriceInTinycents, tinybarValues.asTinycents(computedPriceInTinybars));
        // For the rare cases where computedPrice > minimumPriceInTinycents:
        // Precision loss may occur as we convert between tinyBars and tinycents, but it is typically negligible.
        // The minimal computed price is > 1e6 tinycents, ensuring enough precision.
        // In most cases, the gas difference is zero.
        // In scenarios where we compare significant price fluctuations (200x, 100x), the gas difference should still be
        // unlikely to exceed 0 gas.
        return gasRequirementFromTinycents(priceInTinycents, tinybarValues.childTransactionTinycentGasPrice());
    }

    /**
     * Returns the gas price for the top-level HAPI operation.
     *
     * @return the gas price for the top-level HAPI operation in tinyBars.
     */
    public long topLevelGasPriceInTinyBars() {
        return tinybarValues.topLevelTinybarGasPriceFullPrecision();
    }

    /**
     * Estimates the gas requirement for a view operation.
     * The minimum gas requirement is 100 gas.
     * For all view operations, the gas requirement is determined using the canonical gas value
     * for the TOKEN_INFO dispatch type, as specified in the canonical-prices.json.
     * The TOKEN_INFO operation is representative of view operations.
     *
     * @return the gas requirement for a view operation
     */
    public long viewGasRequirement() {
        // isCanonicalViewGasEnabled is a temporary feature flag that will be removed in the future.
        if (!tinybarValues.isCanonicalViewGasEnabled()) {
            return FIXED_VIEW_GAS_COST;
        }
        final var gasRequirement = gasRequirementFromTinycents(
                dispatchPrices.canonicalPriceInTinycents(DispatchType.TOKEN_INFO), FIXED_TINY_CENT_GAS_PRICE_COST);
        return Math.max(FIXED_VIEW_GAS_COST, gasRequirement);
    }

    /**
     * Given a dispatch type, returns the canonical gas requirement for that dispatch type.
     * Useful when providing a ballpark gas requirement in the absence of a valid
     * transaction body for the dispatch type.
     * Used for non-query operations.
     *
     * @param dispatchType the dispatch type
     * @return the canonical gas requirement for that dispatch type
     */
    public long canonicalGasRequirement(@NonNull final DispatchType dispatchType) {
        // isGasPrecisionLossFixEnabled is a temporary feature flag that will be removed in the future.
        if (!tinybarValues.isGasPrecisionLossFixEnabled()) {
            return asGasRequirement(canonicalPriceInTinybars(dispatchType));
        }
        return gasRequirementFromTinycents(
                dispatchPrices.canonicalPriceInTinycents(dispatchType),
                tinybarValues.childTransactionTinycentGasPrice());
    }

    /**
     * Given a dispatch type, returns the canonical price for that dispatch type.
     *
     * @param dispatchType the dispatch type
     * @return the canonical price for that dispatch type
     */
    public long canonicalPriceInTinycents(@NonNull final DispatchType dispatchType) {
        requireNonNull(dispatchType);
        // If not enabled, return the price in TinyBars.
        // This is directly used only in ClassicTransfersCall. However, it is easier to place the feature flag here.
        if (!tinybarValues.isGasPrecisionLossFixEnabled()) {
            return canonicalPriceInTinybars(dispatchType);
        }
        return dispatchPrices.canonicalPriceInTinycents(dispatchType);
    }

    /**
     * Given a dispatch, returns the canonical price for that dispatch.
     *
     * @param body the transaction body to be dispatched
     * @param payer the payer account
     * @return the canonical price for that dispatch
     */
    public long feeCalculatorPriceInTinyBars(@NonNull final TransactionBody body, @NonNull final AccountID payer) {
        return feeCalculator.applyAsLong(body, payer);
    }

    /**
     * Given a gas requirement, returns the equivalent tinybar cost at the current gas price.
     *
     * @param gas the gas requirement
     * @return the equivalent tinybar cost at the current gas price
     */
    public long gasCostInTinybars(final long gas) {
        return gas * tinybarValues.childTransactionTinybarGasPrice();
    }

    /**
     * Calculates the gas requirement for an operation based on the provided tinycents price and gas price.
     * The calculation rounds up the result to the nearest gas unit and then adds 20% to the computed gas
     * requirement to account for the premium of executing a HAPI operation within the EVM.
     *
     * @param tinycentsPrice the price of the operation in tinycents
     * @param gasPriceInCents the current gas price in cents
     * @return the computed gas requirement for the operation
     */
    private long gasRequirementFromTinycents(long tinycentsPrice, final long gasPriceInCents) {
        final var gasRequirement =
                (tinycentsPrice + gasPriceInCents - 1) * FEE_SCHEDULE_UNITS_PER_TINYCENT / gasPriceInCents;
        return gasRequirement + (gasRequirement / 5);
    }

    /**
     * After feature flag isGasPrecisionLossFixEnabled is removed, this method should be removed.
     * @deprecated
     */
    @Deprecated(since = "Precision loss fix was implemented in PR #14842", forRemoval = true)
    public long topLevelGasPrice() {
        return tinybarValues.topLevelTinybarGasPrice();
    }

    /**
     * After feature flag isGasPrecisionLossFixEnabled is removed, this method should be removed.
     * @deprecated
     */
    @Deprecated(since = "Precision loss fix was implemented in PR #14842", forRemoval = true)
    public long canonicalPriceInTinybars(@NonNull final DispatchType dispatchType) {
        requireNonNull(dispatchType);
        return tinybarValues.asTinybars(dispatchPrices.canonicalPriceInTinycents(dispatchType));
    }

    /**
     * After feature flag isGasPrecisionLossFixEnabled is removed, this method should be removed.
     * @deprecated
     */
    @Deprecated(since = "Precision loss fix was implemented in PR #14842", forRemoval = true)
    public long gasRequirementOldWithPrecisionLoss(
            @NonNull final TransactionBody body, @NonNull final AccountID payer, final long minimumPriceInTinybars) {
        final var nominalPriceInTinybars = feeCalculator.applyAsLong(body, payer);
        final var priceInTinybars = Math.max(minimumPriceInTinybars, nominalPriceInTinybars);
        return asGasRequirement(priceInTinybars);
    }

    /**
     * After feature flag isGasPrecisionLossFixEnabled is removed, this method should be removed.
     * @deprecated
     */
    @Deprecated(since = "Precision loss fix was implemented in PR #14842", forRemoval = true)
    private long asGasRequirement(final long tinybarPrice) {
        return asGasRequirement(tinybarPrice, tinybarValues.childTransactionTinybarGasPrice());
    }

    /**
     * After feature flag isGasPrecisionLossFixEnabled is removed, this method should be removed.
     * @deprecated
     */
    @Deprecated(since = "Precision loss fix was implemented in PR #14842", forRemoval = true)
    private long asGasRequirement(final long tinybarPrice, final long gasPrice) {
        // We round up to the nearest gas unit, and then add 20% to account for the premium
        // of doing a HAPI operation from inside the EVM
        final var gasRequirement = (tinybarPrice + gasPrice - 1) / gasPrice;
        return gasRequirement + (gasRequirement / 5);
    }
}
