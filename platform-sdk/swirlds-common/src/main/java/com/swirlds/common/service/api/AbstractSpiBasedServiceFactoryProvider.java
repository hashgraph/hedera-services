/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.service.api;

import com.swirlds.common.service.internal.SpiLoader;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

public abstract class AbstractSpiBasedServiceFactoryProvider<
                C extends Record, S extends Service, F extends ServiceFactory<C, S>>
        implements ServiceFactoryProvider<C, S, F> {

    private final F serviceFactory;

    private static final Configuration EMPTY_CONFIG =
            ConfigurationBuilder.create().build();

    protected AbstractSpiBasedServiceFactoryProvider(
            @NonNull final Class<F> factoryClass, @NonNull final Configuration configuration) {
        ModuleLayer moduleLayer = getClass().getModule().getLayer();
        this.serviceFactory = SpiLoader.loadFromSpi(moduleLayer, factoryClass, configuration);
    }

    protected AbstractSpiBasedServiceFactoryProvider(@NonNull final Class<F> factoryClass) {
        this(factoryClass, EMPTY_CONFIG);
    }

    protected AbstractSpiBasedServiceFactoryProvider(
            @NonNull final Class<F> factoryClass, @NonNull final ModuleLayer moduleLayer) {
        this(factoryClass, moduleLayer, EMPTY_CONFIG);
    }

    protected AbstractSpiBasedServiceFactoryProvider(
            @NonNull final Class<F> factoryClass,
            @NonNull final ModuleLayer moduleLayer,
            @NonNull final Configuration configuration) {
        this.serviceFactory = SpiLoader.loadFromSpi(moduleLayer, factoryClass, configuration);
    }

    @Override
    public F getServiceFactory() {
        return serviceFactory;
    }
}
