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

import com.hedera.node.app.spi.state.*;
import com.hedera.node.app.state.merkle.disk.OnDiskKeySerializer;
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
 * An implementation of the {@link StateRegistry} based on merkle trees. Each instance of this class
 * acts as a different namespace for a different {@link com.hedera.node.app.spi.Service}. A new
 * instance should be provided to each service instance, thereby ensuring that each has its own
 * unique namespace and cannot collide (intentionally or accidentally) with others.
 *
 * <p>The {@link StateRegistry} is used to create or configure a service states on the {@link
 * MerkleHederaState}. Each {@link com.hedera.node.app.spi.Service} must {@link #register(String)}
 * or {@link #remove(String, Parser, Parser, Writer, Writer, Ruler)} each and every item in the
 * saved state, and each and every item it needs. This happens <b>BEFORE</b> the saved state is
 * loaded. Then, after the saved state has been loaded, the {@link #migrate(MerkleHederaState)}
 * method is called to work on that state.
 *
 * @see StateRegistry
 */
/*@NotThreadSafe*/
public final class MerkleStateRegistry implements StateRegistry {
    private static final EmptyReadableStates EMPTY_READABLE_STATES = new EmptyReadableStates();
    /** The name of the service using this registry. */
    private final String serviceName;
    /** The registry to use when deserializing from saved states */
    private final ConstructableRegistry constructableRegistry;
    /** The location on disk where we should store on-disk state */
    private final Path storageDir;
    /** The version of the current application instance */
    private final SoftwareVersion currentVersion;
    /** The version of state we're starting from. May be null for genesis */
    private final SoftwareVersion previousVersion;
    /** The set of registrations made by the service module */
    private final Map<String, Registration> registrations;

    /**
     * Create a new instance.
     *
     * @param constructableRegistry The {@link ConstructableRegistry} to register states with for
     *     deserialization
     * @param serviceName The name of the service using this registry.
     * @param currentVersion The {@link SoftwareVersion} of the application instance
     * @param previousVersion The {@link SoftwareVersion} of the previous state (loaded from disk)
     */
    public MerkleStateRegistry(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final Path storageDir,
            @NonNull final String serviceName,
            @NonNull final SoftwareVersion currentVersion,
            @Nullable final SoftwareVersion previousVersion) {
        this.constructableRegistry = Objects.requireNonNull(constructableRegistry);
        this.storageDir = Objects.requireNonNull(storageDir);
        this.serviceName = StateUtils.validateStateKey(Objects.requireNonNull(serviceName));
        this.currentVersion = Objects.requireNonNull(currentVersion);
        this.previousVersion = previousVersion;
        this.registrations = new HashMap<>();
    }

    @NonNull
    @Override
    public SoftwareVersion getCurrentVersion() {
        return currentVersion;
    }

    @Nullable
    @Override
    public SoftwareVersion getPreviousVersion() {
        return previousVersion;
    }

    @NonNull
    @Override
    public StateRegistrationBuilder register(@NonNull final String stateKey) {
        StateUtils.validateStateKey(stateKey);
        return new MerkleStateRegistrationBuilder(stateKey);
    }

    @Override
    public <K, V> void remove(
            @NonNull final String stateKey,
            @NonNull final Parser<K> keyParser,
            @NonNull final Parser<V> valueParser,
            @NonNull final Writer<K> keyWriter,
            @NonNull final Writer<V> valueWriter,
            @Nullable final Ruler keyRuler) {

        StateUtils.validateStateKey(stateKey);
        Objects.requireNonNull(keyParser);
        Objects.requireNonNull(valueParser);
        Objects.requireNonNull(keyWriter);
        Objects.requireNonNull(valueWriter);

        // Remember this removal and apply it AFTER migrations have occurred.
        registrations.put(
                stateKey,
                Registration.remove(
                        stateKey, keyParser, valueParser, keyWriter, valueWriter, keyRuler, 0));
        // Register with the constructable registry system this state. When we deserialize
        // a saved state, we will deserialize a MerkleMap or VirtualMap for this old state,
        // and we will need to supply the parsers and writers to be able to work with it
        // (if it is used during migration) or for the system to hash everything to verify
        // the hash of the system is still intact.
        registerWithSystem(
                new StateMetadata<>(
                        serviceName,
                        stateKey,
                        keyParser,
                        valueParser,
                        keyWriter,
                        valueWriter,
                        keyRuler));
    }

    /**
     * Called by the application after saved states have been loaded to perform the migration.
     * During migration, we migrate all states we were told to migrate, and remove all states that
     * we were told to remove, in that order. It is possible that we were told to remove a state,
     * and also use it for migrating from. In this case, we migrate first, and then remove. It is
     * also possible that the same stateKey was used for removal and registration. In that case, we
     * do whatever we were told to do last (either remove, or registration).
     *
     * @param hederaState The {@link MerkleHederaState} instance for this registry to use.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void migrate(@NonNull final MerkleHederaState hederaState) {
        Objects.requireNonNull(hederaState);

        // When a service registers, it gives the builder the parsers and writers to use
        // for each state key that *should* exist in the old merkle tree (in addition to
        // any that will be removed). It may be that the service wants to migrate from
        // FOO_KEY (with some set of parsers and writers) and also register FOO_KEY (with
        // some other set of parsers and writers).
        //
        // Now, at the time of registration, we tell the ConstructableRegistry about FOO_KEY,
        // and we preferentially give it the parsers and writers defined by the "migrateFrom"
        // old states. That way, when it is read from disk, we are using whatever parsers
        // and writers make sense for the old state.
        //
        // Then, during migration, we go back to the ConstructableRegistry and tell it about
        // the new parsers and writers for FOO_KEY. It is OK to update the state node associated
        // with FOO_KEY whenever we want during, since the need for the old parsers and
        // writers has passed.
        //
        // It is important that we migrate first, and remove second. Because it may be that
        // BAR_KEY is used during migration and removed. We need to migrate first, or we
        // won't have the data available for migration.
        //
        // The `hederaState` supplied as an argument to this method is the working (mutable)
        // state. It has not yet been hashed, and this method is called once all children of
        // `hederaState` have been loaded from saved state.
        //
        // Suppose we have states FOO and BAR on disk. Suppose when we start our app, we
        // want to take the values in FOO and BAR and use them to create a new state BAZ.
        // Suppose we also want to migrate FOO to some new version, and we want to delete
        // BAR. For this to work, it is imperative that both FOO and BAZ, during migration,
        // see the *OLD* state of FOO. And we must perform all migrations before removing
        // things. We should allow either FOO or BAZ to migrate first. To do this, we need
        // to *NOT* commit the WritableState that represents FOO until after all migrations
        // are completed.

        // The set of states to commit after migration is completed.
        final Set<WritableState<?, ?>> statesToCommit = new HashSet<>();
        // We have to process each registration.
        for (final var reg : registrations.values()) {
            // Skip removals for now, since we need to create new states and migrate them
            // before we remove the old ones.
            if (!reg.toRemove) {
                // Step 1: Create the state if it does not already exist, or update it.
                final var md =
                        new StateMetadata<>(
                                serviceName,
                                reg.stateKey(),
                                reg.keyParser(),
                                reg.valueParser(),
                                reg.keyWriter(),
                                reg.valueWriter(),
                                reg.keyRuler());
                if (reg.inMemory) {
                    final var map = new MerkleMap<>();
                    map.setLabel(StateUtils.computeLabel(md.serviceName(), md.stateKey()));
                    hederaState.putServiceStateIfAbsent(md, map);
                    registerWithSystem(md);
                } else {
                    final var ds =
                            new JasperDbBuilder()
                                    .maxNumOfKeys(reg.maxNumOfKeys)
                                    .storageDir(storageDir)
                                    .keySerializer(new OnDiskKeySerializer(md))
                                    .virtualLeafRecordSerializer(
                                            new VirtualLeafRecordSerializer(
                                                    (short) 1,
                                                    DigestType.SHA_384,
                                                    (short) 1,
                                                    DataFileCommon.VARIABLE_DATA_SIZE,
                                                    new OnDiskKeySerializer(md),
                                                    (short) 1,
                                                    DataFileCommon.VARIABLE_DATA_SIZE,
                                                    new OnDiskValueSerializer(md),
                                                    true));

                    final var label = StateUtils.computeLabel(serviceName, reg.stateKey());
                    hederaState.putServiceStateIfAbsent(md, new VirtualMap<>(label, ds));
                    registerWithSystem(md);
                }

                // Step 2: For new and existing states, if there is an onMigrate handler defined,
                // then we will go through the migration work. We have to collect any "old states"
                // that the service registered for this migration, and pass it and the new state to
                // the handler.
                if (reg.onMigrate != null) {
                    final var oldStates =
                            reg.migrateFrom == null
                                    ? EMPTY_READABLE_STATES
                                    : new FilteredReadableStates(
                                            hederaState.createReadableStates(serviceName),
                                            reg.migrateFrom);

                    final var newStates = hederaState.createWritableStates(serviceName);
                    final var newState = newStates.get(reg.stateKey);
                    reg.onMigrate.onMigrate(oldStates, newState);
                    statesToCommit.add(newState);
                }
            }
        }

        // Now commit all the states that need to be committed
        statesToCommit.forEach(ws -> ((WritableStateBase) ws).commit());

        // Now that everything has been migrated, we can go back over the set of registrations
        // and remove the states that should be removed.
        for (final var reg : registrations.values()) {
            if (reg.toRemove) {
                hederaState.removeServiceState(serviceName, reg.stateKey);
            }
        }
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

            final Supplier<RuntimeConstructable> onDiskKeyCreator =
                    () -> new OnDiskKeySerializer<>(md);
            final var onDiskKeyPair =
                    new ClassConstructorPair(OnDiskKeySerializer.class, onDiskKeyCreator);
            constructableRegistry.registerConstructable(onDiskKeyPair);

            final Supplier<RuntimeConstructable> onDiskValueCreator =
                    () -> new OnDiskValueSerializer<>(md);
            final var onDiskValuePair =
                    new ClassConstructorPair(OnDiskValueSerializer.class, onDiskValueCreator);
            constructableRegistry.registerConstructable(onDiskValuePair);
        } catch (ConstructableRegistryException e) {
            // This is a fatal error.
            throw new RuntimeException(
                    "Failed to register with the system '"
                            + serviceName
                            + ":"
                            + md.stateKey()
                            + "'",
                    e);
        }
    }

    private record Registration(
            @NonNull String stateKey,
            boolean toRemove,
            boolean inMemory,
            @NonNull Parser keyParser,
            @NonNull Parser valueParser,
            @NonNull Writer keyWriter,
            @NonNull Writer valueWriter,
            @Nullable Ruler keyRuler,
            int maxNumOfKeys,
            @Nullable Set<String> migrateFrom,
            @Nullable MigrationHandler onMigrate) {

        public static Registration remove(
                @NonNull final String stateKey,
                @NonNull Parser keyParser,
                @NonNull Parser valueParser,
                @NonNull Writer keyWriter,
                @NonNull Writer valueWriter,
                @Nullable Ruler ruler,
                int maxNumOfKeys) {
            return new Registration(
                    stateKey,
                    true,
                    true,
                    keyParser,
                    valueParser,
                    keyWriter,
                    valueWriter,
                    ruler,
                    maxNumOfKeys,
                    null,
                    null);
        }

        public static Registration register(
                @NonNull final String stateKey,
                boolean inMemory,
                @NonNull Parser keyParser,
                @NonNull Parser valueParser,
                @NonNull Writer keyWriter,
                @NonNull Writer valueWriter,
                @Nullable Ruler ruler,
                int maxNumOfKeys,
                @Nullable Set<String> migrateFrom,
                @Nullable MigrationHandler onMigrate) {
            return new Registration(
                    stateKey,
                    false,
                    inMemory,
                    keyParser,
                    valueParser,
                    keyWriter,
                    valueWriter,
                    ruler,
                    maxNumOfKeys,
                    migrateFrom,
                    onMigrate);
        }
    }

    private final class MerkleStateRegistrationBuilder implements StateRegistrationBuilder {
        private final String stateKey;
        private Boolean inMemory = null;
        private Writer<?> keyWriter;
        private Writer<?> valueWriter;
        private Parser<?> keyParser;
        private Parser<?> valueParser;
        private Ruler keyRuler;
        private int maxNumOfKeys = 0;
        private final Set<String> migrateFrom = new HashSet<>();
        private MigrationHandler<?, ?> onMigrate;

        MerkleStateRegistrationBuilder(String stateKey) {
            this.stateKey = stateKey;
        }

        @NonNull
        @Override
        public <K> StateRegistrationBuilder keyWriter(@NonNull Writer<K> writer) {
            this.keyWriter = Objects.requireNonNull(writer);
            return this;
        }

        @NonNull
        @Override
        public <K> StateRegistrationBuilder keyParser(@NonNull Parser<K> parser) {
            this.keyParser = Objects.requireNonNull(parser);
            return this;
        }

        @NonNull
        @Override
        public <V> StateRegistrationBuilder valueWriter(@NonNull Writer<V> writer) {
            this.valueWriter = Objects.requireNonNull(writer);
            return this;
        }

        @NonNull
        @Override
        public <V> StateRegistrationBuilder valueParser(@NonNull Parser<V> parser) {
            this.valueParser = Objects.requireNonNull(parser);
            return this;
        }

        @NonNull
        @Override
        public StateRegistrationBuilder keyLength(@NonNull Ruler ruler) {
            this.keyRuler = ruler;
            return this;
        }

        @NonNull
        @Override
        public StateRegistrationBuilder memory() {
            inMemory = true;
            return this;
        }

        @NonNull
        @Override
        public StateRegistrationBuilder disk() {
            inMemory = false;
            return this;
        }

        @NonNull
        @Override
        public StateRegistrationBuilder maxNumOfKeys(int numKeys) {
            if (numKeys <= 0) {
                throw new IllegalArgumentException(
                        "You must specify a positive number for maxNumOfKeys");
            }

            this.maxNumOfKeys = numKeys;
            return this;
        }

        @NonNull
        @Override
        public <K, V> StateRegistrationBuilder addMigrationFrom(
                @NonNull String stateKey,
                @NonNull Parser<K> keyParser,
                @NonNull Parser<V> valueParser,
                @NonNull Writer<K> keyWriter,
                @NonNull Writer<V> valueWriter,
                @Nullable Ruler keyRuler) {
            registerWithSystem(
                    new StateMetadata<>(
                            serviceName,
                            stateKey,
                            keyParser,
                            valueParser,
                            keyWriter,
                            valueWriter,
                            keyRuler));
            migrateFrom.add(stateKey);
            return this;
        }

        @NonNull
        @Override
        public <K, V> StateRegistrationBuilder onMigrate(@NonNull MigrationHandler<K, V> handler) {
            this.onMigrate = Objects.requireNonNull(handler);
            return this;
        }

        @Override
        public void complete() {
            if (this.keyWriter == null) {
                throw new IllegalStateException("Must specify a key writer");
            }

            if (this.keyParser == null) {
                throw new IllegalStateException("Must specify a key parser");
            }

            if (this.valueWriter == null) {
                throw new IllegalStateException("Must specify a value writer");
            }

            if (this.valueParser == null) {
                throw new IllegalStateException("Must specify a value parser");
            }

            if (this.inMemory == null) {
                throw new IllegalStateException("Must specify either `memory` or `disk`");
            }

            if (!this.inMemory && this.keyRuler == null) {
                throw new IllegalStateException(
                        "Must specify 'keyLength` callback when using 'disk'");
            }

            if (!this.inMemory && this.maxNumOfKeys <= 0) {
                throw new IllegalStateException("Must specify `maxNumOfKeys` when using `disk`");
            }

            registrations.put(
                    stateKey,
                    Registration.register(
                            stateKey,
                            inMemory,
                            keyParser,
                            valueParser,
                            keyWriter,
                            valueWriter,
                            keyRuler,
                            maxNumOfKeys,
                            migrateFrom,
                            onMigrate));
            registerWithSystem(
                    new StateMetadata(
                            serviceName,
                            stateKey,
                            keyParser,
                            valueParser,
                            keyWriter,
                            valueWriter,
                            keyRuler));
        }
    }
}
