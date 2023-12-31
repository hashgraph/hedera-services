package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.ProbationBuffer;
import edu.umd.cs.findbugs.annotations.NonNull;

public record ProbationBufferWiring(@NonNull InputWire<GossipEvent> eventInput,
                                    @NonNull OutputWire<GossipEvent> eventOutput,
                                    @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static ProbationBufferWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new ProbationBufferWiring(
                taskScheduler.buildInputWire("events in probation"),
                taskScheduler.getOutputWire(),
                taskScheduler::flush);
    }

    /**
     * Bind a probation buffer to this wiring.
     *
     * @param probationBuffer the probation buffer to bind
     */
    public void bind(@NonNull final ProbationBuffer probationBuffer) {
        ((BindableInputWire<GossipEvent, GossipEvent>) eventInput).bind(probationBuffer::addEvent);
    }
}
