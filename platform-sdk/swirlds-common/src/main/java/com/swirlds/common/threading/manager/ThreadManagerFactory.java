package com.swirlds.common.threading.manager;

import com.swirlds.common.threading.manager.internal.AdHocThreadManager;
import com.swirlds.common.threading.manager.internal.StandardThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Builds thread managers.
 */
public final class ThreadManagerFactory {

    private static final AdHocThreadManager STATIC_THREAD_MANAGER = new AdHocThreadManager();

    private ThreadManagerFactory() {

    }

    /**
     * Get a static thread manager.
     *
     * @return the static thread manager
     * @deprecated avoid using the static thread manager wherever possible
     */
    @Deprecated
    public static @NonNull ThreadManager getStaticThreadManager() {
        return STATIC_THREAD_MANAGER;
    }

    /**
     * Build a new thread manager. Thread manager is not automatically started.
     *
     * @return a new thread manager
     */
    public static @NonNull StartableThreadManager buildThreadManager() {
        return new StandardThreadManager();
    }
}
