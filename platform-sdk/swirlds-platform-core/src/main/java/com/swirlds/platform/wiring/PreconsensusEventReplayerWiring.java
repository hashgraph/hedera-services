package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFiles;
import edu.umd.cs.findbugs.annotations.NonNull;

public record PreconsensusEventReplayerWiring(@NonNull InputWire<PreconsensusEventFiles> preconsensusEventFilesInputWire) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static PreconsensusEventReplayerWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new PreconsensusEventReplayerWiring(taskScheduler.buildInputWire("event files to replay"));
    }

    public void bind(@NonNull final PreconsensusEventReplayerWiring replayIterator) {
        ((BindableInputWire<PreconsensusEventFiles, GossipEvent>) preconsensusEventFilesInputWire).bind(replayIterator::startReplay);
    }
}
