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

package com.swirlds.common.config.sources;

import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.NoSuchElementException;
import java.util.Set;

public class ThreadCountPropertyConfigSource implements ConfigSource {
    private static final String ELEMENT_NOT_FOUND = "Element with name %s not found";
    private static final String THREAD_COUNT =
            String.valueOf(Runtime.getRuntime().availableProcessors());
    private static final Set<String> PROPERTY_NAMES = Set.of("fcHashMap.rebuildThreadCount");

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Set<String> getPropertyNames() {
        return PROPERTY_NAMES;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public String getValue(@NonNull String propertyName) throws NoSuchElementException {
        if (PROPERTY_NAMES.contains(propertyName)) {
            return THREAD_COUNT;
        }
        throw new NoSuchElementException(ELEMENT_NOT_FOUND.formatted(propertyName));
    }
}
