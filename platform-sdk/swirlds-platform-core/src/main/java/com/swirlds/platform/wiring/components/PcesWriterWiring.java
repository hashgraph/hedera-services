package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.DoneStreamingPcesTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;

public record PcesWriterWiring(@NonNull InputWire<DoneStreamingPcesTrigger> doneStreamingPcesInputWire,
                               @NonNull InputWire<GossipEvent> eventInputWire,
                               @NonNull InputWire<Long> discontinuityInputWire,
                               @NonNull InputWire<Long> minimumGenerationNonAncientInput,
                               @NonNull InputWire<Long> minimumGenerationToStoreInputWire,
                               @NonNull Runnable flushRunnable) {

}
