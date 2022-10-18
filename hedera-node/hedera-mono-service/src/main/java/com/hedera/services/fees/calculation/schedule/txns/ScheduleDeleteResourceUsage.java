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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduleDeleteResourceUsage implements TxnResourceUsageEstimator {
    private final ScheduleOpsUsage scheduleOpsUsage;
    private final GlobalDynamicProperties properties;

    @Inject
    public ScheduleDeleteResourceUsage(
            ScheduleOpsUsage scheduleOpsUsage, GlobalDynamicProperties properties) {
        this.scheduleOpsUsage = scheduleOpsUsage;
        this.properties = properties;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasScheduleDelete();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view)
            throws InvalidTxBodyException {
        var op = txn.getScheduleDelete();
        var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());

        var optionalInfo = view.infoForSchedule(op.getScheduleID());
        if (optionalInfo.isPresent()) {
            var info = optionalInfo.get();
            return scheduleOpsUsage.scheduleDeleteUsage(
                    txn, sigUsage, info.getExpirationTime().getSeconds());
        } else {
            long latestExpiry =
                    txn.getTransactionID().getTransactionValidStart().getSeconds()
                            + properties.scheduledTxExpiryTimeSecs();
            return scheduleOpsUsage.scheduleDeleteUsage(txn, sigUsage, latestExpiry);
        }
    }
}
