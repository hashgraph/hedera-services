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

package com.hedera.node.app.fixtures.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fake implementation of the {@link ServicesRegistry} interface.
 */
public class FakeServicesRegistry implements ServicesRegistry {
    public static final ServicesRegistry.Factory FACTORY =
            (@NonNull final ConstructableRegistry registry, @NonNull final Configuration configuration) ->
                    new FakeServicesRegistry();

    private static final Logger logger = LogManager.getLogger(FakeServicesRegistry.class);
    /**
     * The set of registered services
     */
    private final SortedSet<ServicesRegistry.Registration> entries;

    /**
     * Creates a new registry.
     */
    public FakeServicesRegistry() {
        this.entries = new TreeSet<>();
    }

    /**
     * Register the given service.
     *
     * @param service The service to register
     */
    @Override
    public void register(@NonNull final Service service) {
        final var registry = new FakeSchemaRegistry();
        service.registerSchemas(registry);

        entries.add(new FakeServicesRegistry.Registration(service, registry));
        logger.info("Registered service {}", service.getServiceName());
    }

    @NonNull
    @Override
    public SortedSet<FakeServicesRegistry.Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }

    @NonNull
    @Override
    public ServicesRegistry subRegistryFor(@NonNull final String... serviceNames) {
        requireNonNull(serviceNames);
        final var selections = Set.of(serviceNames);
        final var subRegistry = new FakeServicesRegistry();
        subRegistry.entries.addAll(entries.stream()
                .filter(registration -> selections.contains(registration.serviceName()))
                .toList());
        return subRegistry;
    }
}
