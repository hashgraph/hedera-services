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

import static com.hedera.node.app.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.FilteredReadableStates;
import com.hedera.node.app.spi.state.FilteredWritableStates;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskKeySerializer;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.disk.OnDiskValueSerializer;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.hedera.node.app.state.merkle.memory.InMemoryWritableKVState;
import com.hedera.node.app.state.merkle.queue.QueueNode;
import com.hedera.node.app.state.merkle.singleton.SingletonNode;
import com.hedera.node.app.state.merkle.singleton.StringLeaf;
import com.hedera.node.app.state.merkle.singleton.ValueLeaf;
import com.hedera.node.app.workflows.handle.record.MigrationContextImpl;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.config.api.Configuration;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
 * <p>The Hedera application then calls {@link #migrate(MerkleHederaState, SemanticVersion, SemanticVersion, Configuration, NetworkInfo, WritableEntityIdStore)} on each {@link MerkleSchemaRegistry} instance, supplying it the
 * application version number and the newly created (or deserialized) but not yet hashed copy of the {@link
 * MerkleHederaState}. The registry determines which {@link Schema}s to apply, possibly taking multiple migration steps,
 * to transition the merkle tree from its current version to the final version.
 */
public class MerkleSchemaRegistry implements SchemaRegistry {
    private static final Logger logger = LogManager.getLogger(MerkleSchemaRegistry.class);

    /**
     * The name of the service using this registry.
     */
    private final String serviceName;
    /**
     * The registry to use when deserializing from saved states
     */
    private final ConstructableRegistry constructableRegistry;
    /**
     * The ordered set of all schemas registered by the service
     */
    private final SortedSet<Schema> schemas = new TreeSet<>();
    /**
     * Stores system entities created during genesis until the node can build synthetic records
     */
    private final GenesisRecordsBuilder genesisRecordsBuilder;

    /**
     * Create a new instance.
     *
     * @param constructableRegistry The {@link ConstructableRegistry} to register states with for
     * deserialization
     * @param serviceName The name of the service using this registry.
     * @param genesisRecordsBuilder class used to store entities created at genesis
     */
    public MerkleSchemaRegistry(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final String serviceName,
            @NonNull final GenesisRecordsBuilder genesisRecordsBuilder) {
        this.constructableRegistry = requireNonNull(constructableRegistry);
        this.serviceName = StateUtils.validateStateKey(requireNonNull(serviceName));
        this.genesisRecordsBuilder = requireNonNull(genesisRecordsBuilder);
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
        schema.statesToCreate().forEach(def -> {
            //noinspection rawtypes,unchecked
            final var md = new StateMetadata<>(serviceName, schema, def);
            registerWithSystem(md);
        });

        return this;
    }

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
     * @param hederaState The {@link MerkleHederaState} instance for this registry to use.
     * @param previousVersion The version of state loaded from disk. Possibly null.
     * @param currentVersion The current version. Never null. Must be newer than {@code
     * previousVersion}.
     * @param config The system configuration to use at the time of migration
     * @param networkInfo The network information to use at the time of migration
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void migrate(
            @NonNull final MerkleHederaState hederaState,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final SemanticVersion currentVersion,
            @NonNull final Configuration config,
            @NonNull final NetworkInfo networkInfo,
            @Nullable final WritableEntityIdStore entityIdStore) {
        requireNonNull(hederaState);
        requireNonNull(currentVersion);
        requireNonNull(config);
        requireNonNull(networkInfo);

        // Figure out which schemas need to be applied based on the previous and current versions, and then for each
        // of those schemas, create the new states and remove the old states and migrate the data.
        final var schemasToApply = computeApplicableSchemas(previousVersion, currentVersion);
        if (schemasToApply.isEmpty()) {
            logger.info("Service {} does not use state", serviceName);
            return;
        }
        logger.info(
                "Migrating {} applicable schemas for service {} from {} to {}",
                schemasToApply::size,
                () -> serviceName,
                () -> HapiUtils.toString(previousVersion),
                () -> HapiUtils.toString(currentVersion));
        final var latestVersion = schemasToApply.getLast().getVersion();
        for (final var schema : schemasToApply) {
            final var applicationType = checkApplicationType(previousVersion, latestVersion, schema);
            logger.info("Applying {} schema {} ({})", serviceName, schema.getVersion(), applicationType);

            // Now we can migrate the schema and then commit all the changes
            // We just have one merkle tree -- the just-loaded working tree -- to work from.
            // We get a ReadableStates for everything in the current tree, but then wrap
            // it with a FilteredReadableStates that is locked into exactly the set of states
            // available at this moment in time. This is done to make sure that even after we
            // add new states into the tree, it doesn't increase the number of states that can
            // be seen by the schema migration code
            ReadableStates previousStatesIfNeeded = null;
            if (applicationType != SchemaApplicationType.ONLY_STATE_MANAGEMENT) {
                final var readableStates = hederaState.getReadableStates(serviceName);
                previousStatesIfNeeded = new FilteredReadableStates(readableStates, readableStates.stateKeys());
            }

            // Create the new states (based on the schema) which, thanks to the above, does not
            // expand the set of states that the migration code will see
            schema.statesToCreate().stream()
                    .sorted(Comparator.comparing(StateDefinition::stateKey))
                    .forEach(def -> {
                        final var stateKey = def.stateKey();
                        logger.info("  Ensuring {} has state {}", serviceName, stateKey);
                        final var md = new StateMetadata<>(serviceName, schema, def);
                        if (def.singleton()) {
                            hederaState.putServiceStateIfAbsent(md, () -> new SingletonNode<>(md, null));
                        } else if (def.queue()) {
                            hederaState.putServiceStateIfAbsent(md, () -> new QueueNode<>(md));
                        } else if (!def.onDisk()) {
                            hederaState.putServiceStateIfAbsent(md, () -> {
                                final var map = new MerkleMap<>();
                                map.setLabel(StateUtils.computeLabel(serviceName, stateKey));
                                return map;
                            });
                        } else {
                            hederaState.putServiceStateIfAbsent(md, () -> {
                                // MAX_IN_MEMORY_HASHES (ramToDiskThreshold) = 8388608
                                // PREFER_DISK_BASED_INDICES = false
                                final var tableConfig = new MerkleDbTableConfig<>(
                                                (short) 1,
                                                DigestType.SHA_384,
                                                (short) 1,
                                                new OnDiskKeySerializer<>(md),
                                                (short) 1,
                                                new OnDiskValueSerializer<>(md))
                                        .maxNumberOfKeys(def.maxKeysHint());
                                final var label = StateUtils.computeLabel(serviceName, stateKey);
                                final var dsBuilder = new MerkleDbDataSourceBuilder<>(tableConfig);
                                return new VirtualMap<>(label, dsBuilder);
                            });
                        }
                    });

            // Create the writable states. We won't commit anything from these states
            // until we have completed migration.
            final var writableStates = hederaState.getWritableStates(serviceName);
            final var statesToRemove = schema.statesToRemove();
            final var remainingStates = new HashSet<>(writableStates.stateKeys());
            remainingStates.removeAll(statesToRemove);
            final var newStates = new FilteredWritableStates(writableStates, remainingStates);

            if (applicationType != SchemaApplicationType.ONLY_STATE_MANAGEMENT) {
                // For any changes to state that depend on other services outside the current
                // service, we need a reference to the overall state that we can pass into the
                // context. This reference to overall state will be strictly controlled via the
                // MigrationContext API so that only changes explicitly specified in the
                // interface can be made (instead of allowing any arbitrary state change).
                final var migrationContext = new MigrationContextImpl(
                        requireNonNull(previousStatesIfNeeded),
                        newStates,
                        config,
                        networkInfo,
                        genesisRecordsBuilder,
                        entityIdStore,
                        previousVersion);
                if (applicationType != SchemaApplicationType.RESTART_ONLY) {
                    schema.migrate(migrationContext);
                }
                if (applicationType != SchemaApplicationType.MIGRATE_ONLY) {
                    schema.restart(migrationContext);
                }
            }

            // Now commit all the service-specific changes made during this service's update or migration
            if (writableStates instanceof MerkleHederaState.MerkleWritableStates mws) {
                mws.commit();
            }
            // And finally we can remove any states we need to remove
            statesToRemove.forEach(stateKey -> hederaState.removeServiceState(serviceName, stateKey));
        }
    }

    private SchemaApplicationType checkApplicationType(
            @Nullable final SemanticVersion previousVersionFromState,
            @NonNull final SemanticVersion latestRegisteredSchemaVersion,
            @NonNull final Schema schema) {
        // If the previous version is the same as the latest version, then we only need to restart
        // If this schema is the last registered schema, but is before the current version,
        // then we only need to restart. Since we apply atleast one schema(last registered schema)
        // if there are no schemas reported to migrate.
        if (previousVersionFromState != null
                && (isSameVersion(previousVersionFromState, latestRegisteredSchemaVersion)
                        || isSoOrdered(latestRegisteredSchemaVersion, previousVersionFromState))) {
            return SchemaApplicationType.RESTART_ONLY;
        } else if (isSameVersion(schema.getVersion(), latestRegisteredSchemaVersion)) {
            return SchemaApplicationType.MIGRATE_THEN_RESTART;
        } else if (!isSameVersion(schema.getVersion(), previousVersionFromState)) {
            return SchemaApplicationType.MIGRATE_ONLY;
        } else {
            return SchemaApplicationType.ONLY_STATE_MANAGEMENT;
        }
    }

    private enum SchemaApplicationType {
        /**
         * A schema whose version is the same as the version of the saved state,
         * but is not the latest version, has no migration work to do, and also
         * does not have priority for managing the service's restart logic.
         */
        ONLY_STATE_MANAGEMENT,
        /**
         * A schema whose version is after the version of the saved state, but
         * is not the latest version, has migration work to do, but also does
         * not have priority for managing the service's restart logic.
         */
        MIGRATE_ONLY,
        /**
         * A schema whose version is both the previous and latest version has
         * no migration work to do, but does have priority for managing the
         * service's restart logic.
         */
        RESTART_ONLY,
        /**
         * A schema whose version is after the version of the saved state, and
         * is also the latest version, has migration work to do, and also has
         * priority for managing the service's restart logic.
         */
        MIGRATE_THEN_RESTART
    }

    /**
     * Given two versions, gets the ordered list of all schemas that must be applied to transition
     * the merkle tree from some previousVersion to the currentVersion. If {@code previousVersion}
     * and {@code currentVersion} are the same, then an empty set is returned. In all other cases,
     * every registered {@link Schema} newer than {@code previousVersion} and less than or equal to
     * {@code currentVersion} will be returned.
     *
     * @param previousVersion The previous version of the merkle tree. May be null for genesis. Must
     * be less than or equal to {@code currentVersion}.
     * @param currentVersion The current version of the application. May NOT be null under any
     * condition. Must be greater than or equal to the {@code previousVersion}.
     * @return An ordered list of {@link Schema}s which, when applied in order, will transition the
     * merkle tree from {@code previousVersion} to {@code currentVersion}.
     */
    @NonNull
    private List<Schema> computeApplicableSchemas(
            @Nullable final SemanticVersion previousVersion, @NonNull final SemanticVersion currentVersion) {
        // The previous version MUST be strictly less than or equal to the current version
        if (!isSameVersion(previousVersion, currentVersion) && !isSoOrdered(previousVersion, currentVersion)) {
            throw new IllegalArgumentException("The currentVersion must be strictly greater than the previousVersion");
        }

        // Evaluate each of the schemas (which are in ascending order by version, thanks
        // to the tree-set nature of our set) and select the subset that are newer than
        // the "previousVersion" and no newer than the currentVersion.
        final var applicableSchemas = new ArrayList<Schema>();
        for (Schema schema : schemas) {
            final var ver = schema.getVersion();
            if (isSameVersion(ver, currentVersion) || isBetween(previousVersion, ver, currentVersion)) {
                applicableSchemas.add(schema);
            }
        }
        final List<Schema> registeredSchemas = schemas.isEmpty() ? List.of() : List.of(schemas.getLast());
        return applicableSchemas.isEmpty() ? registeredSchemas : applicableSchemas;
    }

    /**
     * Determines whether these two version are equal to each other. Both are equal if they are both
     * null, or have the same version number.
     *
     * @param a The first arg
     * @param b The second arg
     * @return true if both are null, or if both have the same version number
     */
    public static boolean isSameVersion(@Nullable final SemanticVersion a, @Nullable final SemanticVersion b) {
        return (a == null && b == null) || (a != null && b != null && SEMANTIC_VERSION_COMPARATOR.compare(a, b) == 0);
    }

    private boolean isBetween(
            @Nullable final SemanticVersion maybeFirst,
            @NonNull final SemanticVersion maybeSecond,
            @NonNull final SemanticVersion maybeThird) {
        return isSoOrdered(maybeFirst, maybeSecond) && isSoOrdered(maybeSecond, maybeThird);
    }

    /**
     * Determines if the two arguments are in the proper order, such that the first argument is
     * strictly lower than the second argument. If they are the same, we return false.
     *
     * @param maybeBefore The version we hope comes before {@code maybeAfter}
     * @param maybeAfter The version we hope comes after {@code maybeBefore}
     * @return True if, and only if, {@code maybeBefore} is a lower version number than {@code
     * maybeAfter}.
     */
    public static boolean isSoOrdered(
            @Nullable final SemanticVersion maybeBefore, @NonNull final SemanticVersion maybeAfter) {

        // If they are the same version, then we must fail.
        if (isSameVersion(maybeBefore, maybeAfter)) {
            return false;
        }

        // If the first argument is null, then the second argument always
        // comes later (since it must be non-null, or else isSameVersion
        // would have caught it).
        if (maybeBefore == null) {
            return true;
        }

        // If the comparison yields the first argument as being before
        // or matching the second argument, then we return true because
        // the condition we're testing for holds.
        return SEMANTIC_VERSION_COMPARATOR.compare(maybeBefore, maybeAfter) < 0;
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
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(InMemoryValue.class, () -> new InMemoryValue(md)));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(OnDiskKey.class, () -> new OnDiskKey<>(md)));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(OnDiskKeySerializer.class, () -> new OnDiskKeySerializer<>(md)));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(OnDiskValue.class, () -> new OnDiskValue<>(md)));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(OnDiskValueSerializer.class, () -> new OnDiskValueSerializer<>(md)));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(SingletonNode.class, () -> new SingletonNode<>(md, null)));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(QueueNode.class, () -> new QueueNode<>(md)));
            constructableRegistry.registerConstructable(new ClassConstructorPair(StringLeaf.class, StringLeaf::new));
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(ValueLeaf.class, () -> new ValueLeaf<>(md)));
        } catch (ConstructableRegistryException e) {
            // This is a fatal error.
            throw new RuntimeException(
                    "Failed to register with the system '"
                            + serviceName
                            + ":"
                            + md.stateDefinition().stateKey()
                            + "'",
                    e);
        }
    }
}
