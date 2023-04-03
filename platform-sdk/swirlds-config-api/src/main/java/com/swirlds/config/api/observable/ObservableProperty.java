/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.config.api.observable;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Wrapper around a config property that value might change over time.
 *
 * @param <T> value type of the property
 */
public interface ObservableProperty<T> {

    /**
     * Helper interface to handle properties that are not set. See {@link #isSet(Consumer)} for more details.
     */
    @FunctionalInterface
    public static interface OrNotSet {

        /**
         * The given runnable is called if the property is not set.
         *
         * @param notSet the runnable that is called of the property is not set.
         */
        void orElse(@NonNull Runnable notSet);
    }

    /**
     * Returns true if the property is currently set. This is true if any {@link com.swirlds.config.api.source.ConfigSource}
     * provides the property (by its name).
     *
     * @return true if the property is set
     */
    boolean isSet();

    /**
     * Convenience method that let you react on the current state of the property. If the property is set (see {@link
     * #isSet()}) the given consumer will be executed. Otherwise the {@link OrNotSet} result of the method can be used
     * to react on a property that is not set.
     *
     * @param consumer the consumer that will be called if the property is set.
     * @return a functional interface that can be used to handle a property that is not set.
     */
    @NonNull
    OrNotSet isSet(Consumer<T> consumer);

    /**
     * Returns the current value of the property. {@code null} is allowed as value.
     *
     * @return The current value
     * @throws NoSuchElementException if the property is not set (see {@link #isSet()})
     */
    @Nullable
    T getValue() throws NoSuchElementException;

    /**
     * Returns the current value of the property or the given {@code defaultValue} if the property is not set (see
     * {@link #isSet()}). {@code null} is allowed as value.
     *
     * @param defaultValue the default value
     * @return the current value of the property or the given {@code defaultValue} if the property is not set
     */
    @Nullable
    T getValue(@Nullable T defaultValue);

    /**
     * Registers a {@link PropertyObserver} for the property.
     *
     * @param observer the observer that will be registered
     * @param <T>      the value type of the property
     */
    <T> void observe(@NonNull PropertyObserver<T> observer);
}
