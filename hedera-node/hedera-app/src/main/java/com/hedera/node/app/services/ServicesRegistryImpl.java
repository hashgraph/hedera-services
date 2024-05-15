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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.swirlds.common.constructable.ConstructableRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A simple implementation of {@link ServicesRegistry}.
 */
@Singleton
public final class ServicesRegistryImpl implements ServicesRegistry {
    private static final Logger logger = LogManager.getLogger(ServicesRegistryImpl.class);
    /**
     * Use a constant version to be passed to the schema registration.
     * If the version changes the class id will be different and the upgrade will have issues.
     */
    private final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();
    /** We have to register with the {@link ConstructableRegistry} based on the schemas of the services */
    private final ConstructableRegistry constructableRegistry;
    /** The set of registered services */
    private final SortedSet<Registration> entries;

    private final GenesisRecordsBuilder genesisRecords;

    /**
     * Creates a new registry.
     */
    public ServicesRegistryImpl(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final GenesisRecordsBuilder genesisRecords) {
        this.constructableRegistry = requireNonNull(constructableRegistry);
        this.genesisRecords = requireNonNull(genesisRecords);
        this.entries = new TreeSet<>(Comparator.comparing(r -> r.service().getServiceName()));
    }

    /**
     * Register the given service.
     *
     * @param service The service to register
     */
    public void register(@NonNull final Service service) {
        final var serviceName = service.getServiceName();

        logger.debug("Registering schemas for service {}", serviceName);
        final var registry = new MerkleSchemaRegistry(constructableRegistry, serviceName, genesisRecords);
        service.registerSchemas(registry, VERSION);

        entries.add(new Registration(service, registry));
        logger.info("Registered service {} with implementation {}", service.getServiceName(), service.getClass());
    }

    @NonNull
    @Override
    public SortedSet<Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }
}
