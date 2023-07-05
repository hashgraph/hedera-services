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

package com.swirlds.platform.components.state;

import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A {@link StateToDiskAttemptConsumer} that stores the {@link StateToDiskAttempt}s in a {@link BlockingQueue} for testing purposes
 */
public class TestStateToDiskAttemptConsumer implements StateToDiskAttemptConsumer {
    private final BlockingQueue<StateToDiskAttempt> queue = new LinkedBlockingQueue<>();

    @Override
    public void stateToDiskAttempt(
            @NonNull final SignedState signedState, @NonNull final Path directory, final boolean success) {
        try {
            Objects.requireNonNull(signedState);
            Objects.requireNonNull(directory);
            queue.put(new StateToDiskAttempt(signedState, directory, success));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public @NonNull BlockingQueue<StateToDiskAttempt> getAttemptQueue() {
        return queue;
    }
}
