/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.cryptography.nativesupport.internal;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * A utility class to run a given action only once for a given key, handling concurrent calls.
 *
 * @param <T> The type of the key
 */
public class RunOnlyOnce<T> {
    /** A set of keys which have already ran */
    private final Set<T> alreadyRan;

    /**
     * Constructor
     */
    public RunOnlyOnce() {
        alreadyRan = new HashSet<>();
    }

    /**
     * Run the provided action only once for the given key. If the action has already been run for the key, the Runnable
     * will not be invoked. If the runnable throws an exception, it will be propagated and considered as if the action
     * was not run.
     *
     * @param key      The key to run the action for
     * @param runnable The action to run
     */
    public synchronized void runIfNeeded(@NonNull final T key, @NonNull final Runnable runnable) {
        if (alreadyRan.contains(key)) {
            return;
        }
        runnable.run();
        alreadyRan.add(key);
    }
}
