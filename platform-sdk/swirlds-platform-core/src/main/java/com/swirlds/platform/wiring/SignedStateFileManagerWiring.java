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

package com.swirlds.platform.wiring;

import com.swirlds.common.system.status.PlatformStatusManager;
import com.swirlds.common.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The wiring for the {@link SignedStateFileManager}
 *
 * @param outputWire      the output wire
 * @param saveStateToDisk the input wire for saving the state to disk
 * @param dumpStateToDisk the input wire for dumping the state to disk
 */
public record SignedStateFileManagerWiring(
        @NonNull OutputWire<StateSavingResult> outputWire,
        @NonNull InputWire<ReservedSignedState> saveStateToDisk,
        @NonNull InputWire<StateDumpRequest> dumpStateToDisk) {
    /**
     * Create a new instance of the wiring
     *
     * @param scheduler the task scheduler
     */
    public SignedStateFileManagerWiring(@NonNull final TaskScheduler<StateSavingResult> scheduler) {
        this(
                scheduler.getOutputWire(),
                scheduler.buildInputWire("save state to disk"),
                scheduler.buildInputWire("dump state to disk").cast());
    }

    /**
     * Bind the wires to the {@link SignedStateFileManager}
     *
     * @param signedStateFileManager the signed state file manager
     */
    public void bind(@NonNull final SignedStateFileManager signedStateFileManager) {
        ((BindableInputWire<ReservedSignedState, StateSavingResult>) saveStateToDisk)
                .bind(signedStateFileManager::saveStateTask);
        ((BindableInputWire<StateDumpRequest, Void>) dumpStateToDisk).bind(signedStateFileManager::dumpStateTask);
    }

    /**
     * Solder the {@link SignedStateFileManager} to the pre-consensus event writer
     *
     * @param preconsensusEventWriter the pre-consensus event writer
     */
    public void solderPces(@NonNull final PreconsensusEventWriter preconsensusEventWriter) {
        outputWire
                .buildTransformer(
                        "extract oldestMinimumGenerationOnDisk", StateSavingResult::oldestMinimumGenerationOnDisk)
                .solderTo(
                        "PCES minimum generation to store",
                        preconsensusEventWriter::setMinimumGenerationToStoreUninterruptably);
    }

    /**
     * Solder the {@link SignedStateFileManager} to the platform status manager
     *
     * @param statusManager the platform status manager
     */
    public void solderStatusManager(@NonNull final PlatformStatusManager statusManager) {
        outputWire
                .buildTransformer("to StateWrittenToDiskAction", ssr -> new StateWrittenToDiskAction(ssr.round()))
                .solderTo("status manager", statusManager::submitStatusAction);
    }

    /**
     * Solder the {@link SignedStateFileManager} to the app communication component
     *
     * @param appCommunicationComponent the app communication component
     */
    public void solderAppCommunication(@NonNull final AppCommunicationComponent appCommunicationComponent) {
        outputWire.solderTo("app communication", appCommunicationComponent::stateSavedToDisk);
    }
}
