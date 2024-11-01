/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.services;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.merkle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A simple implementation of {@link ServicesRegistry}.
 */
@Singleton
public final class ServicesRegistryImpl implements ServicesRegistry {
    private static final Logger logger = LogManager.getLogger(ServicesRegistryImpl.class);

    /** We have to register with the {@link ConstructableRegistry} based on the schemas of the services */
    private final ConstructableRegistry constructableRegistry;
    /** The set of registered services */
    private final SortedSet<Registration> entries;
    /**
     * The current bootstrap configuration of the network; note this ideally would be a
     * provider of {@link com.hedera.node.config.VersionedConfiguration}s per version,
     * in case a service's states evolved with changing config. But this is a very edge
     * affordance that we have no example of needing.
     */
    private final Configuration bootstrapConfig;

    /**
     * Creates a new registry.
     */
    @Inject
    public ServicesRegistryImpl(
            @NonNull final ConstructableRegistry constructableRegistry, @NonNull final Configuration bootstrapConfig) {
        this.constructableRegistry = requireNonNull(constructableRegistry);
        this.bootstrapConfig = requireNonNull(bootstrapConfig);
        this.entries = new TreeSet<>();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ServicesRegistry subRegistryFor(@NonNull final String... serviceNames) {
        requireNonNull(serviceNames);
        final var selections = Set.of(serviceNames);
        final var subRegistry = new ServicesRegistryImpl(constructableRegistry, bootstrapConfig);
        subRegistry.entries.addAll(entries.stream()
                .filter(registration -> selections.contains(registration.serviceName()))
                .toList());
        return subRegistry;
    }

    /**
     * Register the given service.
     *
     * @param service The service to register
     */
    @Override
    public void register(@NonNull final Service service) {
        final var serviceName = service.getServiceName();

        logger.debug("Registering schemas for service {}", serviceName);
        final var registry =
                new MerkleSchemaRegistry(constructableRegistry, serviceName, bootstrapConfig, new SchemaApplications());
        service.registerSchemas(registry);

        entries.add(new Registration(service, registry));
        logger.info("Registered service {} with implementation {}", service.getServiceName(), service.getClass());
    }

    @NonNull
    @Override
    public SortedSet<Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }
}
