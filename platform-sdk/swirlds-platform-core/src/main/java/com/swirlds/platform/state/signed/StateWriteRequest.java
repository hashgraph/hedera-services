package com.swirlds.platform.state.signed;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * @param reservedSignedState the reserved signed state to be written to disk
 * @param finishedCallback    a function that is called after state writing is complete
 * @param outOfBand           whether this state has been requested to be written out-of-band
 */
public record StateWriteRequest(
        @NonNull ReservedSignedState reservedSignedState,
        @Nullable Consumer<Boolean> finishedCallback,
        boolean outOfBand) {
}
