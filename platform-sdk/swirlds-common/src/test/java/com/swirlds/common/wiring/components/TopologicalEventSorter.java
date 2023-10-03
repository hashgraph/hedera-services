package com.swirlds.common.wiring.components;

import java.util.function.Consumer;

public class TopologicalEventSorter implements Consumer<Event> {
    private static final int PRINT_FREQUENCY = 1_000_000;
    private final EventPool eventPool;
    private long lastTimestamp;

    public TopologicalEventSorter(EventPool eventPool) {
        this.eventPool = eventPool;
    }

    @Override
    public void accept(Event event) {
        if (event.number() % PRINT_FREQUENCY == 0) {
            long curTimestamp = System.currentTimeMillis();
            if (event.number() != 0) {
                System.out.format("Handled %d events, TPS: %d%n", event.number(), PRINT_FREQUENCY * 1000L / (curTimestamp - lastTimestamp));
            }
            lastTimestamp = curTimestamp;
        }
        eventPool.checkin(event);
    }
}
