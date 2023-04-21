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

import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implentation of the {@link ConfigurationBuilderFactory} interface that will automatically be loaded by Java SPI (see
 * {@link java.util.ServiceLoader}).
 */
public final class ConfigurationBuilderFactoryImpl implements ConfigurationBuilderFactory {

    @NonNull
    @Override
    public ConfigurationBuilder create() {
        return new ConfigurationBuilderImpl();
    }
}
