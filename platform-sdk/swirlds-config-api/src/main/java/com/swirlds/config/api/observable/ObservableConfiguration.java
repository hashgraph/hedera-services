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

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Extension of the {@link Configuration} API to add support for observable properties ({@link ObservableProperty}) that
 * might change at runtime.
 * <p>
 * This is only created for discussing the API. Later the API might become a part of {@link Configuration}.
 */
public interface ObservableConfiguration extends Configuration {

    /**
     * Returns an observable property based on the given {@code key}. Calling this method will never return {@code null}
     * or throw an {@link java.util.NoSuchElementException}. Even if the property is currently not defined by the config
     * it might be present in future. Therefore an {@link ObservableProperty} instance is always returned that can be
     * used to check the state and value of a property at runtime.
     *
     * @param key  the key of the property
     * @param type the value type of the property
     * @param <T>  the value type of the property
     * @return the observable property
     */
    @NonNull
    <T> ObservableProperty getObservableProperty(@NonNull String key, @NonNull Class<T> type);

    /**
     * Once an {@link ObservableProperty} is requested by calling {@link #getObservableProperty(String, Class)} the
     * configuration will check all available {@link com.swirlds.config.api.source.ConfigSource} instances periodically
     * to check if the raw value of the property has changed. If the value of a property has changed it will be
     * converted in the needed type for the property by using a matching {@link com.swirlds.config.api.converter.ConfigConverter}.
     * The converted value will than be validated by calling the registered {@link com.swirlds.config.api.validation.ConfigValidator}
     * instances. If an error happens in that workflow it will be automatically forwarded to the given {@link
     * Consumer}.
     *
     * @param onError the consumer that will be called if an error happens.
     */
    void onUpdateError(@NonNull Consumer<Throwable> onError);
}
