/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable.internal;

import com.swirlds.common.constructable.RuntimeConstructable;

/**
 * A {@link RuntimeConstructable} class with its constructor
 *
 * @param constructable
 * 		a {@link RuntimeConstructable} class
 * @param constructor
 * 		its constructor
 * @param <T>
 * 		the type of constructor
 */
public record GenericClassConstructorPair<T>(Class<? extends RuntimeConstructable> constructable, T constructor) {

    /**
     * Is this constructable class equal to the one from the supplied pair
     *
     * @param pair
     * 		the pair to compare to
     * @return true if the classes are the same
     */
    public boolean classEquals(final GenericClassConstructorPair<?> pair) {
        return this.constructable.equals(pair.constructable());
    }
}
