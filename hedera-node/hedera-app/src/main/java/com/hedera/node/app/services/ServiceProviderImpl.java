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

package com.hedera.node.app.services;

import com.hedera.node.app.spi.FacilityFacade;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.service.ServiceFactory;
import com.hedera.node.app.spi.service.ServiceProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.stream.Collectors;

public class ServiceProviderImpl implements ServiceProvider {

    final private Map<Class<Service>, Service> services;

    public ServiceProviderImpl(final FacilityFacade facilityFacade) {
        services = new HashMap<>();
        final ServiceLoader<ServiceFactory> serviceLoader = ServiceLoader.load(ServiceFactory.class);
        final Set<ServiceFactory> servicesFactories = serviceLoader.stream()
                .map(Provider::get)
                .collect(Collectors.toSet());

        servicesFactories.stream()
                .sorted((a, b) -> sort(a, b))
                .forEach(factory -> {
                    if (!services.keySet().containsAll(factory.getDependencies())) {
                        throw new IllegalStateException(
                                "Can not add service for " + factory.getServiceClass()
                                        + " since the service has a missing dependency");
                    }
                    services.keySet().stream()
                            .filter(service -> dependOnEachOther(service, factory.getServiceClass()))
                            .findAny().ifPresent(service -> {
                                throw new IllegalStateException(
                                        "Can not add service for " + factory.getServiceClass()
                                                + " since the service has a clash with " + service);
                            });
                    final Service service = factory.createService(this, facilityFacade);
                    services.put(factory.getServiceClass(), service);
                });
    }

    /**
     * Check if two classes are dependent on each other
     *
     * @param classA the class to check
     * @param classB the class to check
     * @return true if classA is assignable from classB or classB is assignable from classA
     */
    private boolean dependOnEachOther(@NonNull final Class<Service> classA, @NonNull final Class<Service> classB) {
        Objects.requireNonNull(classA, "classA");
        Objects.requireNonNull(classB, "classB");
        return classA.isAssignableFrom(classB) || classB.isAssignableFrom(classA);
    }

    /**
     * Sort the service factories based on their dependencies
     *
     * @param serviceFactoryA the service factory to compare
     * @param serviceFactoryB the service factory to compare
     * @return 1 if serviceFactoryA depends on serviceFactoryB, -1 if serviceFactoryB depends on serviceFactoryA, 0
     * otherwise
     */
    private int sort(@NonNull final ServiceFactory serviceFactoryA, @NonNull final ServiceFactory serviceFactoryB) {
        Objects.requireNonNull(serviceFactoryA, "serviceFactoryA");
        Objects.requireNonNull(serviceFactoryB, "serviceFactoryB");
        if (serviceFactoryA.getDependencies().contains(serviceFactoryB.getServiceClass())) {
            return 1;
        }
        if (serviceFactoryB.getDependencies().contains(serviceFactoryA.getServiceClass())) {
            return -1;
        }
        return 0;
    }

    @NonNull
    @Override
    public Set<Service> getAllServices() {
        return Set.copyOf(services.values());
    }

    @NonNull
    @Override
    public <T extends Service> Optional<T> getServiceByType(@NonNull final Class<T> type) {
        Objects.requireNonNull(type, "type");
        return services.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getKey(), type))
                .map(Map.Entry::getValue)
                .map(type::cast)
                .findFirst();

    }

}
