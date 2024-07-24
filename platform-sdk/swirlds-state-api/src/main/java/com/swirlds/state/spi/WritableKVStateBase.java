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

package com.swirlds.state.spi;

import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapDeleteChange;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;

/**
 * A base class for implementations of {@link WritableKVState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public abstract class WritableKVStateBase<K, V> extends ReadableKVStateBase<K, V> implements WritableKVState<K, V> {
    /**
     * A map of all modified values buffered in this mutable state
     */
    private final Map<K, V> modifications = new LinkedHashMap<>();

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    protected WritableKVStateBase(@NonNull final String stateKey) {
        super(stateKey);
    }

    /**
     * A temporary method used for the block stream MVP to accumulate state changes.
     * @return the list of pending state changes
     */
    public List<StateChange> pendingStateChanges() {
        final List<StateChange> changes = new ArrayList<>();
        for (final var entry : modifications.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value == null) {
                changes.add(StateChange.newBuilder()
                        .stateName(getStateKey())
                        .mapDelete(MapDeleteChange.newBuilder().key(keyFor(key)).build())
                        .build());
            } else {
                changes.add(StateChange.newBuilder()
                        .stateName(getStateKey())
                        .mapUpdate(MapUpdateChange.newBuilder()
                                .key(keyFor(key))
                                .value(valueFor(value))
                                .build())
                        .build());
            }
        }
        return changes;
    }

    private MapChangeKey keyFor(final K key) {
        return switch (key) {
            case AccountID accountID -> MapChangeKey.newBuilder()
                    .accountIdKey(accountID)
                    .build();
            case EntityIDPair entityIDPair -> MapChangeKey.newBuilder()
                    .entityIdPairKey(entityIDPair)
                    .build();
            case EntityNumber entityNumber -> MapChangeKey.newBuilder()
                    .entityNumberKey(entityNumber)
                    .build();
            case FileID fileID -> MapChangeKey.newBuilder().fileIdKey(fileID).build();
            case NftID nftID -> MapChangeKey.newBuilder().nftIdKey(nftID).build();
            case ProtoLong protoLong -> MapChangeKey.newBuilder()
                    .protoLongKey(protoLong.value())
                    .build();
            case ProtoString protoString -> MapChangeKey.newBuilder()
                    .protoStringKey(protoString.value())
                    .build();
            case ScheduleID scheduleID -> MapChangeKey.newBuilder()
                    .scheduleIdKey(scheduleID)
                    .build();
            case SlotKey slotKey -> MapChangeKey.newBuilder()
                    .slotKeyKey(slotKey)
                    .build();
            case TokenID tokenID -> MapChangeKey.newBuilder()
                    .tokenIdKey(tokenID)
                    .build();
            case TopicID topicID -> MapChangeKey.newBuilder()
                    .topicIdKey(topicID)
                    .build();
            case ContractID contractID -> MapChangeKey.newBuilder()
                    .contractIdKey(contractID)
                    .build();
            default -> throw new IllegalStateException(
                    "Unrecognized key type " + key.getClass().getSimpleName());
        };
    }

    private MapChangeValue valueFor(final V value) {
        return switch (value) {
            case Account account -> MapChangeValue.newBuilder()
                    .accountValue(account)
                    .build();
            case AccountID accountID -> MapChangeValue.newBuilder()
                    .accountIdValue(accountID)
                    .build();
            case Bytecode bytecode -> MapChangeValue.newBuilder()
                    .bytecodeValue(bytecode)
                    .build();
            case File file -> MapChangeValue.newBuilder().fileValue(file).build();
            case Nft nft -> MapChangeValue.newBuilder().nftValue(nft).build();
            case ProtoString protoString -> MapChangeValue.newBuilder()
                    .protoStringValue(protoString.value())
                    .build();
            case Schedule schedule -> MapChangeValue.newBuilder()
                    .scheduleValue(schedule)
                    .build();
            case ScheduleList scheduleList -> MapChangeValue.newBuilder()
                    .scheduleListValue(scheduleList)
                    .build();
            case SlotValue slotValue -> MapChangeValue.newBuilder()
                    .slotValueValue(slotValue)
                    .build();
            case StakingNodeInfo stakingNodeInfo -> MapChangeValue.newBuilder()
                    .stakingNodeInfoValue(stakingNodeInfo)
                    .build();
            case Token token -> MapChangeValue.newBuilder().tokenValue(token).build();
            case TokenRelation tokenRelation -> MapChangeValue.newBuilder()
                    .tokenRelationValue(tokenRelation)
                    .build();
            case Topic topic -> MapChangeValue.newBuilder().topicValue(topic).build();
            default -> throw new IllegalStateException(
                    "Unexpected value: " + value.getClass().getSimpleName());
        };
    }

    /**
     * Flushes all changes into the underlying data store. This method should <strong>ONLY</strong>
     * be called by the code that created the {@link WritableKVStateBase} instance or owns it. Don't
     * cast and commit unless you own the instance!
     */
    public void commit() {
        for (final var entry : modifications.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value == null) {
                removeFromDataSource(key);
            } else {
                putIntoDataSource(key, value);
            }
        }
        reset();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the set of modified keys and removed keys. Equivalent semantically to a "rollback"
     * operation.
     */
    @Override
    public final void reset() {
        super.reset();
        modifications.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public final V get(@NonNull K key) {
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        if (modifications.containsKey(key)) {
            return modifications.get(key);
        } else {
            return super.get(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public V getOriginalValue(@NonNull K key) {
        return super.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public final V getForModify(@NonNull final K key) {
        Objects.requireNonNull(key);
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        if (modifications.containsKey(key)) {
            return modifications.get(key);
        }

        // If the modifications map does not contain an answer, but the read cache of the
        // super class does, then it means we've looked this up before but never modified it.
        // So we can just delegate to the super class.
        if (hasBeenRead(key)) {
            return super.get(key);
        }

        // We have not queried this key before, so let's look it up and store that we have
        // read this key. And then return the value.
        final var val = getForModifyFromDataSource(key);
        markRead(key, val);
        return val;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void put(@NonNull final K key, @NonNull final V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        modifications.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void remove(@NonNull final K key) {
        Objects.requireNonNull(key);
        modifications.put(key, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This is a bit of a pain, because we have to take into account modifications! If a key has
     * been removed, then it must be omitted during iteration. If a key has been added, then it must
     * be added into iteration.
     *
     * @return An iterator that iterates over all known keys.
     */
    @NonNull
    @Override
    public Iterator<K> keys() {
        // Capture the set of keys that have been removed, and the set of keys that have been added.
        final var removedKeys = new HashSet<K>();
        final var maybeAddedKeys = new HashSet<K>();
        for (final var mod : modifications.entrySet()) {
            final var key = mod.getKey();
            final var val = mod.getValue();
            if (val == null) {
                removedKeys.add(key);
            } else {
                maybeAddedKeys.add(key);
            }
        }

        // Get the iterator from the backing store
        final var backendItr = super.keys();

        // Create and return a special iterator which will only include those keys that
        // have NOT been removed, ARE in the backendItr, and includes keys that HAVE
        // been added (i.e. are not in the backendItr).
        return new KVStateKeyIterator<>(backendItr, removedKeys, maybeAddedKeys);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final Set<K> modifiedKeys() {
        return modifications.keySet();
    }

    /**
     * {@inheritDoc}
     * For the size of a {@link WritableKVState}, we need to take into account the size of the
     * underlying data source, and the modifications that have been made to the state.
     * <ol>
     * <li>if the key is in backing store and is removed in modifications, then it is counted as removed</li>
     * <li>if the key is not in backing store and is added in modifications, then it is counted as addition</li>
     * <li>if the key is in backing store and is added in modifications, then it is not counted as the
     * key already exists in state</li>
     * <li>if the key is not in backing store and is being tried to be removed in modifications,
     * then it is not counted as the key does not exist in state.</li>
     * </ol>
     *
     * @return The size of the state.
     */
    public long size() {
        final var sizeOfBackingMap = sizeOfDataSource();
        int numAdditions = 0;
        int numRemovals = 0;

        for (final var mod : modifications.entrySet()) {
            boolean isPresentInBackingMap = readFromDataSource(mod.getKey()) != null;
            boolean isRemovedInMod = mod.getValue() == null;

            if (isPresentInBackingMap && isRemovedInMod) {
                numRemovals++;
            } else if (!isPresentInBackingMap && !isRemovedInMod) {
                numAdditions++;
            }
        }
        return sizeOfBackingMap + numAdditions - numRemovals;
    }

    /**
     * Reads from the underlying data source in such a way as to cause any fast-copyable data
     * structures underneath to make a fast copy.
     *
     * @param key key to read from state
     * @return The value read from the underlying data source. May be null.
     */
    protected abstract V getForModifyFromDataSource(@NonNull K key);

    /**
     * Puts the given key/value pair into the underlying data source.
     *
     * @param key key to update
     * @param value value to put
     */
    protected abstract void putIntoDataSource(@NonNull K key, @NonNull V value);

    /**
     * Removes the given key and implicit value from the underlying data source.
     *
     * @param key key to remove from the underlying data source
     */
    protected abstract void removeFromDataSource(@NonNull K key);

    /**
     * Returns the size of the underlying data source. This can be a merkle map or a virtual map.
     *
     * @return size of the underlying data source.
     */
    protected abstract long sizeOfDataSource();

    /**
     * A special iterator which includes all keys in the backend iterator, and all keys that have
     * been added but are not part of the backend iterator, and excludes all keys that have been
     * removed (even if they are in the backend iterator).
     *
     * <p>For each key it gets back from the backing store, it must inspect that key to see if it is
     * in the removedKeys or maybeAddedKeys. If it is in the removedKeys, then we don't return it to
     * the caller, and pump another key from the backing store iterator to check instead. If it is
     * in maybeAddedKeys, then remove it from maybeAddedKeys (since it is clearly not added) and
     * then return it to the caller. At the very end, when the backing store iterator tells us it is
     * out of keys, we start going through everything in maybeAddedKeys.
     *
     * <p>This iterator is not fail-fast.
     *
     * @param <K> The type of key
     */
    private static final class KVStateKeyIterator<K> implements Iterator<K> {
        private final Iterator<K> backendItr;
        private final Set<K> removedKeys;
        private final Set<K> maybeAddedKeys;
        private Iterator<K> addedItr;
        private K next;

        private KVStateKeyIterator(
                @NonNull final Iterator<K> backendItr,
                @NonNull final Set<K> removedKeys,
                @NonNull final Set<K> maybeAddedKeys) {
            this.backendItr = backendItr;
            this.removedKeys = removedKeys;
            this.maybeAddedKeys = maybeAddedKeys;
        }

        @Override
        public boolean hasNext() {
            prepareNext();
            return next != null;
        }

        @Override
        public K next() {
            prepareNext();

            if (next == null) {
                throw new NoSuchElementException();
            }

            final var ret = next;
            next = null;
            return ret;
        }

        private void prepareNext() {
            while (next == null) {
                if (backendItr.hasNext()) {
                    final var candidate = backendItr.next();
                    maybeAddedKeys.remove(candidate);
                    if (removedKeys.contains(candidate)) {
                        continue;
                    }
                    next = candidate;
                    return;
                }

                if (addedItr == null) {
                    addedItr = maybeAddedKeys.iterator();
                }

                if (addedItr.hasNext()) {
                    next = addedItr.next();
                }

                // If we get here, then there is nothing.
                return;
            }
        }
    }
}
