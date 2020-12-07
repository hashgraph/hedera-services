package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

public class ScheduleSignResourceUsage implements TxnResourceUsageEstimator {
    @Override
    public boolean applicableTo(TransactionBody txn) {
        return false;
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws InvalidTxBodyException {
        return null;
    }

    @Override
    public long relativeLifetime(TransactionBody txn, long expiry) {
        return 0;
    }
}
