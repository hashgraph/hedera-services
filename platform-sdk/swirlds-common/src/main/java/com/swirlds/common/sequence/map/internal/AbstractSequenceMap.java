/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.sequence.map.internal;

import com.swirlds.common.sequence.map.SequenceMap;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Boilerplate implementation for {@link SequenceMap}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public abstract class AbstractSequenceMap<K, V> implements SequenceMap<K, V> {

    /**
     * The data in the map.
     */
    private final Map<K, V> data;

    /**
     * Keys for each sequence number currently being stored.
     */
    private final SequenceKeySet<K>[] keySets;

    private final int sequenceNumberCapacity;

    /**
     * A method that gets the sequence number associated with a given key.
     */
    private final ToLongFunction<K> getSequenceNumberFromKey;

    /**
     * When this object is cleared, the lowest allowed sequence number is reset to this value.
     */
    private final long initialFirstSequenceNumber;

    /**
     * Construct an abstract sequence map.
     *
     * @param initialFirstSequenceNumber
     * 		the lowest allowed sequence number when this object is constructed,
     * 		or after it is cleared
     * @param sequenceNumberCapacity
     * 		the number of sequence numbers permitted to exist in this data structure. E.g. if
     * 		the lowest allowed sequence number is 100 and the capacity is 10, then values with
     * 		a sequence number between 100 and 109 (inclusive) will be allowed, and any value
     * 		with a sequence number outside that range will be rejected.
     * @param getSequenceNumberFromKey
     * 		a method that extracts the sequence number from a 1key
     */
    @SuppressWarnings("unchecked")
    protected AbstractSequenceMap(
            final long initialFirstSequenceNumber,
            final int sequenceNumberCapacity,
            final ToLongFunction<K> getSequenceNumberFromKey) {

        this.sequenceNumberCapacity = sequenceNumberCapacity;
        data = buildDataMap();
        keySets = (SequenceKeySet<K>[]) Array.newInstance(SequenceKeySet.class, sequenceNumberCapacity);

        for (long sequenceNumber = initialFirstSequenceNumber;
                sequenceNumber < initialFirstSequenceNumber + sequenceNumberCapacity;
                sequenceNumber++) {

            keySets[getSequenceKeyIndex(sequenceNumber)] = new SequenceKeySet<>(sequenceNumber);
        }

        this.initialFirstSequenceNumber = initialFirstSequenceNumber;
        this.getSequenceNumberFromKey = getSequenceNumberFromKey;
    }

    /**
     * Set the smallest allowed sequence number in the current window.
     *
     * @param firstSequenceNumberInWindow
     * 		the new first sequence number in the window
     */
    protected abstract void setFirstSequenceNumberInWindow(final long firstSequenceNumberInWindow);

    /**
     * Build the map to hold data.
     *
     * @return a map with appropriate thread safety guarantees
     */
    protected abstract Map<K, V> buildDataMap();

    /**
     * Acquire a lock on window management. Held during purge/expand calls, and during clear.
     * No-op for implementations that do not require thread safety.
     */
    protected void windowLock() {
        // Override if thread safety is required
    }

    /**
     * Release a lock on window management. Held during purge/expand calls, and during clear.
     * No-op for implementations that do not require thread safety.
     */
    protected void windowUnlock() {
        // Override if thread safety is required
    }

    /**
     * Acquire an exclusive lock on a sequence number. No-op for implementations that do not require thread safety.
     *
     * @param sequenceNumber
     * 		the sequence number to lock
     */
    protected void lockSequenceNumber(final long sequenceNumber) {
        // Override if thread safety is required
    }

    /**
     * Release an exclusive lock on a sequence number. No-op for implementations that do not require thread safety.
     *
     * @param sequenceNumber
     * 		the sequence number to unlock
     */
    protected void unlockSequenceNumber(final long sequenceNumber) {
        // Override if thread safety is required
    }

    /**
     * Acquire an exclusive lock on all sequence numbers. No-op for implementations that do not require thread safety.
     */
    protected void fullLock() {
        // Override if thread safety is required
    }

    /**
     * Release an exclusive lock on all sequence numbers. No-op for implementations that do not require thread safety.
     */
    protected void fullUnlock() {
        // Override if thread safety is required
    }

    /**
     * When the window is shifted significantly, it can be more efficient to grab all locks at the start, as
     * compared to locking on each sequence number one at a time. This method describes the size of the shift
     * required to trigger a full lock.
     *
     * @return shifts greater or equal to this in size will trigger a full lock
     */
    protected int getFullLockThreshold() {
        // Override if locking is needed
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(final K key) {
        return data.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(final K key) {
        return data.containsKey(key);
    }

    @Override
    public int getSequenceNumberCapacity() {
        return sequenceNumberCapacity;
    }

    /**
     * Get the sequence number from a key.
     *
     * @param key
     * 		the key
     * @return the associated sequence number
     */
    private long getSequenceNumber(final K key) {
        return getSequenceNumberFromKey.applyAsLong(key);
    }

    /**
     * Get the key set index for a given sequence number.
     *
     * @param sequenceNumber
     * 		the sequence number in question
     * @return the index of the sequence number
     */
    private int getSequenceKeyIndex(final long sequenceNumber) {
        if (sequenceNumber >= 0) {
            return (int) (sequenceNumber % sequenceNumberCapacity);
        }
        return (int) (((sequenceNumber % sequenceNumberCapacity) + sequenceNumberCapacity) % sequenceNumberCapacity);
    }

    /**
     * Get the sequence key set for a given sequence number.
     *
     * @param sequenceNumber
     * 		the sequence number to fetch
     * @return the key set for the sequence number
     */
    private SequenceKeySet<K> getSequenceKeySet(final long sequenceNumber) {
        return keySets[getSequenceKeyIndex(sequenceNumber)];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        V value = data.get(key);

        if (value == null) {
            value = mappingFunction.apply(key);
            final boolean added = putIfAbsent(key, value);
            if (!added) {
                value = null;
            }
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean putIfAbsent(final K key, final V value) {
        final long sequenceNumber = getSequenceNumber(key);
        final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);

        lockSequenceNumber(sequenceNumber);
        try {
            if (keys.getSequenceNumber() != sequenceNumber) {
                // the key is outside the allowed window
                return false;
            }
            if (data.containsKey(key)) {
                // don't re-insert if the value is already present
                return false;
            }

            data.put(key, value);
            keys.getKeys().add(key);

            return true;
        } finally {
            unlockSequenceNumber(sequenceNumber);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V put(final K key, final V value) {
        final long sequenceNumber = getSequenceNumber(key);
        final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);

        lockSequenceNumber(sequenceNumber);
        try {
            if (keys.getSequenceNumber() != sequenceNumber) {
                // the key is outside the allowed window
                return null;
            }

            final V previousValue = data.put(key, value);
            keys.getKeys().add(key);

            return previousValue;
        } finally {
            unlockSequenceNumber(sequenceNumber);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V remove(final K key) {
        final long sequenceNumber = getSequenceNumber(key);
        final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);

        lockSequenceNumber(sequenceNumber);
        try {
            if (keys.getSequenceNumber() != sequenceNumber) {
                // the key is outside the allowed window
                return null;
            }

            keys.getKeys().remove(key);
            return data.remove(key);

        } finally {
            unlockSequenceNumber(sequenceNumber);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shiftWindow(final long firstSequenceNumberInWindow, final BiConsumer<K, V> removedValueHandler) {
        windowLock();

        final long shiftSize = firstSequenceNumberInWindow - getFirstSequenceNumberInWindow();
        final boolean largeShift = shiftSize >= getFullLockThreshold();

        if (largeShift) {
            // Better to take locks once than to take them over and over
            fullLock();
        }

        try {
            final long previousFirstSequenceNumber = getFirstSequenceNumberInWindow();
            if (firstSequenceNumberInWindow < previousFirstSequenceNumber) {
                throw new IllegalStateException(
                        "Window can only be shifted towards larger value. " + "Current lowest sequence number = "
                                + previousFirstSequenceNumber + ", requested lowest sequence number = "
                                + firstSequenceNumberInWindow);
            }
            setFirstSequenceNumberInWindow(firstSequenceNumberInWindow);

            for (int offset = 0; offset < sequenceNumberCapacity; offset++) {

                // Stop purging once we encounter a high enough sequence number
                final long sequenceNumberToReplace = previousFirstSequenceNumber + offset;
                if (sequenceNumberToReplace >= firstSequenceNumberInWindow) {
                    return;
                }

                final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumberToReplace);

                if (!largeShift) {
                    lockSequenceNumber(sequenceNumberToReplace);
                }
                try {
                    // Remove the old data from the map
                    for (final K key : keys.getKeys()) {
                        final V value = data.remove(key);
                        if (removedValueHandler != null) {
                            removedValueHandler.accept(key, value);
                        }
                    }

                    // Prepare the key set for the new data
                    keys.setSequenceNumber(
                            mapToNewSequenceNumber(firstSequenceNumberInWindow, sequenceNumberToReplace));
                    keys.getKeys().clear();
                } finally {
                    if (!largeShift) {
                        unlockSequenceNumber(sequenceNumberToReplace);
                    }
                }
            }
        } finally {
            windowUnlock();
            if (largeShift) {
                fullUnlock();
            }
        }
    }

    /**
     * When the window is shifted, it causes some key sets in the circular buffer increase their sequence number.
     * This method computes the new sequence number that the key set is required to have.
     */
    private long mapToNewSequenceNumber(final long firstSequenceNumberInWindow, final long sequenceNumberToReplace) {
        // the distance between the new first sequence number in the window
        // and the sequence number that is being replaced
        final long difference = firstSequenceNumberInWindow - sequenceNumberToReplace;

        // The number of times we have wrapped around the buffer by increasing to the
        // new first sequence number. Will be 1 if we are increasing the minimum
        // sequence number by a small amount, may be larger than 1 if we suddenly
        // move the window a large distance in a single step.
        final long wrapFactor =
                difference / getSequenceNumberCapacity() + (difference % getSequenceNumberCapacity() == 0 ? 0 : 1);

        // Every time we go one time around the buffer, the sequence number at a particular
        // index increases by an amount equal to the capacity.
        final long increase = wrapFactor * getSequenceNumberCapacity();
        return sequenceNumberToReplace + increase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeValuesWithSequenceNumber(final long sequenceNumber, final BiConsumer<K, V> removedValueHandler) {
        windowLock();
        try {
            final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);
            lockSequenceNumber(sequenceNumber);
            try {
                if (keys.getSequenceNumber() != sequenceNumber) {
                    return;
                }

                for (final K key : keys.getKeys()) {
                    final V value = data.remove(key);
                    if (removedValueHandler != null) {
                        removedValueHandler.accept(key, value);
                    }
                }
                keys.getKeys().clear();
            } finally {
                unlockSequenceNumber(sequenceNumber);
            }
        } finally {
            windowUnlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<K> getKeysWithSequenceNumber(final long sequenceNumber) {
        final List<K> list = new LinkedList<>();
        final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);

        lockSequenceNumber(sequenceNumber);
        try {
            if (keys.getSequenceNumber() == sequenceNumber) {
                list.addAll(keys.getKeys());
            }
        } finally {
            unlockSequenceNumber(sequenceNumber);
        }

        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map.Entry<K, V>> getEntriesWithSequenceNumber(final long sequenceNumber) {
        final List<Map.Entry<K, V>> list = new LinkedList<>();
        final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);

        lockSequenceNumber(sequenceNumber);
        try {
            if (keys.getSequenceNumber() == sequenceNumber) {
                for (final K key : keys.getKeys()) {
                    list.add(new AbstractMap.SimpleEntry<>(key, data.get(key)));
                }
            }
        } finally {
            unlockSequenceNumber(sequenceNumber);
        }

        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return data.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        windowLock();
        fullLock();

        try {
            data.clear();
            setFirstSequenceNumberInWindow(initialFirstSequenceNumber);
            for (int offset = 0; offset < sequenceNumberCapacity; offset++) {
                final long sequenceNumber = initialFirstSequenceNumber + offset;
                final SequenceKeySet<K> keys = getSequenceKeySet(sequenceNumber);
                keys.setSequenceNumber(sequenceNumber);
                keys.getKeys().clear();
            }
        } finally {
            windowUnlock();
            fullUnlock();
        }
    }
}
