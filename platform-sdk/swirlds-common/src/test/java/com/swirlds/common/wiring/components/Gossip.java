package com.swirlds.common.wiring.components;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A quick and dirty simulation of gossip :-). It will generate events like crazy.
 */
public class Gossip {
    private final EventPool eventPool;
    private final Consumer<Event> toEventVerifier;
    private final AtomicInteger eventNumber = new AtomicInteger(0);

    public Gossip(EventPool eventPool, Consumer<Event> toEventVerifier) {
        this.toEventVerifier = toEventVerifier;
        this.eventPool = eventPool;
    }

    public void start() {
        // I'm testing at such high event rates that I cannot just test the above events per second directly. So I'm
        // going to take whatever eventsPerSecond is, split it into 1000 pieces, and for each piece loop and create
        // as many events as necessary to fill that millisecond.
        final var singleThreadExecutor = Executors.newSingleThreadScheduledExecutor();
        singleThreadExecutor.submit(this::generateEvents);
    }

    private void generateEvents() {
        for (;;) {
            final var event = eventPool.checkout(eventNumber.getAndIncrement());
            toEventVerifier.accept(event);
        }
    }
}
