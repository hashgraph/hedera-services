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

import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeUnit;

/**
 * Extension of the {@link ConfigurationBuilder} to support {@link ObservableConfiguration}.
 * <p>
 * This is only created for discussing the API. Later the API might become a part of {@link ConfigurationBuilder}.
 */
public interface ObservableConfigurationBuilder extends ConfigurationBuilder {


    @NonNull
    @Override
    ObservableConfiguration build();

    /**
     * Adds an update interval for checking for updates of observable properties (see {@link ObservableProperty}).
     *
     * @param interval the time between updates
     * @param unit     the time unit of the interval parameter
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException if this method is called after the config has been created
     */
    @NonNull
    ObservableConfigurationBuilder withUpdateInterval(long interval, @NonNull TimeUnit unit)
            throws IllegalStateException;

}
