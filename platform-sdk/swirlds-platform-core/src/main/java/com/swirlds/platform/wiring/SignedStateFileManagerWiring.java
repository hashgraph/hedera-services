/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.transformers.WireFilter;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.system.status.actions.StateWrittenToDiskAction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The wiring for the {@link SignedStateFileManager}
 *
 * @param saveStateToDisk                         the input wire for saving the state to disk
 * @param saveToDiskFilter                        the input wire that filters out states that should not be saved
 * @param dumpStateToDisk                         the input wire for dumping the state to disk
 * @param stateSavingResultOutputWire             the output wire for the state saving result
 * @param oldestMinimumGenerationOnDiskOutputWire the output wire for the oldest minimum generation on disk
 * @param stateWrittenToDiskOutputWire            the output wire for the state written to disk action
 */
public record SignedStateFileManagerWiring(
        @NonNull BindableInputWire<ReservedSignedState, StateSavingResult> saveStateToDisk,
        @NonNull InputWire<ReservedSignedState> saveToDiskFilter,
        @NonNull InputWire<StateDumpRequest> dumpStateToDisk,
        @NonNull OutputWire<StateSavingResult> stateSavingResultOutputWire,
        @NonNull OutputWire<Long> oldestMinimumGenerationOnDiskOutputWire,
        @NonNull OutputWire<StateWrittenToDiskAction> stateWrittenToDiskOutputWire) {
    /**
     * Create a new instance of this wiring
     *
     * @param scheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static SignedStateFileManagerWiring create(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<StateSavingResult> scheduler) {
        final WireFilter<ReservedSignedState> saveToDiskFilter =
                new WireFilter<>(model, "saveToDiskFilter", "states", rs -> {
                    if (rs.get().isStateToSave()) {
                        return true;
                    }
                    rs.close();
                    return false;
                });
        final BindableInputWire<ReservedSignedState, StateSavingResult> saveStateToDisk =
                scheduler.buildInputWire("states to save");
        saveToDiskFilter.getOutputWire().solderTo(saveStateToDisk);
        return new SignedStateFileManagerWiring(
                saveStateToDisk,
                saveToDiskFilter.getInputWire(),
                scheduler.buildInputWire("dump state to disk"),
                scheduler.getOutputWire(),
                scheduler
                        .getOutputWire()
                        .buildTransformer(
                                "extractOldestMinimumGenerationOnDisk",
                                "state saving result",
                                StateSavingResult::oldestMinimumGenerationOnDisk),
                scheduler
                        .getOutputWire()
                        .buildTransformer(
                                "toStateWrittenToDiskAction",
                                "state saving result",
                                ssr -> new StateWrittenToDiskAction(ssr.round(), ssr.freezeState())));
    }

    /**
     * Bind the wires to the {@link SignedStateFileManager}
     *
     * @param signedStateFileManager the signed state file manager
     */
    public void bind(@NonNull final SignedStateFileManager signedStateFileManager) {
        saveStateToDisk.bind(signedStateFileManager::saveStateTask);
        ((BindableInputWire<StateDumpRequest, Void>) dumpStateToDisk)
                .bindConsumer(signedStateFileManager::dumpStateTask);
    }
}
