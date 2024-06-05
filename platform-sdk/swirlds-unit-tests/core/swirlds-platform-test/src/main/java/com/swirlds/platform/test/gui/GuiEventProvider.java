package com.swirlds.platform.test.gui;

import com.swirlds.platform.event.GossipEvent;
import java.util.List;

public interface GuiEventProvider {
    List<GossipEvent> generateEvents(final int numberOfEvents);
    void reset();
}
