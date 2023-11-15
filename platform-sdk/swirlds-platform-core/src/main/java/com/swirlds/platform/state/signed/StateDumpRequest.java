/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.state.signed;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.interrupt.Uninterruptable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CountDownLatch;

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
                        "interrupted while waiting for state dump to complete, state dump may not be completed"));
    }
}
