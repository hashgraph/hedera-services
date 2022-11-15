package com.hedera.node.app.workflows.prehandle;

import com.swirlds.common.system.events.Event;

import javax.annotation.Nonnull;

/**
 * Dummy implementation. To be implemented by
 * <a href="https://github.com/hashgraph/hedera-services/issues/4210">#4210</a>.
 */
public final class PreHandleWorkflowImpl implements PreHandleWorkflow {
    @Override
    public void start(@Nonnull final Event event) {
        // To be implemented by Issue #4210
    }
}
