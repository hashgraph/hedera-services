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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.fixtures.state.FakeSchemaRegistry;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class FakeServiceMigrator implements ServiceMigrator {
    private static final String NAME_OF_ENTITY_ID_SERVICE = "EntityIdService";
    private static final String NAME_OF_ENTITY_ID_SINGLETON = "ENTITY_ID";

    @Override
    public void doMigrations(
            @NonNull final HederaState hederaState,
            @NonNull final ServicesRegistry servicesRegistry,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final SemanticVersion currentVersion,
            @NonNull final Configuration config,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final Metrics metrics) {
        requireNonNull(hederaState);
        requireNonNull(servicesRegistry);
        requireNonNull(currentVersion);
        requireNonNull(config);
        requireNonNull(networkInfo);
        requireNonNull(metrics);

        if (!(hederaState instanceof FakeHederaState state)) {
            throw new IllegalArgumentException("Can only be used with FakeHederaState instances");
        }
        if (!(servicesRegistry instanceof FakeServicesRegistry registry)) {
            throw new IllegalArgumentException("Can only be used with FakeServicesRegistry instances");
        }

        final AtomicLong nextEntityNum =
                new AtomicLong(config.getConfigData(HederaConfig.class).firstUserEntity());
        final Map<String, Object> sharedValues = new HashMap<>();
        final var entityIdRegistration = registry.registrations().stream()
                .filter(service ->
                        NAME_OF_ENTITY_ID_SERVICE.equals(service.service().getServiceName()))
                .findFirst()
                .orElseThrow();
        if (!(entityIdRegistration.registry() instanceof FakeSchemaRegistry entityIdRegistry)) {
            throw new IllegalArgumentException("Can only be used with FakeSchemaRegistry instances");
        }
        entityIdRegistry.migrate(
                NAME_OF_ENTITY_ID_SERVICE, state, previousVersion, networkInfo, config, sharedValues, nextEntityNum);
        registry.registrations().stream()
                .filter(r -> !Objects.equals(entityIdRegistration, r))
                .forEach(registration -> {
                    if (!(registration.registry() instanceof FakeSchemaRegistry schemaRegistry)) {
                        throw new IllegalArgumentException("Can only be used with FakeSchemaRegistry instances");
                    }
                    schemaRegistry.migrate(
                            registration.serviceName(),
                            state,
                            previousVersion,
                            networkInfo,
                            config,
                            sharedValues,
                            nextEntityNum);
                });
        final var entityIdWritableStates = state.getWritableStates(NAME_OF_ENTITY_ID_SERVICE);
        if (!(entityIdWritableStates instanceof MapWritableStates mapWritableStates)) {
            throw new IllegalArgumentException("Can only be used with MapWritableStates instances");
        }
        mapWritableStates.getSingleton(NAME_OF_ENTITY_ID_SINGLETON).put(new EntityNumber(nextEntityNum.get()));
        mapWritableStates.commit();
    }
}
