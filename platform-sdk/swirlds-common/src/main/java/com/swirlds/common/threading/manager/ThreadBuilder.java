package com.swirlds.common.threading.manager;

import com.swirlds.common.threading.manager.internal.AdHocThreadManager;
import com.swirlds.common.threading.manager.internal.StandardThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;

public sealed interface ThreadBuilder permits StandardThreadManager, AdHocThreadManager {

    /**
     * Build a new thread.
     *
     * @param runnable    the runnable that will be executed on the thread
     * @return a new Thread
     * @throws com.swirlds.common.utility.LifecycleException if called before the thread manager has been started
     */
    @NonNull
    Thread buildThread(@NonNull Runnable runnable);

}
