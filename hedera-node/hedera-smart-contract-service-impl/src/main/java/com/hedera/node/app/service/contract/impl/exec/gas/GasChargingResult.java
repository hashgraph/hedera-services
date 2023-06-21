package com.hedera.node.app.service.contract.impl.exec.gas;

import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record GasChargingResult(
        @NonNull HederaEvmAccount sender,
        @Nullable HederaEvmAccount relayer,
        long allowanceUsed) {
}
