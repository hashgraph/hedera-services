package com.swirlds.platform.test.gui;

import com.swirlds.platform.event.PlatformEvent;
import java.util.List;

public interface GuiEventProvider {
    List<PlatformEvent> generateEvents(final int numberOfEvents);
    void reset();
}
