package com.swirlds.platform.wiring;

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The wiring for the {@link PcesReplayer}.
 *
 * @param pcesIteratorInputWire       the input wire for the iterator of events to replay
 * @param doneStreamingPcesOutputWire the output wire which indicates that PCES replay is complete
 */
public record PcesReplayerWiring(
        @NonNull InputWire<IOIterator<GossipEvent>> pcesIteratorInputWire,
        @NonNull OutputWire<DoneStreamingPcesTrigger> doneStreamingPcesOutputWire) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static PcesReplayerWiring create(@NonNull final TaskScheduler<DoneStreamingPcesTrigger> taskScheduler) {
        return new PcesReplayerWiring(taskScheduler.buildInputWire("event files to replay"), taskScheduler.buildSecondaryOutputWire());
    }

    /**
     * Bind the given {@link PcesReplayer} to this wiring.
     *
     * @param pcesReplayer the replayer to bind
     */
    public void bind(@NonNull final PcesReplayer pcesReplayer) {
        ((BindableInputWire<IOIterator<GossipEvent>, DoneStreamingPcesTrigger>) pcesIteratorInputWire).bind(pcesReplayer::replayPces);
    }
}
