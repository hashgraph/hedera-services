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

package com.swirlds.config.impl.internal;

/**
 * Basic lifecycle definition for the config. This will be replaced with a general interface once we have a platform
 * context API
 */
public interface ConfigLifecycle {

    /**
     * Inits the component. Calling this on a component that is already initialized might result in an exception.
     */
    void init();

    /**
     * Disposes the component. This can be called on any state and must be able to be called on an already disposed
     * component.
     */
    void dispose();

    /**
     * Returns true if the component is initialized ({@link #init()} has been called).
     *
     * @return true if the component is initialized
     */
    boolean isInitialized();

    /**
     * Throws a {@link IllegalStateException} if the component is initialized
     */
    default void throwIfInitialized() {
        if (isInitialized()) {
            throw new IllegalStateException(getClass().getName() + " is initialized");
        }
    }

    /**
     * Throws a {@link IllegalStateException} if the component is not initialized
     */
    default void throwIfNotInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException(getClass().getName() + " is not initialized");
        }
    }
}
