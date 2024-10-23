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

import static com.hedera.node.app.state.merkle.SchemaApplicationType.MIGRATION;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.RESTART;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.STATE_DEFINITIONS;
import static com.hedera.node.app.state.merkle.VersionUtils.alreadyIncludesStateDefs;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.services.MigrationContextImpl;
import com.hedera.node.app.services.MigrationStateChanges;
import com.hedera.node.app.spi.state.FilteredReadableStates;
import com.hedera.node.app.spi.state.FilteredWritableStates;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.config.api.Configuration;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.state.State;
import com.swirlds.state.merkle.StateMetadata;
import com.swirlds.state.merkle.StateUtils;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskKeySerializer;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.state.merkle.disk.OnDiskValueSerializer;
import com.swirlds.state.merkle.memory.InMemoryValue;
import com.swirlds.state.merkle.memory.InMemoryWritableKVState;
import com.swirlds.state.merkle.queue.QueueNode;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.merkle.singleton.StringLeaf;
import com.swirlds.state.merkle.singleton.ValueLeaf;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link SchemaRegistry}.
 *
 * <p>When the Hedera application starts, it creates an instance of {@link MerkleSchemaRegistry} for
 * each {@link Service}, and passes it to the service as part of construction. The {@link Service}
 * then registers each and every {@link Schema} that it has. Each {@link Schema} is associated with
 * a {@link SemanticVersion}.
 *
 * <p>The Hedera application then calls {@code com.hedera.node.app.Hedera#onMigrate(MerkleStateRoot, HederaSoftwareVersion, InitTrigger, Metrics)} on each {@link MerkleSchemaRegistry} instance, supplying it the
 * application version number and the newly created (or deserialized) but not yet hashed copy of the {@link
 * MerkleStateRoot}. The registry determines which {@link Schema}s to apply, possibly taking multiple migration steps,
 * to transition the merkle tree from its current version to the final version.
 */
public class MerkleSchemaRegistry implements SchemaRegistry {
    private static final Logger logger = LogManager.getLogger(MerkleSchemaRegistry.class);

    /**
     * The name of the service using this registry.
     */
    private final String serviceName;
    /**
     * The current bootstrap configuration of the network; note this ideally would be a
     * provider of {@link com.hedera.node.config.VersionedConfiguration}s per version,
     * in case a service's states evolved with changing config. But this is a very edge
     * affordance that we have no example of needing.
     */
    private final Configuration bootstrapConfig;
    /**
     * The registry to use when deserializing from saved states
     */
    private final ConstructableRegistry constructableRegistry;
    /**
     * The ordered set of all schemas registered by the service
     */
    private final SortedSet<Schema> schemas = new TreeSet<>();
    /**
     * The analysis to use when determining how to apply a schema.
     */
    private final SchemaApplications schemaApplications;

    /**
     * Create a new instance with the default {@link SchemaApplications}.
     *
     * @param constructableRegistry The {@link ConstructableRegistry} to register states with for
     * deserialization
     * @param serviceName The name of the service using this registry.
     * @param schemaApplications the analysis to use when determining how to apply a schema
     */
    public MerkleSchemaRegistry(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final String serviceName,
            @NonNull final Configuration bootstrapConfig,
            @NonNull final SchemaApplications schemaApplications) {
        this.constructableRegistry = requireNonNull(constructableRegistry);
        this.serviceName = StateUtils.validateStateKey(requireNonNull(serviceName));
        this.bootstrapConfig = requireNonNull(bootstrapConfig);
        this.schemaApplications = requireNonNull(schemaApplications);
    }

    /**
     * {@inheritDoc}
     *
     * @return
     */
    @Override
    public SchemaRegistry register(@NonNull Schema schema) {
        schemas.remove(schema);
        schemas.add(requireNonNull(schema));
        logger.debug(
                "Registering schema {} for service {} ",
                () -> HapiUtils.toString(schema.getVersion()),
                () -> serviceName);

        // Any states being created, need to be registered for deserialization
        schema.statesToCreate(bootstrapConfig).forEach(def -> {
            //noinspection rawtypes,unchecked
            final var md = new StateMetadata<>(serviceName, schema, def);
            registerWithSystem(md);
        });

        return this;
    }

    /**
     * Encapsulates the writable states before and after applying a schema's state definitions.
     *
     * @param beforeStates the writable states before applying the schema's state definitions
     * @param afterStates the writable states after applying the schema's state definitions
     */
    private record RedefinedWritableStates(WritableStates beforeStates, WritableStates afterStates) {}

    /**
     * Called by the application after saved states have been loaded to perform the migration. Given
     * the supplied versions, applies all necessary migrations for every {@link Schema} newer than
     * {@code previousVersion} and no newer than {@code currentVersion}.
     *
     * <p>If the {@code previousVersion} and {@code currentVersion} are the same, then we do not need
     * to migrate, but instead we just call {@link Schema#restart(MigrationContext)} to allow the schema
     * to perform any necessary logic on restart. Most services have nothing to do, but some may need
     * to read files from disk, and could potentially change their state as a result.
     *
     * @param state     the state for this registry to use.
     * @param previousVersion The version of state loaded from disk. Possibly null.
     * @param currentVersion The current version. Never null. Must be newer than {@code
     * previousVersion}.
     * @param config The system configuration to use at the time of migration
     * @param genesisNetworkInfo The network information to use at the time of migration
     * @param sharedValues A map of shared values for cross-service migration patterns
     * @param migrationStateChanges Tracker for state changes during migration
     * @throws IllegalArgumentException if the {@code currentVersion} is not at least the
     *                                  {@code previousVersion} or if the {@code state} is not an instance of {@link MerkleStateRoot}
     */
    // too many parameters, commented out code
    @SuppressWarnings({"java:S107", "java:S125"})
    public void migrate(
            @NonNull final State state,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final SemanticVersion currentVersion,
            @NonNull final Configuration config,
            @Nullable final NetworkInfo genesisNetworkInfo,
            @NonNull final Metrics metrics,
            @Nullable final WritableEntityIdStore entityIdStore,
            @NonNull final Map<String, Object> sharedValues,
            @NonNull final MigrationStateChanges migrationStateChanges) {
        requireNonNull(state);
        requireNonNull(currentVersion);
        requireNonNull(config);
        requireNonNull(metrics);
        requireNonNull(sharedValues);
        requireNonNull(migrationStateChanges);
        if (isSoOrdered(currentVersion, previousVersion)) {
            throw new IllegalArgumentException("The currentVersion must be at least the previousVersion");
        }
        if (!(state instanceof MerkleStateRoot stateRoot)) {
            throw new IllegalArgumentException("The state must be an instance of " + MerkleStateRoot.class.getName());
        }
        if (schemas.isEmpty()) {
            logger.info("Service {} does not use state", serviceName);
            return;
        }
        final var latestVersion = schemas.getLast().getVersion();
        logger.info(
                "Applying {} schemas for service {} with state version {}, "
                        + "software version {}, and latest service schema version {}",
                schemas::size,
                () -> serviceName,
                () -> HapiUtils.toString(previousVersion),
                () -> HapiUtils.toString(currentVersion),
                () -> HapiUtils.toString(latestVersion));
        for (final var schema : schemas) {
            final var applications =
                    schemaApplications.computeApplications(previousVersion, latestVersion, schema, config);
            logger.info("Applying {} schema {} ({})", serviceName, schema.getVersion(), applications);
            // Now we can migrate the schema and then commit all the changes
            // We just have one merkle tree -- the just-loaded working tree -- to work from.
            // We get a ReadableStates for everything in the current tree, but then wrap
            // it with a FilteredReadableStates that is locked into exactly the set of states
            // available at this moment in time. This is done to make sure that even after we
            // add new states into the tree, it doesn't increase the number of states that can
            // be seen by the schema migration code
            final var readableStates = stateRoot.getReadableStates(serviceName);
            final var previousStates = new FilteredReadableStates(readableStates, readableStates.stateKeys());
            // Similarly, we distinguish between the writable states before and after
            // applying the schema's state definitions. This is done to ensure that we
            // commit all state changes made by applying this schema's state definitions;
            // but also prevent its migrate() and restart() hooks from accidentally using
            // states that were actually removed by this schema
            final WritableStates writableStates;
            final WritableStates newStates;
            if (applications.contains(STATE_DEFINITIONS)) {
                final var schemasAlreadyInState = schemas.tailSet(schema).stream()
                        .filter(s -> s != schema
                                && previousVersion != null
                                && alreadyIncludesStateDefs(previousVersion, s.getVersion()))
                        .toList();
                final var redefinedWritableStates =
                        applyStateDefinitions(schema, schemasAlreadyInState, config, metrics, stateRoot);
                writableStates = redefinedWritableStates.beforeStates();
                newStates = redefinedWritableStates.afterStates();
            } else {
                newStates = writableStates = stateRoot.getWritableStates(serviceName);
            }

            final var migrationContext = new MigrationContextImpl(
                    previousStates,
                    newStates,
                    config,
                    genesisNetworkInfo,
                    entityIdStore,
                    previousVersion,
                    sharedValues);
            if (applications.contains(MIGRATION)) {
                schema.migrate(migrationContext);
            }
            if (applications.contains(RESTART)) {
                schema.restart(migrationContext);
            }
            // Now commit all the service-specific changes made during this service's update or migration
            if (writableStates instanceof MerkleStateRoot.MerkleWritableStates mws) {
                mws.commit();
                migrationStateChanges.trackCommit();
            }
            // And finally we can remove any states we need to remove
            schema.statesToRemove().forEach(stateKey -> stateRoot.removeServiceState(serviceName, stateKey));
        }
    }

    private RedefinedWritableStates applyStateDefinitions(
            @NonNull final Schema schema,
            @NonNull final List<Schema> schemasAlreadyInState,
            @NonNull final Configuration configuration,
            @NonNull final Metrics metrics,
            @NonNull final MerkleStateRoot stateRoot) {
        // Create the new states (based on the schema) which, thanks to the above, does not
        // expand the set of states that the migration code will see
        schema.statesToCreate(configuration).stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .forEach(def -> {
                    final var stateKey = def.stateKey();
                    if (schemasAlreadyInState.stream()
                            .anyMatch(s -> s.statesToRemove().contains(stateKey))) {
                        logger.info("  Skipping {} as it is removed by a later schema", stateKey);
                        return;
                    }
                    logger.info("  Ensuring {} has state {}", serviceName, stateKey);
                    final var md = new StateMetadata<>(serviceName, schema, def);
                    if (def.singleton()) {
                        stateRoot.putServiceStateIfAbsent(
                                md,
                                () -> new SingletonNode<>(
                                        md.serviceName(),
                                        md.stateDefinition().stateKey(),
                                        md.singletonClassId(),
                                        md.stateDefinition().valueCodec(),
                                        null));

                    } else if (def.queue()) {
                        stateRoot.putServiceStateIfAbsent(
                                md,
                                () -> new QueueNode<>(
                                        md.serviceName(),
                                        md.stateDefinition().stateKey(),
                                        md.queueNodeClassId(),
                                        md.singletonClassId(),
                                        md.stateDefinition().valueCodec()));

                    } else if (!def.onDisk()) {
                        stateRoot.putServiceStateIfAbsent(md, () -> {
                            final var map = new MerkleMap<>();
                            map.setLabel(StateUtils.computeLabel(serviceName, stateKey));
                            return map;
                        });
                    } else {
                        stateRoot.putServiceStateIfAbsent(
                                md,
                                () -> {
                                    final var keySerializer = new OnDiskKeySerializer<>(
                                            md.onDiskKeySerializerClassId(),
                                            md.onDiskKeyClassId(),
                                            md.stateDefinition().keyCodec());
                                    final var valueSerializer = new OnDiskValueSerializer<>(
                                            md.onDiskValueSerializerClassId(),
                                            md.onDiskValueClassId(),
                                            md.stateDefinition().valueCodec());
                                    // MAX_IN_MEMORY_HASHES (ramToDiskThreshold) = 8388608
                                    // PREFER_DISK_BASED_INDICES = false
                                    final var tableConfig = new MerkleDbTableConfig((short) 1, DigestType.SHA_384)
                                            .maxNumberOfKeys(def.maxKeysHint());
                                    final var label = StateUtils.computeLabel(serviceName, stateKey);
                                    final var dsBuilder = new MerkleDbDataSourceBuilder(tableConfig);
                                    final var virtualMap =
                                            new VirtualMap<>(label, keySerializer, valueSerializer, dsBuilder);
                                    return virtualMap;
                                },
                                virtualMap -> virtualMap.registerMetrics(metrics));
                    }
                });

        // Create the "before" and "after" writable states (we won't commit anything
        // from these states until we have completed migration for this schema)
        final var statesToRemove = schema.statesToRemove();
        final var writableStates = stateRoot.getWritableStates(serviceName);
        final var remainingStates = new HashSet<>(writableStates.stateKeys());
        remainingStates.removeAll(statesToRemove);
        logger.info("  Removing states {} from service {}", statesToRemove, serviceName);
        final var newStates = new FilteredWritableStates(writableStates, remainingStates);
        return new RedefinedWritableStates(writableStates, newStates);
    }

    /**
     * Registers with the {@link ConstructableRegistry} system a class ID and a class. While this
     * will only be used for in-memory states, it is safe to register for on-disk ones as well.
     *
     * <p>The implementation will take the service name and the state key and compute a hash for it.
     * It will then convert the hash to a long, and use that as the class ID. It will then register
     * an {@link InMemoryWritableKVState}'s value merkle type to be deserialized, answering with the
     * generated class ID.
     *
     * @param md The state metadata
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerWithSystem(@NonNull final StateMetadata md) {
        // Register with the system the uniqueId as the "classId" of an InMemoryValue. There can be
        // multiple id's associated with InMemoryValue. The secret is that the supplier captures the
        // various delegate writers and parsers, and so can parse/write different types of data
        // based on the id.
        try {
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    InMemoryValue.class,
                    () -> new InMemoryValue(
                            md.inMemoryValueClassId(),
                            md.stateDefinition().keyCodec(),
                            md.stateDefinition().valueCodec())));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    OnDiskKey.class,
                    () -> new OnDiskKey<>(
                            md.onDiskKeyClassId(), md.stateDefinition().keyCodec())));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    OnDiskKeySerializer.class,
                    () -> new OnDiskKeySerializer<>(
                            md.onDiskKeySerializerClassId(),
                            md.onDiskKeyClassId(),
                            md.stateDefinition().keyCodec())));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    OnDiskValue.class,
                    () -> new OnDiskValue<>(
                            md.onDiskValueClassId(), md.stateDefinition().valueCodec())));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    OnDiskValueSerializer.class,
                    () -> new OnDiskValueSerializer<>(
                            md.onDiskValueSerializerClassId(),
                            md.onDiskValueClassId(),
                            md.stateDefinition().valueCodec())));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    SingletonNode.class,
                    () -> new SingletonNode<>(
                            md.serviceName(),
                            md.stateDefinition().stateKey(),
                            md.singletonClassId(),
                            md.stateDefinition().valueCodec(),
                            null)));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    QueueNode.class,
                    () -> new QueueNode<>(
                            md.serviceName(),
                            md.stateDefinition().stateKey(),
                            md.queueNodeClassId(),
                            md.singletonClassId(),
                            md.stateDefinition().valueCodec())));
            constructableRegistry.registerConstructable(new ClassConstructorPair(StringLeaf.class, StringLeaf::new));
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    ValueLeaf.class,
                    () -> new ValueLeaf<>(
                            md.singletonClassId(), md.stateDefinition().valueCodec())));
        } catch (ConstructableRegistryException e) {
            // This is a fatal error.
            throw new IllegalStateException(
                    "Failed to register with the system '"
                            + serviceName
                            + ":"
                            + md.stateDefinition().stateKey()
                            + "'",
                    e);
        }
    }
}
