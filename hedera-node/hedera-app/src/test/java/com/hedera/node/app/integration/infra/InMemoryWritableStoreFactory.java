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

package com.hedera.node.app.integration.infra;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.network.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class InMemoryWritableStoreFactory implements WritableStoreFactory {
    private final Map<String, MapWritableStates> serviceStates = new HashMap<>();

    @Inject
    public InMemoryWritableStoreFactory() {
        final var services = Map.of(
                ConsensusService.NAME, new ConsensusServiceImpl(),
                ContractService.NAME, new ContractServiceImpl(),
                FileService.NAME, new FileServiceImpl(),
                FreezeService.NAME, new FreezeServiceImpl(),
                NetworkService.NAME, new NetworkServiceImpl(),
                ScheduleService.NAME, new ScheduleServiceImpl(),
                TokenService.NAME, new TokenServiceImpl(),
                UtilService.NAME, new UtilServiceImpl());
        services.forEach((name, service) -> serviceStates.put(name, inMemoryStatesFrom(service::registerSchemas)));
    }

    @Override
    public WritableTopicStore createTopicStore() {
        return new WritableTopicStore(serviceStates.get(ConsensusService.NAME));
    }

    @Override
    public WritableTokenStore createTokenStore() {
        return new WritableTokenStore(serviceStates.get(TokenService.NAME));
    }

    public Map<String, MapWritableStates> getServiceStates() {
        return serviceStates;
    }

    private MapWritableStates inMemoryStatesFrom(@NonNull final Consumer<SchemaRegistry> cb) {
        final var factory = new StatesBuildingSchemaRegistry();
        cb.accept(factory);
        return factory.build();
    }

    private static class StatesBuildingSchemaRegistry implements SchemaRegistry {
        private final MapWritableStates.Builder builder = new MapWritableStates.Builder();

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public SchemaRegistry register(@NonNull final Schema schema) {
            schema.statesToCreate().forEach(stateDefinition -> {
                if (stateDefinition.singleton()) {
                    final var accessor = new AtomicReference();
                    builder.state(
                            new WritableSingletonStateBase<>(stateDefinition.stateKey(), accessor::get, accessor::set));
                } else {
                    builder.state(new MapWritableKVState(stateDefinition.stateKey()));
                }
            });
            return this;
        }

        public MapWritableStates build() {
            return builder.build();
        }
    }
}
