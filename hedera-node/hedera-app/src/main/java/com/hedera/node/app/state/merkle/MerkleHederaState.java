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
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.merkle.disk.OnDiskReadableKVState;
import com.hedera.node.app.state.merkle.disk.OnDiskWritableKVState;
import com.hedera.node.app.state.merkle.memory.InMemoryReadableKVState;
import com.hedera.node.app.state.merkle.memory.InMemoryWritableKVState;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An implementation of {@link SwirldState2} and {@link HederaState}. The Hashgraph Platform
 * communicates with the application through {@link com.swirlds.common.system.SwirldMain} and {@link
 * SwirldState2}. The Hedera application, after startup, only needs the ability to get {@link
 * ReadableStates} and {@link WritableStates} from this object.
 *
 * <p>Among {@link MerkleHederaState}'s child nodes are the various {@link
 * com.swirlds.merkle.map.MerkleMap}'s and {@link com.swirlds.virtualmap.VirtualMap}'s that make up
 * the service's states. Each such child node has a label specified that is computed from the
 * metadata for that state. Since both service names and state keys are restricted to characters
 * that do not include the period, we can use it to separate service name from state key. When we
 * need to find all states for a service, we can do so by iteration and string comparison.
 *
 * <p>NOTE: The implementation of this class must change before we can support state proofs
 * properly. In particular, a wide n-ary number of children is less than ideal, since the hash of
 * each child must be part of the state proof. It would be better to have a binary tree. We should
 * consider nesting service nodes in a MerkleMap, or some other such approach to get a binary tree.
 */
public class MerkleHederaState extends PartialNaryMerkleInternal implements MerkleInternal, SwirldState2, HederaState {

    /** Used when asked for a service's readable states that we don't have */
    private static final ReadableStates EMPTY_READABLE_STATES = new EmptyReadableStates();
    /** Used when asked for a service's writable states that we don't have */
    private static final WritableStates EMPTY_WRITABLE_STATES = new EmptyWritableStates();

    // For serialization
    private static final long CLASS_ID = 0x2de3ead3caf06392L;
    private static final int VERSION_1 = 1;
    private static final int CURRENT_VERSION = VERSION_1;

    /**
     * This callback is invoked whenever the consensus round happens. The Hashgraph Platform, today,
     * only communicates the consensus round through the {@link SwirldState2} interface. In the
     * future it will use a callback on a platform created via a platform builder. Until that
     * happens the only way our application will know of new transactions, will be through this
     * callback. Since this is not serialized and saved to state, it must be restored on application
     * startup. If this is never set, the application will never be able to handle a new round of
     * transactions.
     *
     * <p>This reference is moved forward to the working mutable state.
     */
    private BiConsumer<Round, SwirldDualState> onHandleConsensusRound;

    /**
     * This callback is invoked whenever there is an event to pre-handle.
     *
     * <p>This reference is moved forward to the working mutable state.
     */
    private Consumer<Event> onPreHandle;

    /**
     * This callback is invoked when the platform determines it is time to perform a migration. This
     * is supplied via the constructor, and so a custom entry in the ConstructableRegistry has to be
     * made to create this object.
     *
     * <p>This reference is only on the first, original state. It is not moved or copied forward to
     * later working mutable states.
     */
    private Consumer<MerkleHederaState> onMigrate;

    /**
     * Maintains information about each service, and each state of each service, known by this
     * instance. The key is the "service-name.state-key".
     */
    private final Map<String, Map<String, StateMetadata<?, ?>>> services = new HashMap<>();

    /**
     * Create a new instance. This constructor must be used for all creations of this class.
     *
     * @param onMigrate The callback to invoke when the platform deems it time to migrate
     * @param onPreHandle The callback to invoke when an event is ready for pre-handle
     * @param onHandleConsensusRound The callback invoked when the platform has
     */
    public MerkleHederaState(
            @NonNull final Consumer<MerkleHederaState> onMigrate,
            @NonNull final Consumer<Event> onPreHandle,
            @NonNull final BiConsumer<Round, SwirldDualState> onHandleConsensusRound) {
        this.onMigrate = Objects.requireNonNull(onMigrate);
        this.onPreHandle = Objects.requireNonNull(onPreHandle);
        this.onHandleConsensusRound = Objects.requireNonNull(onHandleConsensusRound);
    }

