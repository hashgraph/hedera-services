/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.FilteredReadableStates;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskKeySerializer;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.state.merkle.disk.OnDiskValueSerializer;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.hedera.node.app.state.merkle.memory.InMemoryWritableState;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * An implementation of {@link SchemaRegistry}.
 *
 * <p>When the Hedera application starts, it creates an instance of {@link MerkleSchemaRegistry} for
 * each {@link Service}, and passes it to the service as part of construction. The {@link Service}
 * then registers each and every {@link Schema} that it has. Each {@link Schema} is associated with
 * a {@link SoftwareVersion}.
 *
 * <p>The Hedera application then calls {@link #migrate(MerkleHederaState, SoftwareVersion,
 * SoftwareVersion)} on each {@link MerkleSchemaRegistry} instance, supplying it the application
 * version number and the newly created (or deserialized) but not yet hashed copy of the {@link
 * MerkleHederaState}. The registry determines which {@link Schema}s to apply, possibly taking
 * multiple migration steps, to transition the merkle tree from its current version to the final
 * version.
 */
public class MerkleSchemaRegistry implements SchemaRegistry {
    /** The name of the service using this registry. */
    private final String serviceName;
    /** The location on disk where we should store on-disk state */
    private final Path storageDir;
    /** The registry to use when deserializing from saved states */
    private final ConstructableRegistry constructableRegistry;
    /** The ordered set of all schemas registered by the service */
    private final Set<Schema> schemas = new TreeSet<>();

    /**
     * Create a new instance.
     *
     * @param constructableRegistry The {@link ConstructableRegistry} to register states with for
     *     deserialization
     * @param storageDir A storage directory to store all virtual map data
     * @param serviceName The name of the service using this registry.
     */
    public MerkleSchemaRegistry(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final Path storageDir,
            @NonNull final String serviceName) {
        this.constructableRegistry = Objects.requireNonNull(constructableRegistry);
        this.storageDir = Objects.requireNonNull(storageDir);
        this.serviceName = StateUtils.validateStateKey(Objects.requireNonNull(serviceName));
    }

    /** {@inheritDoc} */
    @Override
    public void register(@NonNull Schema schema) {
        schemas.remove(schema);
        schemas.add(Objects.requireNonNull(schema));

        // Any states being created, need to be registered for deserialization
        schema.statesToCreate()
                .values()
                .forEach(
                        def -> {
                            //noinspection rawtypes,unchecked
                            final var md = new StateMetadata(serviceName, schema, def);
                            registerWithSystem(md);
                        });
    }

