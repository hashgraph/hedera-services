package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.schedule.ScheduleDeleteUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

import java.util.function.BiFunction;

public class ScheduleDeleteResourceUsage implements TxnResourceUsageEstimator {

    static BiFunction<TransactionBody, SigUsage, ScheduleDeleteUsage> factory = ScheduleDeleteUsage::newEstimate;

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasScheduleDelete();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
        var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        return factory.apply(txn, sigUsage).get();
    }
}
