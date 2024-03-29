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

package com.hedera.node.app.state.merkle;

import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;
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
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.constructable.ConstructableRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
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

    private static final HederaSoftwareVersion VERSION = new HederaSoftwareVersion(CURRENT_VERSION, CURRENT_VERSION, 0);
    private static final VersionedConfigImpl VERSIONED_CONFIG =
            new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), 1);
    private static final long INITIAL_ENTITY_ID = 5;

    @Mock
    private ThrottleAccumulator accumulator;

    @Mock
    private HederaLifecycles lifecycles;

    @Mock
    private NetworkInfo networkInfo;

    private MerkleHederaState merkleTree;

    @BeforeEach
    void setUp() {
        registry = mock(ConstructableRegistry.class);
        merkleTree = new MerkleHederaState(lifecycles);
    }

    @Nested
    @SuppressWarnings("DataFlowIssue")
    final class ConstructorTests {
        @Test
        void servicesRegistryRequired() {
            Assertions.assertThatThrownBy(() -> new OrderedServiceMigrator(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @SuppressWarnings("DataFlowIssue")
    @ExtendWith(MockitoExtension.class)
    final class DoMigrationsNullParams {
        @Mock
        private ServicesRegistryImpl servicesRegistry;

        @Test
        void stateRequired() {
            final var subject = new OrderedServiceMigrator(servicesRegistry);
            Assertions.assertThatThrownBy(
                            () -> subject.doMigrations(null, CURRENT_VERSION, null, VERSIONED_CONFIG, networkInfo))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void currentVersionRequired() {
            final var subject = new OrderedServiceMigrator(servicesRegistry);
            Assertions.assertThatThrownBy(
                            () -> subject.doMigrations(merkleTree, null, null, VERSIONED_CONFIG, networkInfo))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void versionedConfigRequired() {
            final var subject = new OrderedServiceMigrator(servicesRegistry);
            Assertions.assertThatThrownBy(
                            () -> subject.doMigrations(merkleTree, CURRENT_VERSION, null, null, networkInfo))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void networkInfoRequired() {
            final var subject = new OrderedServiceMigrator(servicesRegistry);
            Assertions.assertThatThrownBy(
                            () -> subject.doMigrations(merkleTree, CURRENT_VERSION, null, VERSIONED_CONFIG, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("Genesis inter-service dependency migration works")
    void genesisWithNullVersion() {
        // Given: register the EntityIdService and the DependentService (order of registration shouldn't matter)
        final var servicesRegistry = new ServicesRegistryImpl(registry, new NoOpGenesisRecordsBuilder());
        final var entityService = new EntityIdService() {
            @Override
            public void registerSchemas(
                    @NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
                registry.register(new Schema(version) {
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
        final DependentService dsService = new DependentService();
        Set.of(entityService, dsService).forEach(service -> servicesRegistry.register(service, VERSION));

        // When: the migrations are run
        final var subject = new OrderedServiceMigrator(servicesRegistry);
        subject.doMigrations(
                merkleTree, SemanticVersion.newBuilder().major(2).build(), null, VERSIONED_CONFIG, networkInfo);

        // Then: we verify the migrations had the desired effects on both entity ID state and DependentService state
        // First check that the entity ID service has an updated entity ID, despite its schema migration not doing
        // anything except setting the initial entity ID. DependentService's schema #2 should have caused the increments
        // with its new additions to its own state.
        final var postMigrationEntityIdState =
                merkleTree.getReadableStates(EntityIdService.NAME).getSingleton(EntityIdService.ENTITY_ID_STATE_KEY);
        assertThat(postMigrationEntityIdState.get()).isEqualTo(new EntityNumber(INITIAL_ENTITY_ID + 2));

        // Also verify that both of the DependentService's schema migrations took place. First the initial mappings are
        // created, then the new mappings dependent on the incrementing entity ID are added
        final var postMigrationDsState =
                merkleTree.getReadableStates(DependentService.NAME).get(DependentService.STATE_KEY);
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID - 1)).isEqualTo("previously added");
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID)).isEqualTo("last added");
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID + 1)).isEqualTo("newly-added 1");
        assertThat(postMigrationDsState.get(INITIAL_ENTITY_ID + 2)).isEqualTo("newly-added 2");
    }

    @Test
    @DisplayName("Service migrations are ordered as expected")
    void expectedMigrationOrdering() {
        final var orderedInvocations = new LinkedList<>();

        // Given: register four services, each with their own schema migration, that will add an object to
        // orderedInvocations during migration. We'll do this to track the order of the service migrations
        final var servicesRegistry = new ServicesRegistryImpl(registry, new NoOpGenesisRecordsBuilder());
        // Define the Entity ID Service:
        final EntityIdService entityIdService = new EntityIdService() {
            @Override
            public void registerSchemas(
                    @NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
                registry.register(new Schema(version) {
                    @NonNull
                    public Set<StateDefinition> statesToCreate() {
                        return Set.of(StateDefinition.singleton(ENTITY_ID_STATE_KEY, EntityNumber.PROTOBUF));
                    }

                    public void migrate(@NonNull MigrationContext ctx) {
                        orderedInvocations.add("EntityIdService#migrate");
                    }
                });
            }
        };
        // Define Service A:
        final var serviceA = new Service() {
            @NonNull
            @Override
            public String getServiceName() {
                return "A-Service";
            }

            @Override
            public void registerSchemas(
                    @NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
                registry.register(new Schema(version) {
                    public void migrate(@NonNull MigrationContext ctx) {
                        orderedInvocations.add("A-Service#migrate");
                    }
                });
            }
        };
        // Define Service B:
        final var serviceB = new Service() {
            @NonNull
            @Override
            public String getServiceName() {
                return "B-Service";
            }

            @Override
            public void registerSchemas(
                    @NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
                registry.register(new Schema(version) {
                    public void migrate(@NonNull MigrationContext ctx) {
                        orderedInvocations.add("B-Service#migrate");
                    }
                });
            }
        };
        // Define DependentService:
        final DependentService dsService = new DependentService() {
            @Override
            public void registerSchemas(
                    @NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
                registry.register(new Schema(version) {
                    public void migrate(@NonNull MigrationContext ctx) {
                        orderedInvocations.add("DependentService#migrate");
                    }
                });
            }
        };
        // Intentionally register the services in a different order than the expected migration order
        List.of(dsService, serviceA, entityIdService, serviceB)
                .forEach(service -> servicesRegistry.register(service, VERSION));

        // When: the migrations are run
        final var subject = new OrderedServiceMigrator(servicesRegistry);
        subject.doMigrations(
                merkleTree, SemanticVersion.newBuilder().major(1).build(), null, VERSIONED_CONFIG, networkInfo);

        // Then: we verify the migrations were run in the expected order
        Assertions.assertThat(orderedInvocations)
                .containsExactly(
                        // EntityIdService should be migrated first
                        "EntityIdService#migrate",
                        // And the rest are migrated by service name
                        "A-Service#migrate",
                        "B-Service#migrate",
                        "DependentService#migrate");
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

        public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
            // Schema #1 - initial schema
            registry.register(new Schema(version) {
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
