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
package com.hedera.services.fees.calculation.schedule.txns;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.schedule.ScheduleOpsUsage.ONE_MONTH_IN_SECS;
import static com.hederahashgraph.fee.FeeUtils.clampedMultiply;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.FeeObject;
import com.hederahashgraph.fee.SigValueObj;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduleCreateResourceUsage implements TxnResourceUsageEstimator {
    private final ScheduleOpsUsage scheduleOpsUsage;
    private final GlobalDynamicProperties dynamicProperties;
    private long lifetimeSecs;

    @Inject
    public ScheduleCreateResourceUsage(
            ScheduleOpsUsage scheduleOpsUsage, GlobalDynamicProperties dynamicProperties) {
        this.scheduleOpsUsage = scheduleOpsUsage;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasScheduleCreate();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view)
            throws InvalidTxBodyException {
        var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());

        lifetimeSecs = calculateLifeTimeSecs(txn);
        final long defaultLifeTimeSecs = dynamicProperties.scheduledTxExpiryTimeSecs();
        return scheduleOpsUsage.scheduleCreateUsage(
                txn, sigUsage, lifetimeSecs, defaultLifeTimeSecs);
    }

    @Override
    public boolean hasSecondaryFees() {
        return true;
    }

    /**
     * Since long term scheduled transactions can be scheduled far from future, adds a fee based on
     * number of minutes until scheduled time and size of schedule transaction. This fee is added
     * only to the transactions scheduled beyond the defaultLifeTimeSecs.
     *
     * <p>Fee calculation is as follows : If lifetimeSecs &lt;= defaultLifeTimeSecs, no additional
     * fee is charged If lifetimeSecs &gt; defaultLifeTimeSecs, an additional price is charged based
     * on serialized size of the scheduled transaction and the expiration tiem is calculated.
     *
     * <p>For example if the defaultLifeTimeSecs is {@code 1800}, priceIncrement is {@code 20000000}
     * tinycents, and secondaryFeeBytesPerMonth is {@code 128} bytes, then for a scheduled
     * transaction that is scheduled to execute anytime after 1800 secs, an extra fee of $0.002 for
     * 128 bytes/month is charged.
     *
     * @param txn schedule create transaction body
     * @return fee object for additional fee
     */
    @Override
    public FeeObject secondaryFeesFor(final TransactionBody txn) {
        final long secondaryFeeTinyCents = dynamicProperties.scheduleTxSecondaryFee();
        final int secondaryFeeBPM = dynamicProperties.scheduleTxSecondaryFeeBytesPerMonth();
        final long defaultLifeTimeSecs = dynamicProperties.scheduledTxExpiryTimeSecs();

        if (lifetimeSecs > defaultLifeTimeSecs) {
            final var serializedSize =
                    txn.getScheduleCreate().getScheduledTransactionBody().getSerializedSize();
            final var numerator =
                    clampedMultiply(
                            clampedMultiply(secondaryFeeTinyCents, lifetimeSecs), serializedSize);
            final var denominator = secondaryFeeBPM * ONE_MONTH_IN_SECS;
            final var additionalServiceFee =
                    ESTIMATOR_UTILS.nonDegenerateDiv(numerator, denominator);
            return new FeeObject(0, 0, additionalServiceFee);
        }
        return new FeeObject(0, 0, 0);
    }

    private long calculateLifeTimeSecs(final TransactionBody txn) {
        final var op = txn.getScheduleCreate();
        final var validStart = txn.getTransactionID().getTransactionValidStart().getSeconds();
        if (op.hasExpirationTime() && dynamicProperties.schedulingLongTermEnabled()) {
            return Math.max(0L, op.getExpirationTime().getSeconds() - validStart);
        }
        return dynamicProperties.scheduledTxExpiryTimeSecs();
    }
}
