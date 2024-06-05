package com.swirlds.platform.test.gui;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.event.generator.GraphGenerator;
import java.util.List;

public class GeneratorEventProvider implements GuiEventProvider{
    private final GraphGenerator<?> graphGenerator;

    public GeneratorEventProvider(final GraphGenerator<?> graphGenerator) {
        this.graphGenerator = graphGenerator;
    }

    @Override
    public List<GossipEvent> generateEvents(final int numberOfEvents) {
        return graphGenerator.generateEvents(numberOfEvents).stream().map(EventImpl::getBaseEvent).toList();
    }

    @Override
    public void reset() {
        graphGenerator.reset();
    }
}
