/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.services.context.properties.StaticPropertiesHolder;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class EntityId implements SelfSerializable {
    private static final long DEFAULT_SHARD = 0L;
    private static final long DEFAULT_REALM = 0L;

    static final int MERKLE_VERSION = 1;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xf35ba643324efa37L;

    public static final EntityId MISSING_ENTITY_ID = new EntityId(0, 0, 0);

    private long shard;
    private long realm;
    private long num;

    public EntityId() {
        /* For RuntimeConstructable */
    }

    public EntityId(Id id) {
        this.shard = id.shard();
        this.realm = id.realm();
        this.num = id.num();
    }

    public EntityId(long shard, long realm, long num) {
        this.shard = shard;
        this.realm = realm;
        this.num = num;
    }

    /**
     * Gives a "compressed" code to identify this entity id.
     *
     * @return the code for this id
     */
    public int identityCode() {
        return codeFromNum(num);
    }

    /**
     * Builds an entity id from its encoded format.
     *
     * @param code the compressed representation
     * @return the equivalent entity id
     */
    public static EntityId fromIdentityCode(int code) {
        return new EntityId(DEFAULT_SHARD, DEFAULT_REALM, numFromCode(code));
    }

    /* --- SelfSerializable --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    /**
     * Builds an entity id from just the entity number.
     *
     * <p>The shard and realm numbers are set to the default values configured in properties.
     *
     * @param num the number of the entity id.
     * @return the equivalent entity id using the node's shard and realm.
     */
    public static EntityId fromNum(long num) {
        return new EntityId(
                StaticPropertiesHolder.STATIC_PROPERTIES.getShard(),
                StaticPropertiesHolder.STATIC_PROPERTIES.getRealm(),
                num);
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        shard = in.readLong();
        realm = in.readLong();
        num = in.readLong();
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(shard);
        out.writeLong(realm);
        out.writeLong(num);
    }

    /* --- Object --- */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityId.class != o.getClass()) {
            return false;
        }
        EntityId that = (EntityId) o;
        return shard == that.shard && realm == that.realm && num == that.num;
    }

    public boolean matches(AccountID aId) {
        return shard == aId.getShardNum()
                && realm == aId.getRealmNum()
                && num == aId.getAccountNum();
    }

    public boolean matches(Id id) {
        return shard == id.shard() && realm == id.realm() && num == id.num();
    }

    @Override
    public int hashCode() {
        return Objects.hash(shard, realm, num);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("shard", shard)
                .add("realm", realm)
                .add("num", num)
                .toString();
    }

    public String toAbbrevString() {
        return String.format("%d.%d.%d", shard, realm, num);
    }

    public EntityId copy() {
        return new EntityId(shard, realm, num);
    }

    public long shard() {
        return shard;
    }

    public long realm() {
        return realm;
    }

    public long num() {
        return num;
    }

    public void setNum(long num) {
        this.num = num;
    }

    /* --- Helpers --- */
    public static EntityId fromGrpcAccountId(AccountID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    public static EntityId fromGrpcFileId(FileID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getFileNum());
    }

    public static EntityId fromGrpcTopicId(TopicID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getTopicNum());
    }

    public static EntityId fromGrpcTokenId(TokenID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getTokenNum());
    }

    public static EntityId fromGrpcScheduleId(ScheduleID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getScheduleNum());
    }

    public static EntityId fromGrpcContractId(ContractID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getContractNum());
    }

    public static EntityId fromAddress(final Address address) {
        final var evmAddress = address.toArrayUnsafe();
        return new EntityId(
                Ints.fromByteArray(Arrays.copyOfRange(evmAddress, 0, 4)),
                Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 4, 12)),
                Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20)));
    }

    public ContractID toGrpcContractId() {
        return ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
    }

    public TokenID toGrpcTokenId() {
        return TokenID.newBuilder().setShardNum(shard).setRealmNum(realm).setTokenNum(num).build();
    }

    public ScheduleID toGrpcScheduleId() {
        return ScheduleID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setScheduleNum(num)
                .build();
    }

    public AccountID toGrpcAccountId() {
        return AccountID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setAccountNum(num)
                .build();
    }

    public EntityNum asNum() {
        return EntityNum.fromLong(num);
    }

    public Id asId() {
        return new Id(shard, realm, num);
    }

    public Address toEvmAddress() {
        final var evmAddress = asEvmAddress((int) shard, realm, num);
        return Address.wrap(Bytes.wrap(evmAddress));
    }
}
