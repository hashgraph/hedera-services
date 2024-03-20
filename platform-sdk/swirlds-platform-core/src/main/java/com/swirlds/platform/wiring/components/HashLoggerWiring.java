/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.util.HashLogger;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.util.HashLogger}.
 *
 * @param hashLoggerInputWire the input wire for hash logger's reserved signed state to be logged
 */
public record HashLoggerWiring(@NonNull InputWire<ReservedSignedState> hashLoggerInputWire) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static HashLoggerWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new HashLoggerWiring(taskScheduler.buildInputWire("state"));
    }

    /**
     * Bind a hash logger to this wiring.
     *
     * @param hashLogger the hash logger to bind
     */
    public void bind(@NonNull final HashLogger hashLogger) {
        ((BindableInputWire<ReservedSignedState, Void>) hashLoggerInputWire).bindConsumer(hashLogger::logHashes);
    }
}
