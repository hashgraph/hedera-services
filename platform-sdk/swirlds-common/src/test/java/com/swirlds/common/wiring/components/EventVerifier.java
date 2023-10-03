package com.swirlds.common.wiring.components;

import java.util.function.Consumer;

public class EventVerifier implements Consumer<Event> {
    private final Consumer<Event> output;

    public EventVerifier(Consumer<Event> output) {
        this.output = output;
    }

    @Override
    public void accept(Event event) {
        // Pretend like we did verification by sleeping for a few microseconds
        busySleep(2000);
        output.accept(event);
    }

    public static void busySleep(long nanos) {
        long elapsed;
        final long startTime = System.nanoTime();
        do {
            elapsed = System.nanoTime() - startTime;
        } while (elapsed < nanos);
    }
}
