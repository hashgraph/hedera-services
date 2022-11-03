/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.schedule;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;
import static com.hederahashgraph.api.proto.java.SubType.SCHEDULE_CREATE_CONTRACT_CALL;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduleOpsUsage {
    /* Scheduled transaction ids have the scheduled=true flag set */
    private static final long SCHEDULED_TXN_ID_SIZE = (1L * BASIC_TX_ID_SIZE) + BOOL_SIZE;
    public static final int ONE_MONTH_IN_SECS = 2592000;

    @VisibleForTesting EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;
    @VisibleForTesting Function<ResponseType, QueryUsage> queryEstimateFactory = QueryUsage::new;

    @Inject
    public ScheduleOpsUsage() {
        // Default constructor
    }

    public FeeData scheduleInfoUsage(Query scheduleInfo, ExtantScheduleContext ctx) {
        var op = scheduleInfo.getScheduleGetInfo();

        var estimate = queryEstimateFactory.apply(op.getHeader().getResponseType());
        estimate.addTb(BASIC_ENTITY_ID_SIZE);
        estimate.addRb(ctx.nonBaseRb());

        return estimate.get();
    }

    public FeeData scheduleCreateUsage(
            TransactionBody scheduleCreate,
            SigUsage sigUsage,
            long lifetimeSecs,
            long costIncrementTinyCents,
            int costIncrementBytesPerMonth,
            final long defaultLifeTimeSecs) {
        var op = scheduleCreate.getScheduleCreate();

        var scheduledTxn = op.getScheduledTransactionBody();
        long msgBytesUsed = (long) scheduledTxn.getSerializedSize() + op.getMemoBytes().size();
        if (op.hasPayerAccountID()) {
            msgBytesUsed += BASIC_ENTITY_ID_SIZE;
        }

        var creationCtx =
                ExtantScheduleContext.newBuilder()
                        .setScheduledTxn(scheduledTxn)
                        .setNumSigners(SCHEDULE_ENTITY_SIZES.estimatedScheduleSigs(sigUsage))
                        .setMemo(op.getMemo())
                        .setResolved(false);
        if (op.hasAdminKey()) {
            var adminKey = op.getAdminKey();
            msgBytesUsed += getAccountKeyStorageSize(adminKey);
            creationCtx.setAdminKey(adminKey);
        } else {
            creationCtx.setNoAdminKey();
        }

        var estimate = txnEstimateFactory.get(sigUsage, scheduleCreate, ESTIMATOR_UTILS);
        estimate.addBpt(msgBytesUsed);
        estimate.addRbs(creationCtx.build().nonBaseRb() * lifetimeSecs);

        /* The receipt of a schedule create includes both the id of the created schedule
        and the transaction id to use for querying the record of the scheduled txn. */
        estimate.addNetworkRbs(
                (BASIC_ENTITY_ID_SIZE + SCHEDULED_TXN_ID_SIZE)
                        * USAGE_PROPERTIES.legacyReceiptStorageSecs());

        /* fee for storing long term schedule transactions based on the size of the transaction
        and minutesTillScheduledTime */
        if (lifetimeSecs > defaultLifeTimeSecs) {
            final var addedFeeForLongTerm =
                    addedFeeForLongTermScheduleTxn(
                            lifetimeSecs,
                            costIncrementTinyCents,
                            costIncrementBytesPerMonth,
                            defaultLifeTimeSecs,
                            scheduledTxn.getSerializedSize());
            estimate.addConstant(addedFeeForLongTerm);
        } else {
            estimate.addRbs(scheduledTxn.getSerializedSize() * lifetimeSecs);
        }

        if (scheduledTxn.hasContractCall()) {
            return estimate.get(SCHEDULE_CREATE_CONTRACT_CALL);
        }

        return estimate.get();
    }

    /**
     * Since long term scheduled transactions can be scheduled far from future, adds a fee based on
     * number of minutes until scheduled time and size of schedule transaction. This fee is added
     * only to the transactions scheduled beyond the defaultLifeTimeSecs.
     *
     * <p>Fee calculation is as follows :
     * <li>Charges the base fee for transactions whose lifetimeSecs <= defaultLifeTimeSecs
     * <li>lifetimeSecs > defaultLifeTimeSecs, an additional price of costIncrementTinyCents per
     *     costIncrementBytesPerMonth bytes per month is added to the base price above. The
     *     additional cost is for storing the scheduled transaction in the disk.
     *
     * @param lifetimeSecs seconds until the transaction will be expired
     * @param costIncrementTinyCents additional cost in tiny cents , defaults to 20000000
     * @param costIncrementBytesPerMonth number of bytes per month to increment cost by
     *     costIncrementTinyCents, defaults to 128 bytes
     * @param defaultLifeTimeSecs default lifetime of schedule txn , defaults to 30 mins
     * @param serializedSize size of the schedule txn
     * @return additiona fee to be charged
     */
    private long addedFeeForLongTermScheduleTxn(
            final long lifetimeSecs,
            final long costIncrementTinyCents,
            final int costIncrementBytesPerMonth,
            final long defaultLifeTimeSecs,
            final int serializedSize) {
        if (lifetimeSecs > defaultLifeTimeSecs) {
            final var numerator =
                    Math.multiplyExact(costIncrementTinyCents, lifetimeSecs) * serializedSize;
            final var denominator = costIncrementBytesPerMonth * ONE_MONTH_IN_SECS;
            return ESTIMATOR_UTILS.nonDegenerateDiv(numerator, denominator);
        }
        return 0L;
    }

    public FeeData scheduleSignUsage(
            TransactionBody scheduleSign, SigUsage sigUsage, long scheduleExpiry) {
        var estimate = txnEstimateFactory.get(sigUsage, scheduleSign, ESTIMATOR_UTILS);

        estimate.addBpt(BASIC_ENTITY_ID_SIZE);

        int estNewSigners = SCHEDULE_ENTITY_SIZES.estimatedScheduleSigs(sigUsage);
        long lifetime = ESTIMATOR_UTILS.relativeLifetime(scheduleSign, scheduleExpiry);
        estimate.addRbs(SCHEDULE_ENTITY_SIZES.bytesUsedForSigningKeys(estNewSigners) * lifetime);

        estimate.addNetworkRbs(SCHEDULED_TXN_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());

        return estimate.get();
    }

    public FeeData scheduleDeleteUsage(
            TransactionBody scheduleDelete, SigUsage sigUsage, long scheduleExpiry) {
        var estimate = txnEstimateFactory.get(sigUsage, scheduleDelete, ESTIMATOR_UTILS);

        estimate.addBpt(BASIC_ENTITY_ID_SIZE);

        long lifetime = ESTIMATOR_UTILS.relativeLifetime(scheduleDelete, scheduleExpiry);
        estimate.addRbs(BASIC_RICH_INSTANT_SIZE * lifetime);

        return estimate.get();
    }
}
