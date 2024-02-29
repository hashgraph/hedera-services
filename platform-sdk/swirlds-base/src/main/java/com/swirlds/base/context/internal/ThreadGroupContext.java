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

package com.swirlds.base.context.internal;

import com.swirlds.base.context.Context;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.System.Logger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadGroupContext implements Context {

    private static final Logger logger = System.getLogger(ThreadGroupContext.class.getName());

    private static final ThreadGroupContext INSTANCE = new ThreadGroupContext();

    private final Map<ThreadGroup, Map<String, String>> groupBasedContexts;

    private ThreadGroupContext() {
        this.groupBasedContexts = Collections.synchronizedMap(new WeakHashMap<>());
    }

    public static Context getInstance() {
        return INSTANCE;
    }

    @NonNull
    public AutoCloseable add(@NonNull final String key, @Nullable final String value) {
        currentThreadGroup()
                .ifPresentOrElse(
                        threadGroup -> {
                            Map<String, String> contextMap =
                                    groupBasedContexts.computeIfAbsent(threadGroup, t -> new ConcurrentHashMap<>());
                            contextMap.put(key, value);
                        },
                        () -> {
                            logger.log(
                                    Logger.Level.WARNING,
                                    "ThreadGroup of thread " + Thread.currentThread() + " is null, cannot add (" + key
                                            + "," + value + ") to context");
                        });
        return () -> remove(key);
    }

    @Override
    public void remove(@NonNull final String key) {
        remove(key, true);
    }

    public void remove(@NonNull final String key, final boolean includeParents) {
        currentThreadGroup()
                .ifPresentOrElse(
                        threadGroup -> {
                            final Map<String, String> map = groupBasedContexts.get(threadGroup);
                            if (map != null) {
                                map.remove(key);
                            }
                            if (includeParents) {
                                ThreadGroup parent = threadGroup.getParent();
                                if (parent != null) {
                                    remove(key, true);
                                }
                            }
                        },
                        () -> {
                            logger.log(
                                    Logger.Level.WARNING,
                                    "ThreadGroup of thread " + Thread.currentThread() + " is null, cannot remove " + key
                                            + " from context");
                        });
    }

    @NonNull
    private Optional<ThreadGroup> currentThreadGroup() {
        return Optional.ofNullable(Thread.currentThread().getThreadGroup());
    }

    @NonNull
    private Map<String, String> getContextForGroup(@NonNull final ThreadGroup group) {
        final Map<String, String> contextMap = new HashMap<>();
        final ThreadGroup parent = group.getParent();
        if (parent != null) {
            contextMap.putAll(getContextForGroup(parent));
        }
        contextMap.putAll(groupBasedContexts.getOrDefault(group, Collections.emptyMap()));
        return Collections.unmodifiableMap(contextMap);
    }
}
