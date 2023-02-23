/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.service;

import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service provider for all {@link Service} instances in the system
 */
public interface ServiceProvider {

    /**
     * Get all services
     *
     * @return all services
     */
    @NonNull
    Set<Service> getAllServices();

    /**
     * Get all transaction handlers
     *
     * @return all transaction handlers
     */
    @NonNull
    default Set<TransactionHandler> getAllTransactionHandler() {
        return getAllServices().stream()
                .flatMap(service -> service.getTransactionHandler().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get all query handlers
     *
     * @return all query handlers
     */
    @NonNull
    default Set<QueryHandler> getAllQueryHandler() {
        return getAllServices().stream()
                .flatMap(service -> service.getQueryHandler().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get service by name
     *
     * @param name service name
     * @return service
     */
    @NonNull
    default Optional<Service> getServiceByName(@NonNull final String name) {
        return getAllServices().stream()
                .filter(service -> Objects.equals(service.getServiceName(), name))
                .findFirst();
    }

    /**
     * Get service by type (class).
     *
     * @param type service type
     * @param <T>  service type
     * @return service
     */
    @NonNull
    <T extends Service> Optional<T> getServiceByType(@NonNull final Class<T> type);
}
