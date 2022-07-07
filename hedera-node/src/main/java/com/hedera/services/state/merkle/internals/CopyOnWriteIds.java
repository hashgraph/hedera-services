/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle.internals;

import static java.util.Comparator.comparingLong;

import com.google.common.base.MoreObjects;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Manages a multiset of {@code (num, realm, shard)} ids with convenience methods for adding,
 * removing, and checking membership of {@link com.hedera.services.store.models.Id} instances.
 *
 * <p>Does simplistic copy-on-write via structural sharing with the {@link CopyOnWriteIds#copy()}
 * method. That is, given an instance {@code a} and {@code aCopy = a.copy()}, both instances will
 * share the same {@code long[] ids} array until one is mutated.
 */
public class CopyOnWriteIds {
    private static final int NUM_OFFSET = 0;
    private static final int REALM_OFFSET = 1;
    private static final int SHARD_OFFSET = 2;
    private static final int NUM_ID_PARTS = 3;

    private static final long[] NO_IDS = new long[0];
    private static final Comparator<long[]> ID_CMP =
            comparingLong((long[] l) -> l[NUM_OFFSET])
                    .thenComparingLong(l -> l[REALM_OFFSET])
                    .thenComparingLong(l -> l[SHARD_OFFSET]);

    private long[] ids = NO_IDS;

    public CopyOnWriteIds() {}

    public CopyOnWriteIds(long[] ids) {
        if (ids.length % NUM_ID_PARTS != 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Argument 'ids' has length=%d not divisible by %d",
                            ids.length, NUM_ID_PARTS));
        }
        this.ids = ids;
    }

    public CopyOnWriteIds copy() {
        return new CopyOnWriteIds(ids);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || CopyOnWriteIds.class != o.getClass()) {
            return false;
        }

        var that = (CopyOnWriteIds) o;

        return Arrays.equals(this.ids, that.ids);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ids);
    }

    /* --- Bean --- */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("ids", toReadableIdList()).toString();
    }

    public String toReadableIdList() {
        var sb = new StringBuilder("[");
        for (int i = 0, n = size(); i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%d.%d.%d", ids[shard(i)], ids[realm(i)], ids[num(i)]));
        }
        sb.append("]");

        return sb.toString();
    }

    /* --- Multiset operations --- */
    public int size() {
        return ids.length / NUM_ID_PARTS;
    }

    public boolean contains(TokenID grpcId) {
        return logicalIndexOf(asNativeId(grpcId)) >= 0;
    }

    public boolean contains(Id id) {
        return logicalIndexOf(asNativeId(id)) >= 0;
    }

    /**
     * Adds the {@code (num, realm, shard)} ids represented by the given set of gRPC {@link TokenID}
     * objects. Will be removed by ongoing HTS refactor.
     *
     * @param grpcIds the ids to add
     * @deprecated
     */
    @Deprecated
    public void addAll(Set<TokenID> grpcIds) {
        add(asNativeIds(grpcIds));
    }

    /**
     * Adds the {@code (num, realm, shard)} ids represented by the given set of model {@link Id}
     * objects.
     *
     * @param modelIds the ids to add
     */
    public void addAllIds(Set<Id> modelIds) {
        add(asNative(modelIds));
    }

    /**
     * Removes all appearances of any managed {@code (num, realm, shard)} ids represented in the
     * given set of model {@link Id} objects.
     *
     * @param modelIds the ids to remove
     */
    public void removeAllIds(Set<Id> modelIds) {
        remove(nativeId -> modelIds.contains(asModel(nativeId)));
    }

    /**
     * Overwrite the managed multiset with the given sequence of {@code (num, realm, shard)} ids.
     *
     * @param ids the sequence of {@code (num, realm, shard)} ids to overwrite
     */
    public void setNativeIds(long[] ids) {
        this.ids = ids;
    }

    public long[] getNativeIds() {
        return ids;
    }

    public List<TokenID> getAsIds() {
        final List<TokenID> modelIds = new ArrayList<>();
        for (int i = 0, n = size(); i < n; i++) {
            modelIds.add(asGrpcTokenId(nativeIdAt(i)));
        }
        return modelIds;
    }

    /* --- Helpers --- */
    void remove(Predicate<long[]> removalFilter) {
        int n = size();
        int newN = 0;
        for (int i = 0; i < n; i++) {
            if (!removalFilter.test(nativeIdAt(i))) {
                newN++;
            }
        }
        if (newN != n) {
            long[] newIds = new long[newN * NUM_ID_PARTS];
            for (int i = 0, j = 0; i < n; i++) {
                final var nativeId = nativeIdAt(i);
                if (!removalFilter.test(nativeId)) {
                    set(newIds, j++, nativeId);
                }
            }
            ids = newIds;
        }
    }

    void add(List<long[]> nativeIds) {
        final var allIds = mutableListOfIds();
        allIds.addAll(nativeIds);
        allIds.sort(ID_CMP);
        int newN = allIds.size();
        long[] newIds = new long[newN * NUM_ID_PARTS];
        for (int i = 0; i < newN; i++) {
            set(newIds, i, allIds.get(i));
        }
        ids = newIds;
    }

    private List<long[]> mutableListOfIds() {
        final var n = size();
        final var mutableList = new ArrayList<long[]>(n);
        for (int i = 0; i < n; i++) {
            mutableList.add(nativeIdAt(i));
        }
        return mutableList;
    }

    private int num(int i) {
        return i * NUM_ID_PARTS + NUM_OFFSET;
    }

    private int realm(int i) {
        return i * NUM_ID_PARTS + REALM_OFFSET;
    }

    private int shard(int i) {
        return i * NUM_ID_PARTS + SHARD_OFFSET;
    }

    private int logicalIndexOf(long[] shardRealmNum) {
        int lo = 0;
        int hi = ids.length / NUM_ID_PARTS - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int comparison = compareImplied(mid, shardRealmNum);
            if (comparison == 0) {
                return mid;
            } else if (comparison < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -(lo + 1);
    }

    private int compareImplied(int at, long[] nativeId) {
        long numA = ids[num(at)];
        long numB = nativeId[NUM_OFFSET];
        if (numA == numB) {
            long realmA = ids[realm(at)];
            long realmB = nativeId[REALM_OFFSET];
            if (realmA == realmB) {
                return Long.compare(ids[shard(at)], nativeId[SHARD_OFFSET]);
            } else {
                return Long.compare(realmA, realmB);
            }
        } else {
            return Long.compare(numA, numB);
        }
    }

    private void set(long[] someIds, int i, long[] nativeId) {
        someIds[num(i)] = nativeId[NUM_OFFSET];
        someIds[realm(i)] = nativeId[REALM_OFFSET];
        someIds[shard(i)] = nativeId[SHARD_OFFSET];
    }

    private long[] nativeIdAt(int i) {
        return new long[] {ids[num(i)], ids[realm(i)], ids[shard(i)]};
    }

    private long[] asNativeId(TokenID grpcId) {
        return new long[] {grpcId.getTokenNum(), grpcId.getRealmNum(), grpcId.getShardNum()};
    }

    private long[] asNativeId(Id id) {
        return new long[] {id.num(), id.realm(), id.shard()};
    }

    private List<long[]> asNativeIds(Set<TokenID> grpcIds) {
        final var nativeIds = new ArrayList<long[]>();
        grpcIds.forEach(grpcId -> nativeIds.add(asNativeId(grpcId)));
        return nativeIds;
    }

    private List<long[]> asNative(Set<Id> modelIds) {
        final var nativeIds = new ArrayList<long[]>();
        modelIds.forEach(grpcId -> nativeIds.add(asNativeId(grpcId)));
        return nativeIds;
    }

    private Id asModel(long[] nativeId) {
        return new Id(nativeId[SHARD_OFFSET], nativeId[REALM_OFFSET], nativeId[NUM_OFFSET]);
    }

    private TokenID asGrpcTokenId(long[] nativeId) {
        return TokenID.newBuilder()
                .setShardNum(nativeId[SHARD_OFFSET])
                .setRealmNum(nativeId[REALM_OFFSET])
                .setTokenNum(nativeId[NUM_OFFSET])
                .build();
    }
}
