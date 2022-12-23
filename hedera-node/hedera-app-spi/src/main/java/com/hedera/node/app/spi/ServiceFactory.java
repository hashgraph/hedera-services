/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class provides the ability to load {@link Service} implementations at runtime. The Java SPI
 * (see {@link ServiceLoader}) is used to provide such information at runtime. Since we use the Java
 * module system the {@link ServiceLoader} instance can not be created in the factory. It must be
 * created in the module that add "uses" information for the module to the {@code module-info.java}.
 */
public final class ServiceFactory {

    private ServiceFactory() {}

    /**
     * This method returns a service instance of the given service that is provided by the Java SPI.
     *
     * @param type the service type
     * @param serviceLoader the service loaded that will be used
     * @param <S> the service type
     * @return the service instance
     * @throws IllegalStateException if no or multiple services are found
     */
    @NonNull
    public static <S extends Service> S loadService(
            @NonNull final Class<S> type, @NonNull final ServiceLoader<S> serviceLoader) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(serviceLoader, "serviceLoader must not be null");
        final Iterator<S> iterator = serviceLoader.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException(
                    "No service implementation found for service type '" + type + "'");
        }
        final S serviceInstance = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalStateException(
                    "Multiple service implementations found for service type '" + type + "'");
        }
        return serviceInstance;
    }

    @NonNull
    public static Set<Service> loadServices() {
        return ServiceLoader.load(Service.class).stream()
                .map(provider -> provider.get())
                .collect(Collectors.toSet());
    }
}
