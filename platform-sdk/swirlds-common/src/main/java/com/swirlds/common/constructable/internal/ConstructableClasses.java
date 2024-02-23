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
import java.util.ArrayList;
import java.util.List;

/**
 * A list of {@link RuntimeConstructable} with an interface that represents their constructor type
 *
 * @param <T>
 * 		the constructor type
 */
public class ConstructableClasses<T> {
    private final Class<T> constructorType;
    private final List<Class<? extends RuntimeConstructable>> classes;

    /**
     * @param constructorType
     * 		the constructor type
     */
    public ConstructableClasses(final Class<T> constructorType) {
        this.constructorType = constructorType;
        this.classes = new ArrayList<>();
    }

    /**
     * @param theClass
     * 		the {@link RuntimeConstructable} class to add
     */
    public void addClass(final Class<? extends RuntimeConstructable> theClass) {
        classes.add(theClass);
    }

    /**
     * @return the constructor type
     */
    public Class<T> getConstructorType() {
        return constructorType;
    }

    /**
     * @return a list of all {@link RuntimeConstructable} classes with this constructor type
     */
    public List<Class<? extends RuntimeConstructable>> getClasses() {
        return classes;
    }
}
