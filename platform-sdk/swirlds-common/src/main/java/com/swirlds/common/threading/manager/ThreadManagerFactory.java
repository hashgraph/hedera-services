/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.threading.manager;

import com.swirlds.common.threading.manager.internal.AdHocThreadManager;
import com.swirlds.common.threading.manager.internal.StandardThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Builds thread managers.
 */
public final class ThreadManagerFactory {

    private static final AdHocThreadManager STATIC_THREAD_MANAGER = new AdHocThreadManager();

    private ThreadManagerFactory() {}

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
