package com.hedera.node.app.state.merkle;

import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A callback that is invoked when the state is initialized.
 */
public interface OnStateInitialized {
    void onStateInitialized(
            @NonNull MerkleHederaState state,
            @NonNull Platform platform,
            @NonNull SwirldDualState dualState,
            @NonNull InitTrigger trigger,
            @NonNull SoftwareVersion deserializedVersion);
}
