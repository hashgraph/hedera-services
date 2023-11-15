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

import com.swirlds.common.wiring.InputWire;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.TaskScheduler;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.state.signed.StateSavingResult;

public record SignedStateFileManagerWiring(
        TaskScheduler<StateSavingResult> scheduler,
        InputWire<ReservedSignedState, StateSavingResult> saveStateToDisk,
        InputWire<StateDumpRequest, Void> dumpStateToDisk) {
    public SignedStateFileManagerWiring(final TaskScheduler<StateSavingResult> scheduler) {
        this(
                scheduler,
                scheduler.buildInputWire("save state to disk"),
                scheduler.buildInputWire("dump state to disk").cast());
    }

    public void bind(final SignedStateFileManager signedStateFileManager) {
        saveStateToDisk.bind(signedStateFileManager::saveStateTask);
        dumpStateToDisk.bind(signedStateFileManager::dumpStateTask);
    }

    public OutputWire<StateSavingResult> outputWire() {
        return scheduler.getOutputWire();
    }
}
