package com.swirlds.common.wiring.components;

import java.util.Stack;

public final class EventPool {
    private final Stack<Event> pool = new Stack<>();

    public EventPool() {
        for (int i = 0; i < 1000; i++) {
            pool.add(new Event());
        }
    }

    public synchronized Event checkout(int number) {
        if (pool.isEmpty()) {
            final var event = new Event();
            event.reset(number);
            return event;
        } else {
            final var event = pool.pop();
            event.reset(number);
            return event;
        }
    }

    public void checkin(Event event) {
        pool.push(event);
    }
}
