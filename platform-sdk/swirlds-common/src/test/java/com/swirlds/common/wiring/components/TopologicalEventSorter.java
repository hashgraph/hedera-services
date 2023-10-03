package com.swirlds.common.wiring.components;

import java.util.function.Consumer;

public class TopologicalEventSorter implements Consumer<Event> {
    private final EventPool eventPool;
    private final int printFrequency;

    public TopologicalEventSorter(EventPool eventPool, int printFrequency) {
        this.eventPool = eventPool;
        this.printFrequency = printFrequency;
    }

    @Override
    public void accept(Event event) {
        if (event.number() % printFrequency == 0) {
            System.out.println("Handled " + event.number() + " events");
        }
        eventPool.checkin(event);
    }
}
