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

package com.swirlds.base.function;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A function that accepts and object and returns a primitive boolean. Side effects are allowed.
 *
 * @param <T> the type accepted by the function
 */
@FunctionalInterface
public interface BooleanFunction<T> {

    /**
     * A function that accepts and object and returns a primitive boolean. Side effects are allowed.
     *
     * @param object the object to apply
     * @return true if success, false otherwise
     */
    boolean apply(@Nullable final T object);
}
