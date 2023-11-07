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

package com.hedera.node.app;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.config.VersionedConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The entire purpose of this class is to ensure that inter-service dependencies are respected between
 * migrations. The only required dependency right now is the {@link EntityIdService}, which is needed
 * for genesis blocklist accounts.
 */
public class OrderedServiceMigrator {
    private static final Logger logger = LogManager.getLogger(OrderedServiceMigrator.class);
    private final ServicesRegistry servicesRegistry;
    private final ThrottleAccumulator backendThrottle;

    public OrderedServiceMigrator(
            @NonNull final ServicesRegistryImpl servicesRegistry, @NonNull final ThrottleAccumulator backendThrottle) {
        this.servicesRegistry = requireNonNull(servicesRegistry);
        this.backendThrottle = requireNonNull(backendThrottle);
    }

    /**
     * Migrates the services registered with the {@link ServicesRegistry}
     */
    public void doMigrations(
            @NonNull final MerkleHederaState state,
            @NonNull final SemanticVersion currentVersion,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final VersionedConfiguration versionedConfiguration,
            @NonNull final NetworkInfo networkInfo) {
        requireNonNull(state);
        requireNonNull(currentVersion);
        requireNonNull(versionedConfiguration);
        requireNonNull(networkInfo);

        logger.info("Migrating Entity ID Service as pre-requisite for other services");
        final var entityIdRegistration = servicesRegistry.registrations().stream()
                .filter(service -> EntityIdService.NAME.equals(service.service().getServiceName()))
                .findFirst()
                .orElseThrow();
        final var entityIdRegistry = (MerkleSchemaRegistry) entityIdRegistration.registry();
        entityIdRegistry.migrate(
                state,
                previousVersion,
                currentVersion,
                versionedConfiguration,
                networkInfo,
                backendThrottle,
                // We call with null here because we're migrating the entity ID service itself
                null);

        // Now that the Entity ID Service is migrated, migrate the remaining services
        servicesRegistry.registrations().stream()
                .filter(r -> !Objects.equals(entityIdRegistration, r))
                .forEach(registration -> {
                    // FUTURE We should have metrics here to keep track of how long it takes to
                    // migrate each service
                    final var service = registration.service();
                    final var serviceName = service.getServiceName();
                    logger.info("Migrating Service {}", serviceName);
                    final var registry = (MerkleSchemaRegistry) registration.registry();

                    // The token service has a dependency on the entity ID service during genesis migrations, so we
                    // CAREFULLY create a different WritableStates specific to the entity ID service. The different
                    // WritableStates instances won't be able to see the changes made by each other, but there shouldn't
                    // be any conflicting changes. We'll inject this into the MigrationContext below to enable
                    // generation of entity IDs.
                    final var entityIdWritableStates = state.createWritableStates(EntityIdService.NAME);
                    final var entityIdStore = new WritableEntityIdStore(entityIdWritableStates);

                    registry.migrate(
                            state,
                            previousVersion,
                            currentVersion,
                            versionedConfiguration,
                            networkInfo,
                            backendThrottle,
                            requireNonNull(entityIdStore));
                    // Now commit any changes that were made to the entity ID state (since other service entities could
                    // depend on newly-generated entity IDs)
                    if (entityIdWritableStates instanceof MerkleHederaState.MerkleWritableStates mws) {
                        mws.commit();
                    }
                });
    }
}
