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

import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.test.fixtures.state.ListWritableQueueState;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class FakeSchemaRegistry implements SchemaRegistry {

    private final List<Schema> schemas = new LinkedList<>();

    @SuppressWarnings("rawtypes")
    public void migrate(
            @NonNull final String serviceName,
            @NonNull final FakeHederaState state,
            @NonNull final NetworkInfo networkInfo) {
        // For each schema, create the underlying raw data sources (maps, or lists) and the writable states that
        // will wrap them. Then call the schema's migrate method to populate those states, and commit each of them
        // to the underlying data sources. At that point, we have properly migrated the state.
        for (final var schema : schemas) {
            // Collect the data sources and the writable states
            final var dataSources = new HashMap<String, Object>();
            final var writables = new HashMap<String, Object>();
            for (final var sd : schema.statesToCreate()) {
                if (sd.queue()) {
                    final var dataSource = new LinkedList<>();
                    dataSources.put(sd.stateKey(), dataSource);
                    writables.put(sd.stateKey(), new ListWritableQueueState<>(sd.stateKey(), dataSource));
                } else if (sd.singleton()) {
                    final var dataSource = new AtomicReference();
                    dataSources.put(sd.stateKey(), dataSource);
                    writables.put(
                            sd.stateKey(),
                            new WritableSingletonStateBase<>(sd.stateKey(), dataSource::get, dataSource::set));
                } else {
                    final var dataSource = new HashMap<String, Object>();
                    dataSources.put(sd.stateKey(), dataSource);
                    writables.put(sd.stateKey(), new MapWritableKVState<>(sd.stateKey(), dataSource));
                }
            }

            // Run the migration which will populate the writable states
            final var previousStates = new EmptyReadableStates();
            final var writableStates = new MapWritableStates(writables);
            schema.migrate(new MigrationContext() {
                private final Map<String, Object> sharedValues = new HashMap<>();

                @Override
                public void copyAndReleaseOnDiskState(String stateKey) {
                    // No-op
                }

                @Override
                public SemanticVersion previousVersion() {
                    return CURRENT_VERSION;
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
                    return ConfigurationBuilder.create().build();
                }

                @Override
                public NetworkInfo networkInfo() {
                    return networkInfo;
                }

                @Override
                public long newEntityNum() {
                    return 0;
                }

                @Override
                public Map<String, Object> sharedValues() {
                    return sharedValues;
                }
            });

            // Now commit them all
            for (final var s : writables.values()) {
                if (s instanceof ListWritableQueueState listState) {
                    listState.commit();
                } else if (s instanceof MapWritableKVState mapState) {
                    mapState.commit();
                } else if (s instanceof WritableSingletonStateBase singletonState) {
                    singletonState.commit();
                } else {
                    throw new RuntimeException("Not yet supported here");
                }
            }

            if (!dataSources.isEmpty()) {
                state.addService(serviceName, dataSources);
            }
        }
    }

    @Override
    public SchemaRegistry register(@NonNull Schema schema) {
        schemas.add(schema);
        return this;
    }
}
