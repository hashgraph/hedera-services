package com.swirlds.platform.state.signed;

import com.swirlds.base.state.Startable;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.interrupt.Uninterruptable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * @param reservedSignedState the reserved signed state to be written to disk
 * @param finishedCallback    called after state writing is complete
 */
public record StateDumpRequest(
        @NonNull ReservedSignedState reservedSignedState,
        @NonNull Runnable finishedCallback,
        @NonNull Runnable waitForFinished) {
    public static StateDumpRequest create(@NonNull final ReservedSignedState reservedSignedState) {
        final CountDownLatch latch = new CountDownLatch(1);
        final InterruptableRunnable await = latch::await;
        return new StateDumpRequest(
                reservedSignedState,
                latch::countDown,
                () -> Uninterruptable.abortAndLogIfInterrupted(
                        await,
                        "interrupted while waiting for state dump to complete, state dump may not be completed")
        );
    }
}
