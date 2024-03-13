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
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.system.state.notifications.IssNotification;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link IssHandler}
 *
 * @param issNotificationInput the input wire for ISS notifications
 */
public record IssHandlerWiring(@NonNull InputWire<IssNotification> issNotificationInput) {
    /**
     * Create a new instance of this wiring
     *
     * @param taskScheduler the task scheduler to use
     * @return the new instance
     */
    @NonNull
    public static IssHandlerWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new IssHandlerWiring(taskScheduler.buildInputWire("iss notification"));
    }

    /**
     * Bind the input wire to the given handler
     *
     * @param issHandler the handler to bind to
     */
    public void bind(@NonNull final IssHandler issHandler) {
        ((BindableInputWire<IssNotification, Void>) issNotificationInput).bindConsumer(issHandler::issObserved);
    }
}
