package com.hedera.evm.fees;

import com.hedera.evm.context.primitives.StateView;
import com.hedera.evm.utils.accessors.TxnAccessor;
import com.hederahashgraph.fee.FeeObject;
import org.apache.tuweni.bytes.Bytes;

import java.time.Instant;

public interface FeeCalculator {
    //FUTURE WORK - alternative to JKey
    FeeObject computeFee(
            TxnAccessor accessor, Bytes[] payerKey, StateView view, Instant consensusTime);
}
