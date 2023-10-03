package com.swirlds.common.wiring.components;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class EventPool {
    private static final int CAPACITY = 10000;
    private final BlockingQueue<Event> pool = new ArrayBlockingQueue<>(CAPACITY);

    public EventPool() {
        for (int i = 0; i < CAPACITY; i++) {
            pool.add(new Event());
        }
    }

    public Event checkout(long number) {
        try {
            Event event = pool.take();
            event.reset(number);
            return event;
        } catch (InterruptedException iex) {
            throw new RuntimeException(iex);
        }
    }

    public void checkin(Event event) {
        pool.add(event);
    }
}
