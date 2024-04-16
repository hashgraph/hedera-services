/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.schedule;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.mono.state.virtual.utils.ThrowingConsumer;
import com.hedera.node.app.service.mono.state.virtual.utils.ThrowingSupplier;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * This is currently used in a MerkleMap due to issues with virtual map in the 0.27 release. It
 * should be moved back to VirtualMap in 0.28.
 */
public class ScheduleEqualityVirtualValue extends PartialMerkleLeaf
        implements VirtualValue, Keyed<ScheduleEqualityVirtualKey>, MerkleLeaf {

    public static final int CURRENT_VERSION = 1;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x1fe377366e3282f2L;

    private long number;

    /**
     * Although extremely unlikely, we must handle the case where more than one schedule has the
     * same long equality hash. So this is a Map of string equality hash to schedule ID.
     */
    private final SortedMap<String, Long> ids;

    public ScheduleEqualityVirtualValue() {
        this(TreeMap::new, null);
    }

    public ScheduleEqualityVirtualValue(Map<String, Long> ids) {
        this(ids, null);
    }

    public ScheduleEqualityVirtualValue(Map<String, Long> ids, ScheduleEqualityVirtualKey key) {
        this(() -> new TreeMap<>(ids), key);
    }

    private ScheduleEqualityVirtualValue(Supplier<SortedMap<String, Long>> ids, ScheduleEqualityVirtualKey key) {
        this.ids = ids.get();
        this.number = key == null ? -1 : key.getKeyAsLong();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || ScheduleEqualityVirtualValue.class != o.getClass()) {
            return false;
        }

        var that = (ScheduleEqualityVirtualValue) o;
        return Objects.equals(this.ids, that.ids);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ids);
    }

    @Override
    public String toString() {
        var helper = MoreObjects.toStringHelper(ScheduleEqualityVirtualValue.class)
                .add("ids", ids)
                .add("number", number);
        return helper.toString();
    }

    int serializedSizeInBytes() {
        int size = 0;
        size += Integer.BYTES; // ids size
        for (var e : ids.entrySet()) {
            size += Integer.BYTES + e.getKey().getBytes(StandardCharsets.UTF_8).length + Long.BYTES;
        }
        size += Long.BYTES;
        return size;
    }

    private <E extends Exception> void serializeTo(
            final ThrowingConsumer<Integer, E> writeIntFn,
            final ThrowingConsumer<Long, E> writeLongFn,
            final ThrowingConsumer<byte[], E> writeBytesFn)
            throws E {
        writeIntFn.accept(ids.size());
        for (var e : ids.entrySet()) {
            var keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
            writeIntFn.accept(keyBytes.length);
            writeBytesFn.accept(keyBytes);
            writeLongFn.accept(e.getValue());
        }
        writeLongFn.accept(number);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeTo(out::writeInt, out::writeLong, out::write);
    }

    void serialize(final WritableSequentialData out) {
        serializeTo(out::writeInt, out::writeLong, out::writeBytes);
    }

    private <E extends Exception> void deserializeFrom(
            final ThrowingSupplier<Integer, E> readIntFn,
            final ThrowingSupplier<Long, E> readLongFn,
            final ThrowingConsumer<byte[], E> readBytesFn)
            throws E {
        final int s = readIntFn.get();
        ids.clear();
        for (int x = 0; x < s; x++) {
            final int l = readIntFn.get();
            final byte[] keyBytes = new byte[l];
            readBytesFn.accept(keyBytes);
            final String key = new String(keyBytes, StandardCharsets.UTF_8);
            final long value = readLongFn.get();
            ids.put(key, value);
        }
        number = readLongFn.get();
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        deserializeFrom(in::readInt, in::readLong, in::readFully);
    }

    void deserialize(final ReadableSequentialData in) {
        deserializeFrom(in::readInt, in::readLong, in::readBytes);
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public ScheduleEqualityVirtualValue copy() {
        var fc = new ScheduleEqualityVirtualValue(ids, new ScheduleEqualityVirtualKey(number));

        this.setImmutable(true);

        return fc;
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    public void add(String hash, long id) {
        throwIfImmutable("Cannot add to ids if it's immutable.");

        var cur = ids.get(hash);

        if (cur != null && cur != id) {
            throw new IllegalStateException("multiple ids with same hash during add! " + cur + " and " + id);
        }

        ids.put(hash, id);
    }

    public void remove(String hash, long id) {
        throwIfImmutable("Cannot remove from ids if it's immutable.");

        var cur = ids.get(hash);

        if (cur != null && cur != id) {
            throw new IllegalStateException("multiple ids with same hash during remove! " + cur + " and " + id);
        }

        ids.remove(hash);
    }

    public SortedMap<String, Long> getIds() {
        return Collections.unmodifiableSortedMap(ids);
    }

    /** {@inheritDoc} */
    @Override
    public ScheduleEqualityVirtualValue asReadOnly() {
        var c = new ScheduleEqualityVirtualValue(this::getIds, new ScheduleEqualityVirtualKey(number));
        c.setImmutable(true);
        return c;
    }

    /**
     * Needed until getForModify works on VirtualMap
     *
     * @return a copy of this without marking this as immutable
     */
    public ScheduleEqualityVirtualValue asWritable() {
        return new ScheduleEqualityVirtualValue(this.ids, new ScheduleEqualityVirtualKey(number));
    }

    @Override
    public ScheduleEqualityVirtualKey getKey() {
        return new ScheduleEqualityVirtualKey(number);
    }

    @Override
    public void setKey(final ScheduleEqualityVirtualKey key) {
        this.number = key == null ? -1 : key.getKeyAsLong();
    }
}
