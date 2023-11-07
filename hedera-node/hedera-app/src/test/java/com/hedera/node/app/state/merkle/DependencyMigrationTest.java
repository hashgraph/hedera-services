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

package com.hedera.node.app.state.merkle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.OrderedServiceMigrator;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.fixtures.state.NoOpGenesisRecordsBuilder;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.config.VersionedConfigImpl;
import com.swirlds.common.constructable.ConstructableRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DependencyMigrationTest extends MerkleTestBase {
    private static final long INITIAL_ENTITY_ID = 5;

    @Mock
    private VersionedConfigImpl versionedConfig;

    @Mock
    private NetworkInfo networkInfo;

    private MerkleHederaState merkleTree;

    @BeforeEach
    void setUp() {
        registry = mock(ConstructableRegistry.class);
        merkleTree = new MerkleHederaState((tree, state) -> {}, (e, m, s) -> {}, (s, p, ds, t, dv) -> {});
    }

    @Nested
    @SuppressWarnings("DataFlowIssue")
    final class ConstructorTests {
        @Test
        void servicesRegistryRequired() {
            Assertions.assertThatThrownBy(() -> new OrderedServiceMigrator(null, mock(ThrottleAccumulator.class)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void throttleAccumulatorRequired() {
            Assertions.assertThatThrownBy(() -> new OrderedServiceMigrator(mock(ServicesRegistryImpl.class), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @SuppressWarnings("DataFlowIssue")
    @ExtendWith(MockitoExtension.class)
    final class DoMigrationsNullParams {
        @Mock
        private ServicesRegistryImpl servicesRegistry;

        @Mock
        private ThrottleAccumulator accumulator;

        @Test
        void stateRequired() {
            final var subject = new OrderedServiceMigrator(servicesRegistry, accumulator);
            Assertions.assertThatThrownBy(() ->
                            subject.doMigrations(null, SemanticVersion.DEFAULT, null, versionedConfig, networkInfo))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void currentVersionRequired() {
            final var subject =
                    new OrderedServiceMigrator(mock(ServicesRegistryImpl.class), mock(ThrottleAccumulator.class));
            Assertions.assertThatThrownBy(
                            () -> subject.doMigrations(merkleTree, null, null, versionedConfig, networkInfo))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void versionedConfigRequired() {
            final var subject =
                    new OrderedServiceMigrator(mock(ServicesRegistryImpl.class), mock(ThrottleAccumulator.class));
            Assertions.assertThatThrownBy(
                            () -> subject.doMigrations(merkleTree, SemanticVersion.DEFAULT, null, null, networkInfo))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void networkInfoRequired() {
            final var subject =
                    new OrderedServiceMigrator(mock(ServicesRegistryImpl.class), mock(ThrottleAccumulator.class));
            Assertions.assertThatThrownBy(() ->
                            subject.doMigrations(merkleTree, SemanticVersion.DEFAULT, null, versionedConfig, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("Inter-service dependency migration works")
    void genesisWithNullVersion() {
        // Given: we register the EntityIdService, followed by our DependentService
        final var servicesRegistry =
                new ServicesRegistryImpl(mock(ConstructableRegistry.class), new NoOpGenesisRecordsBuilder());
        final DependentService dsService = new DependentService();
        Set.of(entityServiceStartingWithInitialId(), dsService).forEach(servicesRegistry::register);

        // When: the migrations are run
        final var subject = new OrderedServiceMigrator(servicesRegistry, mock(ThrottleAccumulator.class));
        subject.doMigrations(
                merkleTree,
                SemanticVersion.newBuilder().major(2).build(),
                null,
                mock(VersionedConfigImpl.class),
                networkInfo);

        // Then: we verify the migrations had the desired effects on both entity ID state and DependentService state
        // First check that the entity ID service has an updated entity ID, despite its schema migration not doing
        // anything except setting the initial entity ID. DependentService's schema #2 should have caused the increments
        // with its new additions to its own state.
        final var postMigrationEntityIdState =
                merkleTree.createReadableStates(EntityIdService.NAME).getSingleton(EntityIdService.ENTITY_ID_STATE_KEY);
        assertThat(postMigrationEntityIdState.get()).isEqualTo(new EntityNumber(INITIAL_ENTITY_ID + 2));

        // Also verify that both of the DependentService's schema migrations took place. First the initial mappings are
        // created, then the new mappings dependent on the incrementing entity ID are added
        final var postMigrationDsState =
                merkleTree.createReadableStates(DependentService.NAME).get(DependentService.STATE_KEY);
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID - 1)).isEqualTo("previously added");
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID)).isEqualTo("last added");
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID + 1)).isEqualTo("newly-added 1");
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID + 2)).isEqualTo("newly-added 2");
    }

    private EntityIdService entityServiceStartingWithInitialId() {
        return new EntityIdService() {
            @Override
            public void registerSchemas(@NonNull SchemaRegistry registry) {
                registry.register(new Schema(SemanticVersion.DEFAULT) {
                    @NonNull
                    @Override
                    public Set<StateDefinition> statesToCreate() {
                        return Set.of(StateDefinition.singleton(ENTITY_ID_STATE_KEY, EntityNumber.PROTOBUF));
                    }

                    public void migrate(@NonNull MigrationContext ctx) {
                        final var entityIdState = ctx.newStates().getSingleton(ENTITY_ID_STATE_KEY);
                        entityIdState.put(new EntityNumber(INITIAL_ENTITY_ID));
                    }
                });
            }
        };
    }

    // This class represents a service that depends on EntityIdService. This class will create a simple mapping from an
    // entity ID to a string value.
    private static class DependentService implements Service {
        static final String NAME = "DependentService";
        static final String STATE_KEY = "DS_MAPPINGS";

        @NotNull
        @Override
        public String getServiceName() {
            return NAME;
        }

        public void registerSchemas(@NonNull SchemaRegistry registry) {
            // Schema #1 - initial schema
            registry.register(new Schema(SemanticVersion.DEFAULT) {
                @NonNull
                @Override
                public Set<StateDefinition> statesToCreate() {
                    return Set.of(StateDefinition.inMemory(STATE_KEY, LONG_CODEC, STRING_CODEC));
                }

                public void migrate(@NonNull final MigrationContext ctx) {
                    WritableStates dsWritableStates = ctx.newStates();
                    dsWritableStates.get(STATE_KEY).put(INITIAL_ENTITY_ID - 1, "previously added");
                    dsWritableStates.get(STATE_KEY).put(INITIAL_ENTITY_ID, "last added");
                }
            });

            // Schema #2 - schema that adds new mappings, dependent on EntityIdService
            registry.register(new Schema(SemanticVersion.newBuilder().major(2).build()) {
                public void migrate(@NonNull final MigrationContext ctx) {
                    final WritableStates dsWritableStates = ctx.newStates();
                    final var newEntityNum1 = ctx.newEntityNum();
                    dsWritableStates.get(STATE_KEY).put(newEntityNum1, "newly-added 1");
                    final var newEntityNum2 = ctx.newEntityNum();
                    dsWritableStates.get(STATE_KEY).put(newEntityNum2, "newly-added 2");
                }
            });
        }
    }
}
