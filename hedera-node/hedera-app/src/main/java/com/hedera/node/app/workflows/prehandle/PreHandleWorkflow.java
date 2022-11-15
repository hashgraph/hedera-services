package com.hedera.node.app.workflows.prehandle;

import com.swirlds.common.system.events.Event;

public interface PreHandleWorkflow {
    void start(Event event);
}
