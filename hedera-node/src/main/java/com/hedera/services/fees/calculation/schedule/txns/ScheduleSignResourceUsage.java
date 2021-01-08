package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleSignUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

import java.util.function.BiFunction;

public class ScheduleSignResourceUsage implements TxnResourceUsageEstimator {

    static BiFunction<TransactionBody, SigUsage, ScheduleSignUsage> factory = ScheduleSignUsage::newEstimate;
    private final GlobalDynamicProperties dynamicProperties;

    public ScheduleSignResourceUsage(GlobalDynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasScheduleSign();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
        var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        var estimate = factory.apply(txn, sigUsage);
        return estimate.givenScheduledTxExpirationTimeSecs(dynamicProperties.scheduledTxExpiryTimeSecs()).get();
    }
}
