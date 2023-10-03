package com.swirlds.common.wiring.components;

import java.util.function.Consumer;

public class TopologicalEventSorter implements Consumer<Event> {
    private static final int PRINT_FREQUENCY = 1_000_000;
    private final EventPool eventPool;
    private long lastTimestamp;
    private long checkSum;

    public TopologicalEventSorter(EventPool eventPool) {
        this.eventPool = eventPool;
        this.checkSum = 0;
    }

    @Override
    public void accept(Event event) {
        long number = event.number();
        checkSum += number + 1;     // make 0 contribute to the sum
        if (number % PRINT_FREQUENCY == 0) {
            long curTimestamp = System.currentTimeMillis();
            if (number != 0) {
                System.out.format("Handled %d events, TPS: %d%n", number, PRINT_FREQUENCY * 1000L / (curTimestamp - lastTimestamp));
            }
            lastTimestamp = curTimestamp;
        }
        eventPool.checkin(event);
    }

    public long getCheckSum() {
        return checkSum;
    }
}
