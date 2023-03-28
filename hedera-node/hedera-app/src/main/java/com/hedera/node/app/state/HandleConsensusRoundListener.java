package com.hedera.node.app.state;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import edu.umd.cs.findbugs.annotations.NonNull;

@FunctionalInterface
public interface HandleConsensusRoundListener {
    void accept(@NonNull Round round, @NonNull SwirldDualState dualState, @NonNull HederaState state);
}
