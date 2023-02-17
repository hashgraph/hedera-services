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

package com.hedera.node.app.spi;

import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.swirlds.common.context.PlatformContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

public class ServiceEntryPoint {

    final private Set<Service> services;

    public ServiceEntryPoint() {
        services = new HashSet<>();
    }

    public void init(final PlatformContext platformContext) {
        final ServiceLoader<ServiceFactory> serviceLoader = ServiceLoader.load(ServiceFactory.class);
        final Iterator<ServiceFactory> iterator = serviceLoader.iterator();
        while (iterator.hasNext()) {
            services.add(iterator.next().createService(platformContext));
        }
    }

    public Set<Service> getAllServices() {
        return Collections.unmodifiableSet(services);
    }

    public Set<TransactionHandler> getAllTransactionHandler() {
        return getAllServices().stream()
                .flatMap(service -> service.getTransactionHandler().stream())
                .collect(Collectors.toSet());
    }

    public Set<QueryHandler> getAllQueryHandler() {
        return getAllServices().stream()
                .flatMap(service -> service.getQueryHandler().stream())
                .collect(Collectors.toSet());
    }

    public Optional<Service> getServiceByName(final String name) {
        return getAllServices().stream()
                .filter(service -> Objects.equals(service.getServiceName(), name))
                .findFirst();
    }

    public <T extends Service> Optional<T> getServiceByType(final Class<T> type) {
        Objects.requireNonNull(type);
        return (Optional<T>) getAllServices().stream()
                .filter(service -> type.isAssignableFrom(service.getClass()))
                .findFirst();
    }

}
