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

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Builds a new thread.
 */
public interface ThreadBuilder {

    /**
     * Build a new thread.
     *
     * @param runnable the runnable that will be executed on the thread
     * @return a new Thread
     * @throws com.swirlds.common.utility.LifecycleException if called before the thread manager has been started
     */
    @NonNull
    Thread buildThread(@NonNull Runnable runnable);
}
