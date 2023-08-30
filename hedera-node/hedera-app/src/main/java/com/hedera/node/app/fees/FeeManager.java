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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    public void update(@NonNull final Bytes bytes) {
        try {
            // Parse the current and next fee schedules
            final var schedules = CurrentAndNextFeeSchedule.PROTOBUF.parse(bytes.toReadableSequentialData());

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
        } catch (final Exception e) {
            logger.warn("Unable to parse fee schedule file", e);
        }
    }

    /**
     * Create a {@link FeeCalculator} for the given transaction and its details.
     */
    @NonNull
    public FeeCalculator createFeeCalculator(
            @NonNull final TransactionInfo txInfo,
            @NonNull final Key payerKey,
            final int numVerifications,
            @NonNull final Instant consensusTime,
            @NonNull final SubType subType) {

        // Determine which fee schedule to use, based on the consensus time
        final var feeData = getFeeData(txInfo, consensusTime, subType);

        // Create the fee calculator
        return new FeeCalculatorImpl(
                txInfo, payerKey, numVerifications, feeData, exchangeRateManager.activeRate(consensusTime));
    }

    /**
     * Looks up the fee data for the given transaction and its details.
     */
    @NonNull
    private FeeData getFeeData(
            @NonNull TransactionInfo txInfo, @NonNull Instant consensusTime, @NonNull SubType subType) {
        final var feeDataMap =
                consensusTime.getEpochSecond() > currentScheduleExpirationSeconds ? nextFeeDataMap : currentFeeDataMap;

        // Now, lookup the fee data for the transaction type. If it is not known, that is, if we have no fee data for
        // that transaction, then we MUST NOT execute that transaction! We will not be able to charge appropriately
        // for it.
        final var feeData = feeDataMap.get(new Entry(txInfo.functionality(), subType));
        if (feeData == null) {
            throw new HandleException(ResponseCodeEnum.NOT_SUPPORTED);
        }
        return feeData;
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
