package com.swirlds.platform.components.state;

import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Deque;
import java.util.LinkedList;

public class TestSavedStateController extends SavedStateController {
    private final Deque<SignedState> queue = new LinkedList<>();

    public TestSavedStateController() {
        super(new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class), s->true);
    }

    @Override
    public synchronized void maybeSaveState(@NonNull final SignedState signedState) {
        queue.add(signedState);
    }

    @Override
    public synchronized void reconnectStateReceived(@NonNull final SignedState signedState) {
        queue.add(signedState);
    }

    @Override
    public synchronized void registerSignedStateFromDisk(@NonNull final SignedState signedState) {
        queue.add(signedState);
    }

    public @NonNull Deque<SignedState> getStatesQueue() {
        return queue;
    }

}
