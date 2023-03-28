package com.hedera.node.app.state;

import com.swirlds.common.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;

@FunctionalInterface
public interface PreHandleListener {
    void accept(@NonNull Event event, @NonNull HederaState state);
}
