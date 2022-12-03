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
import com.hedera.node.app.state.MutableStateBase;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;

/**
 * An implementation of the {@link StateRegistry} based on merkle trees. Each instance of this class
 * acts as a different namespace. A new instance should be provided to each service instance,
 * thereby ensuring that each has its own unique namespace and cannot collide (intentionally or
 * accidentally) with others.
 *
 * @see StateRegistry
 */
/*@NotThreadSafe*/
public final class StateRegistryImpl implements StateRegistry {
    /** The name of the service using this registry. */
    private final String serviceName;
    /** The registry to use when deserializing from saved states */
    private final ConstructableRegistry constructableRegistry;
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
     * @param previousVersion The {@link SoftwareVersion} of the previous state that we are loading
     *     from
     */
    public StateRegistryImpl(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final String serviceName,
            @NonNull final SoftwareVersion currentVersion,
            @Nullable final SoftwareVersion previousVersion) {
        this.constructableRegistry = Objects.requireNonNull(constructableRegistry);
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
    public SoftwareVersion getExistingVersion() {
        return previousVersion;
    }

    @Override
    public StateRegistrationBuilder register(@NonNull final String stateKey) {
        StateUtils.validateStateKey(stateKey);
        return new StateRegistrationBuilderImpl(stateKey);
    }

    @Override
    public <K, V> void remove(
            @NonNull String stateKey,
            @NonNull Parser<K> keyParser,
            @NonNull Parser<V> valueParser,
            @NonNull Writer<K> keyWriter,
            @NonNull Writer<V> valueWriter) {

        Objects.requireNonNull(keyParser);
        Objects.requireNonNull(valueParser);
        Objects.requireNonNull(keyWriter);
        Objects.requireNonNull(valueWriter);

        StateUtils.validateStateKey(stateKey);
        registrations.put(
                stateKey,
                Registration.remove(stateKey, keyParser, valueParser, keyWriter, valueWriter));
        registerWithSystem(stateKey, keyParser, valueParser, keyWriter, valueWriter);
    }

    /**
     * Called by the application after states have been loaded to perform the migration. During
     * migration, we remove all states that we were told to remove, and migrate all states we were
     * told to migrate.
     *
     * @param serviceMerkle The {@link ServiceStateNode} instance for this registry to use. Cannot
     *     be null.
     */
    public void migrate(@NonNull final ServiceStateNode serviceMerkle) {
        Objects.requireNonNull(serviceMerkle);
        // We have to process each registration
        for (final var reg : registrations.values()) {
            if (reg.toRemove) {
                // Skip removals for now
            } else {
                // Ok, some state is being registered. If we find that the state does not yet exist,
                // then we need to create it.
                final var existingMerkle = serviceMerkle.find(reg.stateKey);
                if (existingMerkle == null) {
                    MerkleNode merkleNode;
                    if (reg.inMemory) {
                        merkleNode = new MerkleMap<>();
                    } else {
                        // TODO Need to do a proper initialization of the virtual map. Not sure how
                        // to do that yet.
                        //      Or how to test it properly.
                        merkleNode =
                                new VirtualMap<>(
                                        "label",
                                        new JasperDbBuilder<>()
                                                .keySerializer(null)
                                                .virtualLeafRecordSerializer(null)
                                                .virtualInternalRecordSerializer(null));
                    }

                    serviceMerkle.put(reg.stateKey, merkleNode);
                }

                // Now, whether it existed before or not, if there is an onMigrate handler defined,
                // then
                // we will go through the migration work. We have to collect any "old states" that
                // the
                // service registered for this migration, and pass it and the new state to the
                // handler.
                if (reg.onMigrate != null) {
                    // Load the old states, if there are any to load
                    final var oldStates = new HashMap<String, ReadableState>();
                    if (reg.migrateFrom != null) {
                        for (final var from : reg.migrateFrom) {
                            final var oldMerkle = serviceMerkle.find(from.stateKey);
                            if (oldMerkle != null) {
                                oldStates.put(
                                        from.stateKey,
                                        asState(
                                                from.stateKey,
                                                oldMerkle,
                                                from.keyParser,
                                                from.valueParser,
                                                from.keyWriter,
                                                from.valueWriter));
                            }
                        }
                    }

                    try {
                        final var newState =
                                asState(
                                        reg.stateKey,
                                        serviceMerkle.find(reg.stateKey),
                                        reg.keyParser,
                                        reg.valueParser,
                                        reg.keyWriter,
                                        reg.valueWriter);
                        reg.onMigrate.onMigrate(newState, oldStates);
                        newState.commit();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        for (final var reg : registrations.values()) {
            if (reg.toRemove) {
                // If during construction the service asked for some state to be removed, then we
                // will
                // remove it now.
                serviceMerkle.remove(reg.stateKey);
            }
        }
    }

    /**
     * Registers with the {@link ConstructableRegistry} system a class ID and a class. While this
     * will only be used for in-memory states, it is safe to register for on-disk ones as well.
     *
     * <p>The implementation will take the service name and the state key and compute a hash for it.
     * It will then convert the hash to a long, and use that as the class ID. It will then register
     * an {@link InMemoryState}'s value merkle type to be deserialized, answering with the generated
     * class ID.
     *
     * @param stateKey The state key
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerWithSystem(
            @NonNull String stateKey,
            @NonNull Parser<?> keyParser,
            @NonNull Parser<?> valueParser,
            @NonNull Writer<?> keyWriter,
            @NonNull Writer<?> valueWriter) {
        // Register with the system the uniqueId as the "classId" of an InMemoryValue. There can be
        // multiple
        // id's associated with InMemoryValue. The secret is that the supplier captures the various
        // delegate
        // writers and parsers, and so can parse/write different types of data based on the id.
        try {
            final long classId = StateUtils.computeClassId(serviceName, stateKey);
            final var pair =
                    new ClassConstructorPair(
                            InMemoryValue.class,
                            () ->
                                    new InMemoryValue(
                                            classId,
                                            keyParser,
                                            valueParser,
                                            keyWriter,
                                            valueWriter));
            constructableRegistry.registerConstructable(pair);
        } catch (ConstructableRegistryException e) {
            // This is a fatal error.
            throw new RuntimeException(
                    "Failed to register with the system '" + serviceName + ":" + stateKey + "'", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <K, V> MutableStateBase<K, V> asState(
            @NonNull final String stateKey,
            @Nullable final MerkleNode merkleNode,
            @NonNull final Parser<?> keyParser,
            @NonNull final Parser<?> valueParser,
            @NonNull final Writer<?> keyWriter,
            @NonNull final Writer<?> valueWriter) {

        if (merkleNode == null) {
            return null;
        }

        if (merkleNode instanceof MerkleMap mmap) {
            return new InMemoryState(
                    stateKey,
                    mmap,
                    StateUtils.computeClassId(serviceName, stateKey),
                    keyParser,
                    valueParser,
                    keyWriter,
                    valueWriter);
        } else if (merkleNode instanceof VirtualMap vmap) {
            return new OnDiskState(stateKey, vmap);
        } else {
            throw new IllegalStateException("The merkle node was of an unsupported type!");
        }
    }

    private record MigrateFromRegistration(
            @NonNull String stateKey,
            @NonNull Parser<?> keyParser,
            @NonNull Parser<?> valueParser,
            @NonNull Writer<?> keyWriter,
            @NonNull Writer<?> valueWriter) {}

    private record Registration(
            @NonNull String stateKey,
            boolean toRemove,
            boolean inMemory,
            @NonNull Parser<?> keyParser,
            @NonNull Parser<?> valueParser,
            @NonNull Writer<?> keyWriter,
            @NonNull Writer<?> valueWriter,
            @Nullable Set<MigrateFromRegistration> migrateFrom,
            @Nullable MigrationHandler onMigrate) {

        public static Registration remove(
                @NonNull final String stateKey,
                @NonNull Parser<?> keyParser,
                @NonNull Parser<?> valueParser,
                @NonNull Writer<?> keyWriter,
                @NonNull Writer<?> valueWriter) {
            return new Registration(
                    stateKey,
                    true,
                    true,
                    keyParser,
                    valueParser,
                    keyWriter,
                    valueWriter,
                    null,
                    null);
        }

        public static Registration register(
                @NonNull final String stateKey,
                boolean inMemory,
                @NonNull Parser<?> keyParser,
                @NonNull Parser<?> valueParser,
                @NonNull Writer<?> keyWriter,
                @NonNull Writer<?> valueWriter,
                @Nullable Set<MigrateFromRegistration> migrateFrom,
                @Nullable MigrationHandler<?, ?> onMigrate) {
            return new Registration(
                    stateKey,
                    false,
                    inMemory,
                    keyParser,
                    valueParser,
                    keyWriter,
                    valueWriter,
                    migrateFrom,
                    onMigrate);
        }
    }

    private final class StateRegistrationBuilderImpl implements StateRegistrationBuilder {
        private final String stateKey;
        private Boolean inMemory = null;
        private Writer<?> keyWriter;
        private Writer<?> valueWriter;
        private Parser<?> keyParser;
        private Parser<?> valueParser;
        private final Set<MigrateFromRegistration> migrateFrom = new HashSet<>();
        private MigrationHandler<?, ?> onMigrate;

        StateRegistrationBuilderImpl(String stateKey) {
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
        public <K, V> StateRegistrationBuilder migrateFrom(
                @NonNull String stateKey,
                @NonNull Parser<K> keyParser,
                @NonNull Parser<V> valueParser,
                @NonNull Writer<K> keyWriter,
                @NonNull Writer<V> valueWriter) {
            registerWithSystem(stateKey, keyParser, valueParser, keyWriter, valueWriter);
            migrateFrom.add(
                    new MigrateFromRegistration(
                            stateKey, keyParser, valueParser, keyWriter, valueWriter));
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

            registrations.put(
                    stateKey,
                    Registration.register(
                            stateKey,
                            inMemory,
                            keyParser,
                            valueParser,
                            keyWriter,
                            valueWriter,
                            migrateFrom,
                            onMigrate));
            registerWithSystem(stateKey, keyParser, valueParser, keyWriter, valueWriter);
        }
    }
}
