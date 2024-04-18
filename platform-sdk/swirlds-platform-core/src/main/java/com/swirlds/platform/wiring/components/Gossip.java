package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface Gossip { // TODO

    // TODO control flow wires?

    /**
     * Bind the input wires to the gossip implementation.
     *
     * @param model            the wiring model for this node
     * @param eventInput       the input wire for events, events sent here should be gossiped to the network
     * @param eventWindowInput the input wire for the current event window
     * @param startInput       used to tell gossip to start
     * @param stopInput        used to tell gossip to stop
     * @param eventOutput      the output wire for events received from peers during gossip
     */
    void bind(
            @NonNull WiringModel model,
            @NonNull BindableInputWire<GossipEvent, Void> eventInput,
            @NonNull BindableInputWire<EventWindow, Void> eventWindowInput,
            @NonNull BindableInputWire<NoInput, Void> startInput,
            @NonNull BindableInputWire<NoInput, Void> stopInput,
            @NonNull OutputWire<GossipEvent> eventOutput);

}
