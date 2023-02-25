/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.json;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A filter that operates on exceptions.
 */
public class HasExceptionFilter implements Predicate<JsonLogEntry> {

    private final Set<String> exceptionTypes;

    public static HasExceptionFilter hasException(final String... exceptionTypes) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, exceptionTypes);
        return new HasExceptionFilter(set);
    }

    public static HasExceptionFilter hasException(final List<String> exceptionTypes) {
        return new HasExceptionFilter(new HashSet<>(exceptionTypes));
    }

    public static HasExceptionFilter hasException(final Set<String> exceptionTypes) {
        return new HasExceptionFilter(exceptionTypes);
    }

    /**
     * Create a filter that catches only specific exception types.
     *
     * @param exceptionTypes
     * 		a list of exception type names. Exact matches only, does not consider inheritance.
     */
    public HasExceptionFilter(final Set<String> exceptionTypes) {
        this.exceptionTypes = exceptionTypes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(final JsonLogEntry entry) {
        if (!entry.hasException() || exceptionTypes == null) {
            return false;
        }
        return exceptionTypes.contains(entry.getExceptionType());
    }
}
