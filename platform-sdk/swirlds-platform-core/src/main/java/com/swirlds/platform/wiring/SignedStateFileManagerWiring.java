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
                scheduler.buildInputWire("dump state to disk").cast()
        );
    }

    public void bind(final SignedStateFileManager signedStateFileManager){
        saveStateToDisk.bind(signedStateFileManager::saveStateTask);
        dumpStateToDisk.bind(signedStateFileManager::dumpStateTask);
    }

    public OutputWire<StateSavingResult> outputWire(){
        return scheduler.getOutputWire();
    }
}