    /**
     * Called by the application after saved states have been loaded to perform the migration. Given
     * the supplied versions, applies all necessary migrations for every {@link Schema} newer than
     * {@code previousVersion} and no newer than {@code currentVersion}.
     *
     * @param hederaState The {@link MerkleHederaState} instance for this registry to use.
     * @param previousVersion The version of state loaded from disk. Possibly null.
     * @param currentVersion The current version. Never null. Must be newer than {@code
     *     previousVersion}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void migrate(
            @NonNull final MerkleHederaState hederaState,
            @Nullable final SoftwareVersion previousVersion,
            @NonNull final SoftwareVersion currentVersion) {
        Objects.requireNonNull(hederaState);
        Objects.requireNonNull(currentVersion);

        // If the previous and current versions are the same, then we have no need to migrate
        // to achieve the correct merkle tree version. All we need to do is register with
        // the MerkleHederaState the metadata for this version
        if (isSameVersion(previousVersion, currentVersion)) {
            final var schemasToApply = computeApplicableSchemas(null, currentVersion);
            final Map<String, StateMetadata<?, ?>> metadata = new HashMap<>();
            for (Schema schema : schemasToApply) {
                for (var def : schema.statesToCreate().values()) {
                    final var md = new StateMetadata<>(serviceName, schema, def);
                    metadata.put(def.stateKey(), md);
                }
                for (var stateKey : schema.statesToRemove()) {
                    metadata.remove(stateKey);
                }
            }

            for (var md : metadata.values()) {
                hederaState.putServiceStateIfAbsent(md);
            }

            return;
        }

        final var schemasToApply = computeApplicableSchemas(previousVersion, currentVersion);
        for (final var schema : schemasToApply) {
            // We just have one merkle tree -- the just-loaded working tree -- to work from.
            // We get a ReadableStates for everything in the current tree, but then wrap
            // it with a FilteredReadableStates that is locked into exactly the set of states
            // available at this moment in time. This is done to make sure that even after we
            // add new states into the tree, it doesn't increase the number of states that can
            // be seen by the schema migration code
            final var readableStates = hederaState.createReadableStates(serviceName);
            final var previousStates =
                    new FilteredReadableStates(readableStates, readableStates.stateKeys());

            // Create the new states (based on the schema) which, thanks to the above, does not
            // expand the set of states that the migration code will see
            final var statesToCreate = schema.statesToCreate();
            statesToCreate.forEach(
                    (stateKey, definition) -> {
                        final var md = new StateMetadata<>(serviceName, schema, definition);
                        if (!definition.onDisk()) {
                            final var map = new MerkleMap<>();
                            map.setLabel(StateUtils.computeLabel(serviceName, stateKey));
                            hederaState.putServiceStateIfAbsent(md, map);
                        } else {
                            final var ks = new OnDiskKeySerializer(md);
                            final var ds =
                                    new JasperDbBuilder()
                                            .maxNumOfKeys(definition.maxKeysHint())
                                            .storageDir(storageDir)
                                            .keySerializer(ks)
                                            .virtualLeafRecordSerializer(
                                                    new VirtualLeafRecordSerializer(
                                                            (short) 1,
                                                            DigestType.SHA_384,
                                                            (short) 1,
                                                            DataFileCommon.VARIABLE_DATA_SIZE,
                                                            ks,
                                                            (short) 1,
                                                            DataFileCommon.VARIABLE_DATA_SIZE,
                                                            new OnDiskValueSerializer(md),
                                                            true));

                            final var label = StateUtils.computeLabel(serviceName, stateKey);
                            hederaState.putServiceStateIfAbsent(md, new VirtualMap<>(label, ds));
                        }
                    });

            // Create the writable states. We won't commit anything from these states
            // until we have completed migration.
            final var newStates = hederaState.createWritableStates(serviceName);

            // Now we can migrate the schema and then commit all the changes
            schema.migrate(previousStates, newStates);
            newStates
                    .stateKeys()
                    .forEach(
                            stateKey ->
                                    // Reviewers: Should we promote "commit" to WritableKVState?
                                    ((WritableKVStateBase<?, ?>) newStates.get(stateKey)).commit());

            // And finally we can remove any states we need to remove
            final var statesToRemove = schema.statesToRemove();
            statesToRemove.forEach(
                    stateKey -> hederaState.removeServiceState(serviceName, stateKey));
        }
    }

    /**
     * Given two versions, gets the ordered list of all schemas that must be applied to transition
     * the merkle tree from some previousVersion to the currentVersion. If {@code previousVersion}
     * and {@code currentVersion} are the same, then an empty set is returned. In all other cases,
     * every registered {@link Schema} newer than {@code previousVersion} and less than or equal to
     * {@code currentVersion} will be returned.
     *
     * @param previousVersion The previous version of the merkle tree. May be null for genesis. Must
     *     be less than or equal to {@code currentVersion}.
     * @param currentVersion The current version of the application. May NOT be null under any
     *     condition. Must be greater than or equal to the {@code previousVersion}.
     * @return An ordered list of {@link Schema}s which, when applied in order, will transition the
     *     merkle tree from {@code previousVersion} to {@code currentVersion}.
     */
    @NonNull
    private List<Schema> computeApplicableSchemas(
            @Nullable final SoftwareVersion previousVersion,
            @NonNull final SoftwareVersion currentVersion) {

        // If the previous and current versions are the same, then we have no need to migrate
        // to achieve the correct merkle tree version.
        if (isSameVersion(previousVersion, currentVersion)) {
            return Collections.emptyList();
        }

        // The previous version MUST be strictly less than the current version
        if (!isSoOrdered(previousVersion, currentVersion)) {
            throw new IllegalArgumentException(
                    "The currentVersion must be strictly greater than the previousVersion");
        }

        // Evaluate each of the schemas (which are in ascending order by version, thanks
        // to the tree-set nature of our set) and select the subset that are newer than
        // the "previousVersion" and no newer than the currentVersion.
        final var applicableSchemas = new ArrayList<Schema>();
        for (Schema schema : schemas) {
            final var ver = schema.getVersion();
            if (isSameVersion(ver, currentVersion)
                    || isBetween(previousVersion, ver, currentVersion)) {
                applicableSchemas.add(schema);
            }
        }

        return applicableSchemas;
    }

