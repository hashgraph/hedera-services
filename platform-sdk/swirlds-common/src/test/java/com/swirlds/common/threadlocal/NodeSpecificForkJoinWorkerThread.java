package com.swirlds.common.threadlocal;

import com.swirlds.base.context.Context;
import com.swirlds.common.platform.NodeId;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public class NodeSpecificForkJoinWorkerThread extends ForkJoinWorkerThread {

    // TODO what is the impact of not supplying a default value? ForkJoinWorkerThread states:
    /*
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     */
    private final NodeId id;

    protected NodeSpecificForkJoinWorkerThread(final ForkJoinPool pool, final NodeId id) {
        super(pool);
        this.id = id;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Context.getThreadLocalContext().add("nodeId", id.toString());
    }
}
