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

import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The entire purpose of this class is to ensure that inter-service dependencies are respected between
 * migrations. The only required dependency right now is the {@link EntityIdService}, which is needed
 * for genesis blocklist accounts in the token service genesis migration. (See {@link
 * Service#registerSchemas(SchemaRegistry)}).
 *
 * <p>Note: there are only two ordering requirements to maintain: first, that the entity ID service
 * is migrated before the token service; and second, that the remaining services are migrated _in any
 * deterministic order_. In order to ensure the entity ID service is migrated before the token service,
 * we'll just migrate the entity ID service first.
 */
public class OrderedServiceMigrator implements ServiceMigrator {
    private static final Logger logger = LogManager.getLogger(OrderedServiceMigrator.class);

    /**
     * Migrates the services registered with the {@link ServicesRegistry}
     * @param state            The state to migrate
     * @param servicesRegistry The services registry to use for the migrations
     * @param previousVersion The previous version of the state
     * @param currentVersion The current version of the state
     * @param config The configuration to use for the migrations
     * @param genesisNetworkInfo The network information to use for the migrations.
     *                           This is only used in genesis case
     * @param metrics The metrics to use for the migrations
     * @return The list of state changes that occurred during the migrations
     */
    @Override
    public List<StateChanges.Builder> doMigrations(
            @NonNull final State state,
            @NonNull final ServicesRegistry servicesRegistry,
            @Nullable final SoftwareVersion previousVersion,
            @NonNull final SoftwareVersion currentVersion,
            @NonNull final Configuration config,
            @Nullable final NetworkInfo genesisNetworkInfo,
            @NonNull final Metrics metrics) {
        requireNonNull(state);
        requireNonNull(currentVersion);
        requireNonNull(config);
        requireNonNull(metrics);

        final Map<String, Object> sharedValues = new HashMap<>();
        final var migrationStateChanges = new MigrationStateChanges(state, config);
        logger.info("Migrating Entity ID Service as pre-requisite for other services");
        final var entityIdRegistration = servicesRegistry.registrations().stream()
                .filter(service -> EntityIdService.NAME.equals(service.service().getServiceName()))
                .findFirst()
                .orElseThrow();
        final var entityIdRegistry = (MerkleSchemaRegistry) entityIdRegistration.registry();
        final var deserializedPbjVersion = Optional.ofNullable(previousVersion)
                .map(SoftwareVersion::getPbjSemanticVersion)
                .orElse(null);
        entityIdRegistry.migrate(
                state,
                deserializedPbjVersion,
                currentVersion.getPbjSemanticVersion(),
                config,
                genesisNetworkInfo,
                metrics,
                // We call with null here because we're migrating the entity ID service itself
                null,
                sharedValues,
                migrationStateChanges);

        // The token service has a dependency on the entity ID service during genesis migrations, so we
        // CAREFULLY create a different WritableStates specific to the entity ID service. The different
        // WritableStates instances won't be able to "see" the changes made by each other, meaning that a
        // change made with WritableStates instance X would _not_ be read by a separate WritableStates
        // instance Y. However, since the inter-service dependencies are limited to the EntityIdService,
        // there shouldn't be any changes made in any single WritableStates instance that would need to be
        // read by any other separate WritableStates instances. This should hold true as long as the
        // EntityIdService is not directly injected into any genesis generation code. Instead, we'll inject
        // this entity ID writable states instance into the MigrationContext below, to enable generation of
        // entity IDs through an appropriate API.
        final var entityIdWritableStates = state.getWritableStates(EntityIdService.NAME);
        final var entityIdStore = new WritableEntityIdStore(entityIdWritableStates);

        // Now that the Entity ID Service is migrated, migrate the remaining services in the order
        // determined by the service registry (this ordering can be critical for migrations with
        // inter-service dependencies)
        servicesRegistry.registrations().stream()
                .filter(r -> !Objects.equals(entityIdRegistration, r))
                .forEach(registration -> {
                    // FUTURE We should have metrics here to keep track of how long it takes to
                    // migrate each service
                    final var service = registration.service();
                    final var serviceName = service.getServiceName();
                    logger.info("Migrating Service {}", serviceName);
                    final var registry = (MerkleSchemaRegistry) registration.registry();
                    registry.migrate(
                            state,
                            deserializedPbjVersion,
                            currentVersion.getPbjSemanticVersion(),
                            config,
                            genesisNetworkInfo,
                            metrics,
                            entityIdStore,
                            sharedValues,
                            migrationStateChanges);
                    // Now commit any changes that were made to the entity ID state (since other service entities could
                    // depend on newly-generated entity IDs)
                    if (entityIdWritableStates instanceof MerkleStateRoot.MerkleWritableStates mws) {
                        mws.commit();
                        migrationStateChanges.trackCommit();
                    }
                });
        return migrationStateChanges.getStateChanges();
    }

    @Override
    public SemanticVersion creationVersionOf(@NonNull final State state) {
        if (!(state instanceof MerkleStateRoot merkleStateRoot)) {
            throw new IllegalArgumentException("State must be a MerkleStateRoot");
        }
        return PLATFORM_STATE_SERVICE.creationVersionOf(merkleStateRoot);
    }
}
