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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ThreadLocalContext implements Context {

    private static final ThreadLocalContext INSTANCE = new ThreadLocalContext();

    private ThreadLocal<Map<String, String>> contextThreadLocal = new ThreadLocal<>();

    @Override
    public void put(String key, String value) {
        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap == null) {
            contextMap = new HashMap<>();
            contextThreadLocal.set(contextMap);
        }
        contextMap.put(key, value);
    }

    @Override
    public void put(String key, String... values) {
        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap == null) {
            contextMap = new HashMap<>();
            contextThreadLocal.set(contextMap);
        }
        contextMap.put(key, String.join(",", values));
    }

    @Override
    public void remove(String key) {
        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap != null) {
            contextMap.remove(key);
        }
    }

    @Override
    public void clear() {
        Map<String, String> contextMap = contextThreadLocal.get();
        if (contextMap != null) {
            contextMap.clear();
        }
    }

    public static ThreadLocalContext getInstance() {
        return INSTANCE;
    }

    public static Map<String, String> getContextMap() {
        final Map<String, String> current = INSTANCE.contextThreadLocal.get();
        if (current != null) {
            return Collections.unmodifiableMap(current);
        }
        return Map.of();
    }
}
