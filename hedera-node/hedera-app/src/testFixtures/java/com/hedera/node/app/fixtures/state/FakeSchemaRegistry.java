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

package com.hedera.node.app.fixtures.state;

import com.hedera.node.app.spi.fixtures.state.ListWritableQueueState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.state.NoOpGenesisRecordsBuilder;
import com.hedera.node.app.spi.fixtures.throttle.FakeHandleThrottleParser;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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

                @NonNull
                @Override
                public GenesisRecordsBuilder genesisRecordsBuilder() {
                    return new NoOpGenesisRecordsBuilder();
                }

                @NonNull
                @Override
                public HandleThrottleParser handleThrottling() {
                    return new FakeHandleThrottleParser();
                }

                @Override
                public NetworkInfo networkInfo() {
                    return networkInfo;
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
