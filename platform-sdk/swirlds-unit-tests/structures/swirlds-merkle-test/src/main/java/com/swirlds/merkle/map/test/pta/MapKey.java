/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.pta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class MapKey implements SelfSerializable, Comparable<MapKey>, FastCopyable {

    public static final long CLASS_ID = 0x63302b26cc321421L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    @JsonProperty
    private long shardId;

    @JsonProperty
    private long realmId;

    @JsonProperty
    private long accountId;

    private static final Comparator<MapKey> CANONICAL_ORDERING = Comparator.comparing(MapKey::getShardId)
            .thenComparingLong(MapKey::getRealmId)
            .thenComparingLong(MapKey::getAccountId);

    public MapKey(final long shardId, final long realmId, final long accountId) {
        this.shardId = shardId;
        this.realmId = realmId;
        this.accountId = accountId;
    }

    public MapKey() {}

    private MapKey(final MapKey mapKey) {
        this.shardId = mapKey.shardId;
        this.realmId = mapKey.realmId;
        this.accountId = mapKey.accountId;
    }

    public long getRealmId() {
        return realmId;
    }

    public long getShardId() {
        return shardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapKey mapKey = (MapKey) o;
        return new EqualsBuilder()
                .append(shardId, mapKey.shardId)
                .append(realmId, mapKey.realmId)
                .append(accountId, mapKey.accountId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(shardId)
                .append(realmId)
                .append(accountId)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append(shardId)
                .append(realmId)
                .append(accountId)
                .toString();
    }

    @Override
    public int compareTo(final MapKey that) {
        return CANONICAL_ORDERING.compare(this, that);
    }

    public long getAccountId() {
        return accountId;
    }

    @Override
    public MapKey copy() {
        return new MapKey(this);
    }

    @Override
    public void serialize(SerializableDataOutputStream outStream) throws IOException {
        outStream.writeLong(this.shardId);
        outStream.writeLong(this.realmId);
        outStream.writeLong(this.accountId);
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int version) throws IOException {
        this.shardId = inputStream.readLong();
        this.realmId = inputStream.readLong();
        this.accountId = inputStream.readLong();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
