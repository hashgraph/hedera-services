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
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link LatestCompleteStateNotifier}.
 *
 * @param completeStateNotificationInputWire  the input wire for the latest complete state notifier
 */
public record LatestCompleteStateNotifierWiring(
        @NonNull InputWire<ReservedSignedState> completeStateNotificationInputWire) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static LatestCompleteStateNotifierWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new LatestCompleteStateNotifierWiring(taskScheduler.buildInputWire("complete states"));
    }

    /**
     * Bind a latest complete state handler to this wiring.
     *
     * @param latestCompleteStateNotifier the latest complete state notifier to bind
     */
    public void bind(@NonNull final LatestCompleteStateNotifier latestCompleteStateNotifier) {
        ((BindableInputWire<ReservedSignedState, Void>) completeStateNotificationInputWire)
                .bind(latestCompleteStateNotifier::latestCompleteStateHandler);
    }
}
