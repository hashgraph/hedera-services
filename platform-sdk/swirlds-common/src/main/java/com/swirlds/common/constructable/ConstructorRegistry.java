/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable;

/**
 * A registry that holds constructors of {@link RuntimeConstructable} classes. All constructors in this registry will
 * have a signature of the generic type used by an instance of this class.
 *
 * @param <T>
 * 		the type of constructor held in this registry
 */
public interface ConstructorRegistry<T> {
    /**
     * Returns a constructor for the class with the provided ID, or null if this ID is not registered
     *
     * @param classId
     * 		the class ID of the requested constructor
     * @return a constructor or null
     */
    T getConstructor(long classId);

    /**
     * Register the provided {@link RuntimeConstructable} so that it can be instantiated based on its class ID
     *
     * @param aClass
     * 		the class to register
     * @throws ConstructableRegistryException
     * 		thrown if constructor cannot be registered for any reason
     */
    void registerConstructable(Class<? extends RuntimeConstructable> aClass) throws ConstructableRegistryException;

    /**
     * Register the provided {@link RuntimeConstructable} so that it can be instantiated based on its class ID
     *
     * @param aClass
     * 		the class to register
     * @param constructor
     * 		the constructor for this class
     * @throws ConstructableRegistryException
     * 		thrown if constructor cannot be registered for any reason
     */
    void registerConstructable(Class<? extends RuntimeConstructable> aClass, T constructor)
            throws ConstructableRegistryException;
}
