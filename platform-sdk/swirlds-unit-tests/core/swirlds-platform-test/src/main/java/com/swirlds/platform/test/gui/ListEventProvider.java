package com.swirlds.platform.test.gui;

import com.swirlds.platform.event.GossipEvent;
import java.util.List;

public class ListEventProvider implements GuiEventProvider{
    private final List<GossipEvent> events;
    private int index;

    public ListEventProvider(final List<GossipEvent> events) {
        this.events = events;
        this.index = 0;
    }

    @Override
    public List<GossipEvent> generateEvents(final int numberOfEvents) {
        if(index >= events.size()) {
            return List.of();
        }
        final int toIndex = Math.min(index + numberOfEvents, events.size());
        final List<GossipEvent> list = events.subList(index, toIndex);
        index = toIndex;
        return list;
    }

    @Override
    public void reset() {
        index = 0;
    }
}