    /**
     * Private constructor for fast-copy.
     *
     * @param from The other state to fast-copy from. Cannot be null.
     */
    private MerkleHederaState(@NonNull final MerkleHederaState from) {
        // Copy the Merkle route from the source instance
        super(from);

        // Copy over the metadata
        for (final var entry : from.services.entrySet()) {
            this.services.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        // Copy the non-null Merkle children from the source (should also be handled by super, TBH).
        // Note we don't "compress" -- null children remain in here unless we manually remove them
        // (which would cause massive re-hashing).
        for (int childIndex = 0, n = from.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = from.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }

        // **MOVE** over the handle listener. Don't leave it on the old copy anymore.
        this.onHandleConsensusRound = from.onHandleConsensusRound;
        from.onHandleConsensusRound = null;

        // **MOVE** over the pre-handle. Don't leave it on the old copy anymore.
        this.onPreHandle = from.onPreHandle;
        from.onPreHandle = null;

        // **DO NOT** move over the onMigrate handler. We don't need it in subsequent
        // copies of the state
        this.onMigrate = null;
        from.onMigrate = null;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public ReadableStates createReadableStates(@NonNull final String serviceName) {
        final var stateMetadata = services.get(serviceName);
        return stateMetadata == null ? EMPTY_READABLE_STATES : new MerkleReadableStates(stateMetadata);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public WritableStates createWritableStates(@NonNull final String serviceName) {
        throwIfImmutable();
        final var stateMetadata = services.get(serviceName);
        return stateMetadata == null ? EMPTY_WRITABLE_STATES : new MerkleWritableStates(stateMetadata);
    }

    /**
     * @inheritDoc
     */
    @Override
    public MerkleHederaState copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new MerkleHederaState(this);
    }

    /**
     * @inheritDoc
     */
    @Override
    public AddressBook getAddressBookCopy() {
        // To be implemented by Issue #4200
        throw new RuntimeException("Not yet implemented");
    }

    /**
     * @inheritDoc
     */
    @Override
    public void handleConsensusRound(@NonNull final Round round, @NonNull final SwirldDualState swirldDualState) {
        if (onHandleConsensusRound != null) {
            onHandleConsensusRound.accept(round, swirldDualState);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void preHandle(Event event) {
        if (onPreHandle != null) {
            onPreHandle.accept(event);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public MerkleNode migrate(int ignored) {
        if (onMigrate != null) {
            onMigrate.accept(this);
        }

        // Always return this node, we never want to replace MerkleHederaState node in the tree
        return this;
    }

    <K extends Comparable<K>, V> void putServiceStateIfAbsent(@NonNull final StateMetadata<K, V> md) {
        throwIfImmutable();
        Objects.requireNonNull(md);
        final var stateMetadata = services.computeIfAbsent(md.serviceName(), k -> new HashMap<>());
        stateMetadata.put(md.stateDefinition().stateKey(), md);
    }

    /**
     * Puts the defined service state and its associated node into the merkle tree. The precondition
     * for calling this method is that node MUST be a {@link MerkleMap} or {@link VirtualMap} and
     * MUST have a correct label applied.
     *
     * @param md The metadata associated with the state
     * @param node The node to add. Cannot be null.
     * @throws IllegalArgumentException if the node is neither a merkle map nor virtual map, or if
     *     it doesn't have a label, or if the label isn't right.
     */
    <K extends Comparable<K>, V> void putServiceStateIfAbsent(
            @NonNull final StateMetadata<K, V> md, @NonNull final MerkleNode node) {

        // Validate the inputs
        throwIfImmutable();
        Objects.requireNonNull(md);
        Objects.requireNonNull(node);

        String label;
        if (node instanceof MerkleMap<?, ?> m) {
            label = m.getLabel();
        } else if (node instanceof VirtualMap<?, ?> v) {
            label = v.getLabel();
        } else {
            throw new IllegalArgumentException("`node` must be a VirtualMap or MerkleMap");
        }

        final var def = md.stateDefinition();
        if (def.onDisk() && !(node instanceof VirtualMap<?, ?>)) {
            throw new IllegalArgumentException(
                    "Mismatch: state definition claims on-disk, but " + "the merkle node is not a VirtualMap");
        }

        if (label == null || label.isEmpty()) {
            // It looks like both MerkleMap and VirtualMap do not allow for a null label.
            // But I want to leave this check in here anyway, in case that is ever changed.
            throw new IllegalArgumentException("A label must be specified on the node");
        }

        if (!label.equals(StateUtils.computeLabel(md.serviceName(), def.stateKey()))) {
            throw new IllegalArgumentException(
                    "A label must be computed based on the same " + "service name and state key in the metadata!");
        }

        // Put this metadata into the map
        final var stateMetadata = services.computeIfAbsent(md.serviceName(), k -> new HashMap<>());
        stateMetadata.put(def.stateKey(), md);

        // Look for a node, and if we don't find it, then insert the one we were given
        // If there is not a node there, then set it. I don't want to overwrite the existing node,
        // because it may have been loaded from state on disk, and the node provided here in this
        // call is always for genesis. So we may just ignore it.
        if (findNodeIndex(md.serviceName(), def.stateKey()) == -1) {
            setChild(getNumberOfChildren(), node);
        }
    }

    /**
     * Removes the node and metadata from the state merkle tree.
     *
     * @param serviceName The service name. Cannot be null.
     * @param stateKey The state key
     */
    void removeServiceState(@NonNull final String serviceName, @NonNull final String stateKey) {

        throwIfImmutable();
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(stateKey);

        // Remove the metadata entry
        final var stateMetadata = services.get(serviceName);
        if (stateMetadata != null) {
            stateMetadata.remove(stateKey);
        }

        // Remove the node
        final var index = findNodeIndex(serviceName, stateKey);
        if (index != -1) {
            setChild(index, null);
        }
    }

    /**
     * Simple utility method that finds the state node index.
     *
     * @param serviceName the service name
     * @param stateKey the state key
     * @return -1 if not found, otherwise the index into the children
     */
    private int findNodeIndex(@NonNull final String serviceName, @NonNull final String stateKey) {
        final var label = StateUtils.computeLabel(serviceName, stateKey);
        for (int i = 0, n = getNumberOfChildren(); i < n; i++) {
            final var node = getChild(i);
            if (node instanceof MerkleMap<?, ?> m && label.equals(m.getLabel())
                    || node instanceof VirtualMap<?, ?> v && label.equals(v.getLabel())) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Utility method for finding and returning the given node. Will throw an ISE if such a node
     * cannot be found!
     *
     * @param md The metadata
     * @return The found node
     */
    @NonNull
    private MerkleNode findNode(@NonNull StateMetadata<?, ?> md) {
        final var index = findNodeIndex(md.serviceName(), md.stateDefinition().stateKey());
        if (index == -1) {
            // This can only happen if there WAS a node here, and it was removed!
            throw new IllegalStateException("State '"
                    + md.stateDefinition().stateKey()
                    + "' for service '"
                    + md.serviceName()
                    + "' is missing from the merkle tree!");
        }

        return getChild(index);
    }

    /** An implementation of {@link ReadableStates} based on the merkle tree. */
    private final class MerkleReadableStates implements ReadableStates {
        private final Map<String, StateMetadata<?, ?>> stateMetadata;
        private final Map<String, ReadableKVState<?, ?>> instances;
        private final Set<String> stateKeys;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleReadableStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            this.stateMetadata = Objects.requireNonNull(stateMetadata);
            this.stateKeys = Collections.unmodifiableSet(stateMetadata.keySet());
            this.instances = new HashMap<>();
        }

        @NonNull
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
            final ReadableKVState<K, V> instance = (ReadableKVState<K, V>) instances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null) {
                throw new IllegalArgumentException("Unknown state key '" + stateKey + ";");
            }

            final var node = findNode(md);
            if (node instanceof VirtualMap v) {
                final var ret = new OnDiskReadableKVState<>(md, v);
                instances.put(stateKey, ret);
                return ret;
            } else if (node instanceof MerkleMap m) {
                final var ret = new InMemoryReadableKVState<>(md, m);
                instances.put(stateKey, ret);
                return ret;
            } else {
                // This method cannot possibly be reached. The findNodeIndex method ONLY
                // returns an index if the type is VirtualMap or MerkleMap. There is no
                // way to modify the merkle tree between the line that calls findNodeIndex
                // and here. So this can never be reached, even in testing!
                throw new IllegalStateException("Unexpected type for state " + stateKey);
            }
        }

        @Override
        public boolean contains(@NonNull String stateKey) {
            return stateMetadata.containsKey(stateKey);
        }

        @NonNull
        @Override
        public Set<String> stateKeys() {
            return stateKeys;
        }
    }

    /** An implementation of {@link WritableStates} based on the merkle tree. */
    private final class MerkleWritableStates implements WritableStates {
        private final Map<String, StateMetadata<?, ?>> stateMetadata;
        private final Map<String, WritableKVState<?, ?>> instances;
        private final Set<String> stateKeys;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleWritableStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            this.stateMetadata = Objects.requireNonNull(stateMetadata);
            this.stateKeys = Collections.unmodifiableSet(stateMetadata.keySet());
            this.instances = new HashMap<>();
        }

        @NonNull
        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <K extends Comparable<K>, V> WritableKVState<K, V> get(@NonNull String stateKey) {
            final WritableKVState<K, V> instance = (WritableKVState<K, V>) instances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null) {
                throw new IllegalArgumentException("Unknown state key '" + stateKey + "'");
            }

            final var node = findNode(md);
            if (node instanceof VirtualMap v) {
                final var ret = new OnDiskWritableKVState<>(md, v);
                instances.put(stateKey, ret);
                return ret;
            } else if (node instanceof MerkleMap m) {
                final var ret = new InMemoryWritableKVState<>(md, m);
                instances.put(stateKey, ret);
                return ret;
            } else {
                // This method cannot possibly be reached. The findNodeIndex method ONLY
                // returns an index if the type is VirtualMap or MerkleMap. There is no
                // way to modify the merkle tree between the line that calls findNodeIndex
                // and here. So this can never be reached, even in testing!
                throw new IllegalStateException("Unexpected type for state " + stateKey);
            }
        }

        @Override
        public boolean contains(@NonNull String stateKey) {
            return stateMetadata.containsKey(stateKey);
        }

        @NonNull
        @Override
        public Set<String> stateKeys() {
            return stateKeys;
        }
    }
}
