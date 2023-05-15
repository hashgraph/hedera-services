package com.swirlds.platform.gossip;

import com.swirlds.base.state.LifecyclePhase;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Boilerplate code for gossip.
 */
public class AbstractGossip implements Gossip {

    private LifecyclePhase lifecyclePhase;

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        lifecyclePhase = LifecyclePhase.STARTED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        lifecyclePhase = LifecyclePhase.STOPPED;
    }
}
