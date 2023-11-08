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

package com.hedera.node.app.fees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Creates {@link FeeCalculator} instances based on the current fee schedule. Whenever the fee schedule is updated,
 * the {@link #update(Bytes)} method should be called. Until updated, the fee schedule will be empty, which will
 * manifest as errors in attempting to execute a given transaction (a transaction without an entry in the fee schedule
 * cannot be executed).
 */
@Singleton
public final class FeeManager {
    private static final Logger logger = LogManager.getLogger(FeeManager.class);

    private record Entry(HederaFunctionality function, SubType subType) {}

    private static final long DEFAULT_FEE = 100_000L;

    private static final FeeComponents DEFAULT_FEE_COMPONENTS =
            FeeComponents.newBuilder().min(DEFAULT_FEE).max(DEFAULT_FEE).build();
    private static final FeeData DEFAULT_FEE_DATA = FeeData.newBuilder()
            .networkdata(DEFAULT_FEE_COMPONENTS)
            .nodedata(DEFAULT_FEE_COMPONENTS)
            .servicedata(DEFAULT_FEE_COMPONENTS)
            .build();

    /** The current fee schedule, cached for speed. */
    private Map<Entry, FeeData> currentFeeDataMap = Collections.emptyMap();
    /** The next fee schedule, cached for speed. */
    private Map<Entry, FeeData> nextFeeDataMap = Collections.emptyMap();
    /** The expiration time of the "current" fee schedule, in consensus seconds since the epoch, cached for speed. */
    private long currentScheduleExpirationSeconds;
    /** The exchange rate manager to use for the current rate */
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public FeeManager(@NonNull final ExchangeRateManager exchangeRateManager) {
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
    }

    /**
     * Updates the fee schedule based on the given file content. THIS MUST BE CALLED ON THE HANDLE THREAD!!
     *
     * @param bytes The new fee schedule file content.
     */
    public ResponseCodeEnum update(@NonNull final Bytes bytes) {
        // Parse the current and next fee schedules
        final CurrentAndNextFeeSchedule schedules;
        try {
            schedules = CurrentAndNextFeeSchedule.PROTOBUF.parse(bytes.toReadableSequentialData());
        } catch (final BufferUnderflowException | IOException ex) {
            return ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
        }

        // Get the current schedule
        var currentSchedule = schedules.currentFeeSchedule();
        if (currentSchedule == null) {
            // If there is no current schedule, then we default to the default schedule. Since the default
            // schedule is completely empty, this will effectively disable the handling of any transactions,
            // since we don't know what to charge for them.
            logger.warn("Unable to parse current fee schedule, will default to an empty schedule, effectively"
                    + "disabling all transactions.");
            currentSchedule = FeeSchedule.DEFAULT;
        }

        // Populate the map of HederaFunctionality -> FeeData for the current schedule
        this.currentFeeDataMap = new HashMap<>();
        if (currentSchedule.hasTransactionFeeSchedule()) {
            populateFeeDataMap(currentFeeDataMap, currentSchedule.transactionFeeScheduleOrThrow());
        } else {
            logger.warn("The current fee schedule is missing transaction information, effectively disabling all"
                    + "transactions.");
        }

        // Get the expiration time of the current schedule
        if (currentSchedule.hasExpiryTime()) {
            this.currentScheduleExpirationSeconds =
                    currentSchedule.expiryTimeOrThrow().seconds();
        } else {
            // If we don't have an expiration time, then we default to 0, which will effectively expire the
            // current schedule immediately. This is the safest option.
            logger.warn("The current fee schedule has no expiry time, defaulting to 0, effectively expiring it"
                    + "immediately");
            this.currentScheduleExpirationSeconds = 0;
        }

        // Get the next schedule
        var nextSchedule = schedules.nextFeeSchedule();
        if (nextSchedule == null) {
            // If there is no next schedule, then we default to the current schedule. If we didn't have a current
            // schedule either, then basically we have an empty schedule with an expiration time of 0, which will
            // still get used since we continue to use the next schedule even if the expiration time has passed.
            logger.warn("Unable to parse next fee schedule, will default to the current fee schedule.");
            nextFeeDataMap = new HashMap<>(currentFeeDataMap);
        } else {
            // Populate the map of HederaFunctionality -> FeeData for the current schedule
            this.nextFeeDataMap = new HashMap<>();
            if (nextSchedule.hasTransactionFeeSchedule()) {
                populateFeeDataMap(nextFeeDataMap, nextSchedule.transactionFeeScheduleOrThrow());
            } else {
                logger.warn("The next fee schedule is missing transaction information, effectively disabling all"
                        + "transactions once it becomes active.");
            }
        }

        return SUCCESS;
    }

    /**
     * Create a {@link FeeCalculator} for the given transaction and its details.
     */
    @NonNull
    public FeeCalculator createFeeCalculator(
            @Nullable final TransactionBody txBody,
            @Nullable final Key payerKey,
            @Nullable final HederaFunctionality functionality,
            final int numVerifications,
            final int signatureMapSize,
            @NonNull final Instant consensusTime,
            @NonNull final SubType subType,
            final boolean isInternalDispatch) {

        if (txBody == null || payerKey == null || functionality == null) {
            return NoOpFeeCalculator.INSTANCE;
        }

        // Determine which fee schedule to use, based on the consensus time
        // If it is not known, that is, if we have no fee data for that transaction, then we MUST NOT execute that
        // transaction! We will not be able to charge appropriately for it.
        final var feeData = getFeeData(functionality, consensusTime, subType);
        if (feeData == null) {
            throw new IllegalStateException("No fee data found for transaction type " + functionality);
        }

        // Create the fee calculator
        return new FeeCalculatorImpl(
                txBody,
                payerKey,
                numVerifications,
                signatureMapSize,
                feeData,
                exchangeRateManager.activeRate(consensusTime),
                isInternalDispatch);
    }

    @NonNull
    public FeeCalculator createFeeCalculator(
            @NonNull final HederaFunctionality functionality, @NonNull final Instant consensusTime) {
        // Determine which fee schedule to use, based on the consensus time
        final var feeData = getFeeData(functionality, consensusTime, SubType.DEFAULT);

        // Create the fee calculator
        return new FeeCalculatorImpl(feeData, exchangeRateManager.activeRate(consensusTime));
    }

    /**
     * Looks up the fee data for the given transaction and its details.
     */
    @NonNull
    public FeeData getFeeData(
            @NonNull HederaFunctionality functionality, @NonNull Instant consensusTime, @NonNull SubType subType) {
        final var feeDataMap =
                consensusTime.getEpochSecond() > currentScheduleExpirationSeconds ? nextFeeDataMap : currentFeeDataMap;

        // Now, lookup the fee data for the transaction type.
        final var result = feeDataMap.get(new Entry(functionality, subType));
        if (result == null) {
            logger.warn("Using default usage prices to calculate fees for {}!", functionality);
            return DEFAULT_FEE_DATA;
        }
        return result;
    }

    /**
     * Used during {@link #update(Bytes)} to populate the fee data map based on the configuration.
     * @param feeDataMap The map to populate.
     * @param feeSchedule The fee schedule to use.
     */
    private void populateFeeDataMap(
            @NonNull final Map<Entry, FeeData> feeDataMap, @NonNull final List<TransactionFeeSchedule> feeSchedule) {
        feeSchedule.forEach(t -> {
            if (t.hasFees()) {
                for (final var feeData : t.feesOrThrow()) {
                    feeDataMap.put(new Entry(t.hederaFunctionality(), feeData.subType()), feeData);
                }
            } else if (t.hasFeeData()) {
                feeDataMap.put(new Entry(t.hederaFunctionality(), SubType.DEFAULT), t.feeDataOrThrow());
            } else {
                logger.warn(
                        "Neither `fees` nor `feeData` specified for transaction type {}, ignoring it.",
                        t.hederaFunctionality());
            }
        });
    }
}