    /**
     * Determines whether these two version are equal to each other. Both are equal if they are both
     * null, or have the same version number.
     *
     * @param a The first arg
     * @param b The second arg
     * @return true if both are null, or if both have the same version number
     */
    private boolean isSameVersion(
            @Nullable final SoftwareVersion a, @Nullable final SoftwareVersion b) {
        return a == null && b == null || a != null && a.compareTo(b) == 0;
    }

    private boolean isBetween(
            @Nullable final SoftwareVersion maybeFirst,
            @NonNull final SoftwareVersion maybeSecond,
            @NonNull final SoftwareVersion maybeThird) {
        return isSoOrdered(maybeFirst, maybeSecond) && isSoOrdered(maybeSecond, maybeThird);
    }

    /**
     * Determines if the two arguments are in the proper order, such that the first argument is
     * strictly lower than the second argument. If they are the same, we return false.
     *
     * @param maybeBefore The version we hope comes before {@code maybeAfter}
     * @param maybeAfter The version we hope comes after {@code maybeBefore}
     * @return True if, and only if, {@code maybeBefore} is a lower version number than {@code
     *     maybeAfter}.
     */
    private boolean isSoOrdered(
            @Nullable final SoftwareVersion maybeBefore,
            @NonNull final SoftwareVersion maybeAfter) {

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
        return maybeBefore.compareTo(maybeAfter) <= 0;
    }

    /**
     * Registers with the {@link ConstructableRegistry} system a class ID and a class. While this
     * will only be used for in-memory states, it is safe to register for on-disk ones as well.
     *
     * <p>The implementation will take the service name and the state key and compute a hash for it.
     * It will then convert the hash to a long, and use that as the class ID. It will then register
     * an {@link InMemoryWritableState}'s value merkle type to be deserialized, answering with the
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
            final Supplier<RuntimeConstructable> inMemoryValueCreator = () -> new InMemoryValue(md);
            final var inMemoryValuePair =
                    new ClassConstructorPair(InMemoryValue.class, inMemoryValueCreator);
            constructableRegistry.registerConstructable(inMemoryValuePair);

            final Supplier<RuntimeConstructable> onDiskKeyCreator = () -> new OnDiskKey<>(md);
            final var onDiskKeyPair = new ClassConstructorPair(OnDiskKey.class, onDiskKeyCreator);
            constructableRegistry.registerConstructable(onDiskKeyPair);

            final Supplier<RuntimeConstructable> onDiskKeySerializerCreator =
                    () -> new OnDiskKeySerializer<>(md);
            final var onDiskKeySerializerPair =
                    new ClassConstructorPair(OnDiskKeySerializer.class, onDiskKeySerializerCreator);
            constructableRegistry.registerConstructable(onDiskKeySerializerPair);

            final Supplier<RuntimeConstructable> onDiskValueCreator = () -> new OnDiskValue<>(md);
            final var onDiskValuePair =
                    new ClassConstructorPair(OnDiskValue.class, onDiskValueCreator);
            constructableRegistry.registerConstructable(onDiskValuePair);

            final Supplier<RuntimeConstructable> onDiskValueSerializerCreator =
                    () -> new OnDiskValueSerializer<>(md);
            final var onDiskValueSerializerPair =
                    new ClassConstructorPair(
                            OnDiskValueSerializer.class, onDiskValueSerializerCreator);
            constructableRegistry.registerConstructable(onDiskValueSerializerPair);
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
