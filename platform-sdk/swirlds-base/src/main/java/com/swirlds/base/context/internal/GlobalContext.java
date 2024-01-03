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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The GlobalContext is a {@link Context} implementation that defined as a singleton and is used to store global
 * information.
 *
 * @see ThreadLocalContext
 * @see Context
 */
public final class GlobalContext implements Context {

    private static final GlobalContext INSTANCE = new GlobalContext();

    private final Map<String, String> contextMap;

    /**
     * private constructor to prevent instantiation.
     */
    private GlobalContext() {
        contextMap = new ConcurrentHashMap<>();
    }

    @Override
    public AutoCloseable add(@NonNull String key, @NonNull String value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        contextMap.put(key, value);
        return () -> remove(key);
    }

    @Override
    public void remove(@NonNull String key) {
        Objects.requireNonNull(key, "key must not be null");
        contextMap.remove(key);
    }

    /**
     * Clears the context.
     */
    public void clear() {
        contextMap.clear();
    }

    /**
     * Returns the singleton instance of the {@link GlobalContext}.
     *
     * @return the singleton instance of the {@link GlobalContext}
     */
    @NonNull
    public static GlobalContext getInstance() {
        return INSTANCE;
    }

    /**
     * returns the content of the context as an immutable map. This method should only be used by the base apis.
     *
     * @return the content of the context as an immutable map
     */
    @NonNull
    public static Map<String, String> getContextMap() {
        return Collections.unmodifiableMap(INSTANCE.contextMap);
    }
}
