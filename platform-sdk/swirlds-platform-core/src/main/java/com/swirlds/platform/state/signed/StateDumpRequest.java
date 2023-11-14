package com.swirlds.platform.state.signed;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * @param reservedSignedState the reserved signed state to be written to disk
 * @param finishedCallback    a function that is called after state writing is complete
 */
public record StateDumpRequest(
        @NonNull ReservedSignedState reservedSignedState,
        @Nullable Consumer<Boolean> finishedCallback) {
}
