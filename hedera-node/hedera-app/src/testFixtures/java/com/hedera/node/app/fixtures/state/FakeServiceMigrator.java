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

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.test.fixtures.MapWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class FakeServiceMigrator implements ServiceMigrator {
    private static final String NAME_OF_ENTITY_ID_SERVICE = "EntityIdService";
    private static final String NAME_OF_ENTITY_ID_SINGLETON = "ENTITY_ID";

    @Override
    public List<StateChanges.Builder> doMigrations(
            @NonNull final State state,
            @NonNull final ServicesRegistry servicesRegistry,
            @Nullable final SoftwareVersion previousVersion,
            @NonNull final SoftwareVersion currentVersion,
            @NonNull final Configuration nodeConfiguration,
            @NonNull final Configuration platformConfiguration,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final Metrics metrics) {
        requireNonNull(state);
        requireNonNull(servicesRegistry);
        requireNonNull(currentVersion);
        requireNonNull(nodeConfiguration);
        requireNonNull(platformConfiguration);
        requireNonNull(networkInfo);
        requireNonNull(metrics);

        if (!(state instanceof FakeState fakeState)) {
            throw new IllegalArgumentException("Can only be used with FakeState instances");
        }
        if (!(servicesRegistry instanceof FakeServicesRegistry registry)) {
            throw new IllegalArgumentException("Can only be used with FakeServicesRegistry instances");
        }

        final AtomicLong prevEntityNum = new AtomicLong(
                nodeConfiguration.getConfigData(HederaConfig.class).firstUserEntity() - 1);
        final Map<String, Object> sharedValues = new HashMap<>();
        final var entityIdRegistration = registry.registrations().stream()
                .filter(service ->
                        NAME_OF_ENTITY_ID_SERVICE.equals(service.service().getServiceName()))
                .findFirst()
                .orElseThrow();
        if (!(entityIdRegistration.registry() instanceof FakeSchemaRegistry entityIdRegistry)) {
            throw new IllegalArgumentException("Can only be used with FakeSchemaRegistry instances");
        }
        final var deserializedPbjVersion = Optional.ofNullable(previousVersion)
                .map(SoftwareVersion::getPbjSemanticVersion)
                .orElse(null);
        entityIdRegistry.migrate(
                NAME_OF_ENTITY_ID_SERVICE,
                fakeState,
                deserializedPbjVersion,
                networkInfo,
                nodeConfiguration,
                sharedValues,
                prevEntityNum);
        registry.registrations().stream()
                .filter(r -> !Objects.equals(entityIdRegistration, r))
                .forEach(registration -> {
                    if (!(registration.registry() instanceof FakeSchemaRegistry schemaRegistry)) {
                        throw new IllegalArgumentException("Can only be used with FakeSchemaRegistry instances");
                    }
                    schemaRegistry.migrate(
                            registration.serviceName(),
                            fakeState,
                            deserializedPbjVersion,
                            networkInfo,
                            nodeConfiguration,
                            sharedValues,
                            prevEntityNum);
                });
        final var entityIdWritableStates = fakeState.getWritableStates(NAME_OF_ENTITY_ID_SERVICE);
        if (!(entityIdWritableStates instanceof MapWritableStates mapWritableStates)) {
            throw new IllegalArgumentException("Can only be used with MapWritableStates instances");
        }
        mapWritableStates.getSingleton(NAME_OF_ENTITY_ID_SINGLETON).put(new EntityNumber(prevEntityNum.get()));
        mapWritableStates.commit();
        return List.of();
    }

    @Override
    public SemanticVersion creationVersionOf(@NonNull final State state) {
        if (!(state instanceof FakeState)) {
            throw new IllegalArgumentException("Can only be used with FakeState instances");
        }
        // Fake states are always from genesis and have no creation version
        return null;
    }
}
