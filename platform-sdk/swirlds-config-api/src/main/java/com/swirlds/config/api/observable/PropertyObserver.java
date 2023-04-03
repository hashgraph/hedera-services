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

/**
 * The pattern that is used to observe mutable config properties ({@link ObservableProperty}) is based on reactive
 * pattern like described in {@link java.util.concurrent.Flow}. All methods of this interface will be called
 * automatically by the {@link ObservableConfiguration} implementation.
 *
 * @param <T> type of the property value
 */
public interface PropertyObserver<T> {

    /**
     * This method is called exactly 1 time prior to any other method of the interface. An implementation normally
     * stores the initial value of the {@link ObservableProperty} and the {@link Observation} if the observation should
     * be stopped at runtime.
     *
     * @param property    the property that is observed by this observer
     * @param observation the observation
     */
    void onStart(@NonNull ObservableProperty<T> property, @NonNull Observation observation);

    /**
     * This method is called every time the raw value of the property has been updated. The raw value is defined as the
     * {@link String} value that is provided by a {@link com.swirlds.config.api.source.ConfigSource}.
     *
     * @param property the property that is observed by this observer
     */
    void onUpdate(@NonNull ObservableProperty<T> property);

    /**
     * This method is called whenever an update of the observed property fails. Common causes can be based on a {@link
     * com.swirlds.config.api.validation.ConfigViolation} or a conversion error in the internally used {@link
     * com.swirlds.config.api.converter.ConfigConverter}.
     *
     * @param throwable the error
     */
    void onError(@NonNull Throwable throwable);
}
