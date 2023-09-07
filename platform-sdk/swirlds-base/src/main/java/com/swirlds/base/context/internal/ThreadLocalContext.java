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

package com.swirlds.base.context.internal;

import com.swirlds.base.context.Context;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The ThreadLocalContext is a {@link Context} implementation that defined as a singleton and is used to store
 * information that is local to the current thread.
 *
 * @see GlobalContext
 * @see Context
 */
public final class ThreadLocalContext implements Context {

    private static final ThreadLocalContext INSTANCE = new ThreadLocalContext();

    private final ThreadLocal<Map<String, String>> contextThreadLocal;

    /**
     * private constructor to prevent instantiation.
     */
    private ThreadLocalContext() {
        this.contextThreadLocal = new ThreadLocal<>();
    }

    @Override
    public AutoCloseable add(@NonNull String key, @NonNull String value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap == null) {
            contextMap = new HashMap<>();
            contextThreadLocal.set(contextMap);
        }
        contextMap.put(key, value);
        return () -> remove(key);
    }

    @Override
    public void remove(@NonNull String key) {
        Objects.requireNonNull(key, "key must not be null");
        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap != null) {
            contextMap.remove(key);
        }
    }

    /**
     * Clears the context map for the current thread.
     */
    public void clear() {
        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap != null) {
            contextMap.clear();
        }
    }

    /**
     * Returns the singleton instance of the {@link ThreadLocalContext}.
     *
     * @return the singleton instance of the {@link ThreadLocalContext}
     */
    @NonNull
    public static ThreadLocalContext getInstance() {
        return INSTANCE;
    }

    /**
     * returns the content of the context of the current thread as an immutable map. This method should only be used by
     * the base apis.
     *
     * @return the content of the context of the current thread as an immutable map
     */
    @NonNull
    public static Map<String, String> getContextMap() {
        final Map<String, String> current = INSTANCE.contextThreadLocal.get();
        if (current != null) {
            return Collections.unmodifiableMap(current);
        }
        return Map.of();
    }
}
