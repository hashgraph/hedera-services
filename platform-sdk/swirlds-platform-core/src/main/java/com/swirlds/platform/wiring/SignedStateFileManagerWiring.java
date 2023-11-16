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


import com.swirlds.common.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * The wiring for the {@link SignedStateFileManager}
 *
 * @param scheduler       the task scheduler
 * @param saveStateToDisk the input wire for saving the state to disk
 * @param dumpStateToDisk the input wire for dumping the state to disk
 */
public record SignedStateFileManagerWiring(
        @NonNull TaskScheduler<StateSavingResult> scheduler,
        @NonNull InputWire<ReservedSignedState, StateSavingResult> saveStateToDisk,
        @NonNull InputWire<StateDumpRequest, Void> dumpStateToDisk) {
    /**
     * Create a new instance of the wiring
     *
     * @param scheduler the task scheduler
     */
    public SignedStateFileManagerWiring(@NonNull final TaskScheduler<StateSavingResult> scheduler) {
        this(
                scheduler,
                scheduler.buildInputWire("save state to disk"),
                scheduler.buildInputWire("dump state to disk").cast());
    }

    /**
     * Bind the wires to the {@link SignedStateFileManager}
     *
     * @param signedStateFileManager the signed state file manager
     */
    public void bind(@NonNull final SignedStateFileManager signedStateFileManager) {
        saveStateToDisk.bind(signedStateFileManager::saveStateTask);
        dumpStateToDisk.bind(signedStateFileManager::dumpStateTask);
    }

    /**
     * Solder the {@link SignedStateFileManager} to the pre-consensus event writer
     * @param preconsensusEventWriter the pre-consensus event writer
     */
    public void solderPces(@NonNull final PreconsensusEventWriter preconsensusEventWriter) {
        scheduler.getOutputWire()
                .buildTransformer(
                        "extract oldestMinimumGenerationOnDisk",
                        StateSavingResult::oldestMinimumGenerationOnDisk)
                .solderTo(
                        "PCES minimum generation to store",
                        preconsensusEventWriter::setMinimumGenerationToStoreUninterruptably);
    }

    public void solderStatusManager(@NonNull final Consumer<StateWrittenToDiskAction> statusConsumer) {
        scheduler.getOutputWire()
                .buildTransformer("to status", ssr -> new StateWrittenToDiskAction(ssr.round()))
                .solderTo("status manager", statusConsumer);
    }

    public void solderAppCommunication(@NonNull final Consumer<StateSavingResult> stateSavingResultConsumer) {
        scheduler.getOutputWire().solderTo("app comm", stateSavingResultConsumer);
    }
}
