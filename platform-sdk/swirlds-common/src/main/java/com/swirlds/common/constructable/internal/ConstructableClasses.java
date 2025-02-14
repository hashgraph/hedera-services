// SPDX-License-Identifier: Apache-2.0
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
