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
        return txn.hasScheduleSign();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws InvalidTxBodyException {
        // TODO: Not in this scope
        throw new UnsupportedOperationException();
    }
}
