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

package com.swirlds.common.wiring.transformers;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * All enums used by {@link RoutableData} must implement this interface. This is to enforce a uniform clean pattern when
 * creating {@link RoutableData} objects.
 */
public interface RoutableDataType { // TODO performance risk

    /**
     * Create a new {@link RoutableData} object with the given data.
     *
     * @param data the data
     * @param <T>  the type of the enum
     * @return the new {@link RoutableData} object
     */
    @SuppressWarnings("unchecked")
    @NonNull
    default <T extends Enum<T> & RoutableDataType> RoutableData<T> of(@NonNull final Object data) {
        return new RoutableData<>((T) this, data);
    }
}
