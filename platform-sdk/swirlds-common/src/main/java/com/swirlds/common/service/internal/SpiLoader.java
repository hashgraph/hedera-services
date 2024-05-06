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

package com.swirlds.common.service.internal;

import com.swirlds.common.service.api.Service;
import com.swirlds.common.service.api.ServiceFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.stream.Collectors;

public class SpiLoader {

    private static <C extends Record, S extends Service, F extends ServiceFactory<C, S>>
            Set<Provider<F>> loadProviderFromSpi(final ModuleLayer layer, final Class<F> factoryClass) {
        final ServiceLoader<F> serviceLoader = ServiceLoader.load(layer, factoryClass);
        return serviceLoader.stream().collect(Collectors.toSet());
    }

    public static <C extends Record, S extends Service, F extends ServiceFactory<C, S>> F loadFromSpi(
            @NonNull final ModuleLayer layer,
            @NonNull final Class<F> factoryClass,
            @NonNull final Configuration configuration) {
        final Set<Provider<F>> providers = loadProviderFromSpi(layer, factoryClass);
        final Set<F> factories = providers.stream().map(Provider::get).collect(Collectors.toSet());
        final Set<F> activeFactories =
                factories.stream().filter(f -> f.isActive(configuration)).collect(Collectors.toSet());
        if (activeFactories.isEmpty()) {
            throw new IllegalStateException("No active service factory found: " + factoryClass);
        }
        if (activeFactories.size() > 1) {
            throw new IllegalStateException("Multiple active service factories found: " + factoryClass);
        }
        return activeFactories.iterator().next();
    }
}
