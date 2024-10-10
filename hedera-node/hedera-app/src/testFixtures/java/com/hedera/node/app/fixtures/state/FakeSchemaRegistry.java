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

package com.hedera.node.app.fixtures.state;

import static com.hedera.node.app.state.merkle.SchemaApplicationType.MIGRATION;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.RESTART;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.STATE_DEFINITIONS;
import static com.swirlds.platform.test.fixtures.state.TestSchema.CURRENT_VERSION;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.FilteredReadableStates;
import com.hedera.node.app.spi.state.FilteredWritableStates;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FakeSchemaRegistry implements SchemaRegistry {
    private static final Logger logger = LogManager.getLogger(FakeSchemaRegistry.class);
    private final SchemaApplications schemaApplications = new SchemaApplications();

    /**
     * The ordered set of all schemas registered by the service
     */
    private final SortedSet<Schema> schemas = new TreeSet<>();

    @Override
    public SchemaRegistry register(@NonNull final Schema schema) {
        requireNonNull(schema);
        schemas.add(schema);
        return this;
    }

    @SuppressWarnings("rawtypes")
    public void migrate(
            @NonNull final String serviceName, @NonNull final FakeState state, @NonNull final NetworkInfo networkInfo) {
        migrate(
                serviceName,
                state,
                CURRENT_VERSION,
                networkInfo,
                ConfigurationBuilder.create().build(),
                new HashMap<>(),
                new AtomicLong());
    }

    public void migrate(
            @NonNull final String serviceName,
            @NonNull final FakeState state,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final Configuration config,
            @NonNull final Map<String, Object> sharedValues,
            @NonNull final AtomicLong nextEntityNum) {
        if (schemas.isEmpty()) {
            logger.info("Service {} does not use state", serviceName);
            return;
        }
        // For each schema, create the underlying raw data sources (maps, or lists) and the writable states that
        // will wrap them. Then call the schema's migrate method to populate those states, and commit each of them
        // to the underlying data sources. At that point, we have properly migrated the state.
        final var latestVersion = schemas.getLast().getVersion();
        logger.info(
                "Applying {} schemas for service {} and latest service schema version {}",
                schemas::size,
                () -> serviceName,
                () -> HapiUtils.toString(latestVersion));
        for (final var schema : schemas) {
            final var applications =
                    schemaApplications.computeApplications(previousVersion, latestVersion, schema, config);
            logger.info("Applying {} schema {} ({})", serviceName, schema.getVersion(), applications);
            final var readableStates = state.getReadableStates(serviceName);
            final var previousStates = new FilteredReadableStates(readableStates, readableStates.stateKeys());
            final WritableStates writableStates;
            final WritableStates newStates;
            if (applications.contains(STATE_DEFINITIONS)) {
                final var redefinedWritableStates = applyStateDefinitions(serviceName, schema, config, state);
                writableStates = redefinedWritableStates.beforeStates();
                newStates = redefinedWritableStates.afterStates();
            } else {
                newStates = writableStates = state.getWritableStates(serviceName);
            }
            final var context = newMigrationContext(
                    previousVersion, previousStates, newStates, config, networkInfo, nextEntityNum, sharedValues);
            if (applications.contains(MIGRATION)) {
                schema.migrate(context);
            }
            if (applications.contains(RESTART)) {
                schema.restart(context);
            }
            if (writableStates instanceof MapWritableStates mws) {
                mws.commit();
            }
            // And finally we can remove any states we need to remove
            schema.statesToRemove().forEach(stateKey -> state.removeServiceState(serviceName, stateKey));
        }
    }

    /**
     * Encapsulates the writable states before and after applying a schema's state definitions.
     *
     * @param beforeStates the writable states before applying the schema's state definitions
     * @param afterStates the writable states after applying the schema's state definitions
     */
    private record RedefinedWritableStates(WritableStates beforeStates, WritableStates afterStates) {}

    private RedefinedWritableStates applyStateDefinitions(
            @NonNull final String serviceName,
            @NonNull final Schema schema,
            @NonNull final Configuration configuration,
            @NonNull final FakeState state) {
        final Map<String, Object> stateDataSources = new HashMap<>();
        schema.statesToCreate(configuration).forEach(def -> {
            final var stateKey = def.stateKey();
            logger.info("  Ensuring {} has state {}", serviceName, stateKey);
            if (def.singleton()) {
                stateDataSources.put(def.stateKey(), new AtomicReference<>());
            } else if (def.queue()) {
                stateDataSources.put(def.stateKey(), new ConcurrentLinkedDeque<>());
            } else {
                stateDataSources.put(def.stateKey(), new ConcurrentHashMap<>());
            }
        });
        state.addService(serviceName, stateDataSources);

        final var statesToRemove = schema.statesToRemove();
        final var writableStates = state.getWritableStates(serviceName);
        final var remainingStates = new HashSet<>(writableStates.stateKeys());
        remainingStates.removeAll(statesToRemove);
        final var newStates = new FilteredWritableStates(writableStates, remainingStates);
        return new RedefinedWritableStates(writableStates, newStates);
    }

    private MigrationContext newMigrationContext(
            @Nullable final SemanticVersion previousVersion,
            @NonNull final ReadableStates previousStates,
            @NonNull final WritableStates writableStates,
            @NonNull final Configuration config,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final AtomicLong nextEntityNum,
            @NonNull final Map<String, Object> sharedValues) {
        return new MigrationContext() {
            @Override
            public void copyAndReleaseOnDiskState(String stateKey) {
                // No-op
            }

            @Override
            public SemanticVersion previousVersion() {
                return previousVersion;
            }

            @NonNull
            @Override
            public ReadableStates previousStates() {
                return previousStates;
            }

            @NonNull
            @Override
            public WritableStates newStates() {
                return writableStates;
            }

            @NonNull
            @Override
            public Configuration configuration() {
                return config;
            }

            @Override
            public NetworkInfo genesisNetworkInfo() {
                return networkInfo;
            }

            @Override
            public long newEntityNum() {
                return nextEntityNum.getAndIncrement();
            }

            @Override
            public Map<String, Object> sharedValues() {
                return sharedValues;
            }
        };
    }
}
