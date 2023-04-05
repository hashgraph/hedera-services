package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.context.StateChildren;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.system.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordingStatusChangeListener implements PlatformStatusChangeListener {
    private static final Logger log = LogManager.getLogger(RecordingStatusChangeListener.class);
    private final StateChildren stateChildren;
    private final PlatformStatusChangeListener delegate;

    public RecordingStatusChangeListener(
            @NonNull final StateChildren stateChildren,
            @NonNull final PlatformStatusChangeListener delegate) {
        this.stateChildren = stateChildren;
        this.delegate = delegate;
    }

    @Override
    public void notify(@NonNull final PlatformStatusChangeNotification notification) {
        delegate.notify(notification);
        if (notification.getNewStatus() == PlatformStatus.FREEZE_COMPLETE) {
            log.info("Now recording the final state children for replay verification");
        }
        // TODO - for each child of state we want to verify at the end of a replay test
        // (accounts, tokens, etc.), write a representation of this child to a file,
        // preferably using PBJ for leaf representations
    }
}
