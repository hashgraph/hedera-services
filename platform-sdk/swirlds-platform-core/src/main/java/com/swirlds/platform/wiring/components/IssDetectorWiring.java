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
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Wiring for the {@link IssDetector}.
 *
 * @param endOfPcesReplay       the input wire for the end of the PCES replay
 * @param stateAndRoundInput    the input wire for completed rounds and their corresponding states
 * @param overridingState       the input wire for overriding states
 * @param issNotificationOutput the output wire for ISS notifications
 */
public record IssDetectorWiring(
        @NonNull InputWire<NoInput> endOfPcesReplay,
        @NonNull InputWire<StateAndRound> stateAndRoundInput,
        @NonNull InputWire<ReservedSignedState> overridingState,
        @NonNull OutputWire<IssNotification> issNotificationOutput) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler that will detect ISSs
     * @return the new wiring instance
     */
    @NonNull
    public static IssDetectorWiring create(@NonNull final TaskScheduler<List<IssNotification>> taskScheduler) {

        return new IssDetectorWiring(
                taskScheduler.buildInputWire("end of PCES replay"),
                taskScheduler.buildInputWire("stateAndRound"),
                taskScheduler.buildInputWire("overriding state"),
                taskScheduler.getOutputWire().buildSplitter("issNotificationSplitter", "iss notifications"));
    }

    /**
     * Bind the given ISS detector to this wiring.
     *
     * @param issDetector the ISS detector
     */
    public void bind(@NonNull final IssDetector issDetector) {
        ((BindableInputWire<NoInput, Void>) endOfPcesReplay).bindConsumer(issDetector::signalEndOfPreconsensusReplay);
        ((BindableInputWire<StateAndRound, List<IssNotification>>) stateAndRoundInput)
                .bind(issDetector::handleStateAndRound);
        ((BindableInputWire<ReservedSignedState, List<IssNotification>>) overridingState)
                .bind(issDetector::overridingState);
    }
}
