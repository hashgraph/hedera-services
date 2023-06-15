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

package com.swirlds.platform.componentframework.internal;

import com.swirlds.platform.componentframework.TaskProcessor;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Utilities for working with {@link TaskProcessor}s
 */
public final class TaskProcessorUtils {
    private static final Set<String> IGNORED_METHODS = Set.of("getProcessingMethods");

    private TaskProcessorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Check that a {@link TaskProcessor} definition is valid
     *
     * @param def
     * 		the definition to check
     * @throws IllegalArgumentException
     * 		if the definition is invalid
     */
    public static void checkTaskProcessorDefinition(final Class<? extends TaskProcessor> def) {
        if (!def.isInterface()) {
            throw new IllegalArgumentException(
                    String.format("A TaskProcessor must be an interface. %s is not an interface", def.getName()));
        }
        final Method[] methods = def.getDeclaredMethods();
        int processingMethods = 0;
        for (final Method method : methods) {
            if (IGNORED_METHODS.contains(method.getName())) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException(String.format(
                        "A TaskProcessor method must have exactly one parameter. The method %s is invalid", method));
            }
            if (method.getReturnType() != void.class) {
                throw new IllegalArgumentException(
                        String.format("A TaskProcessor method must return void. The method %s is invalid", method));
            }
            processingMethods++;
        }
        if (processingMethods == 0) {
            throw new IllegalArgumentException(String.format(
                    "A TaskProcessor must have at least one processing method. %s has none", def.getName()));
        }
    }
}
