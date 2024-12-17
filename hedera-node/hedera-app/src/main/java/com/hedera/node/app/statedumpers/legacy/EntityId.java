/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.legacy;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.Objects;

public class EntityId {
    public static final EntityId MISSING_ENTITY_ID = new EntityId(0, 0, 0);

    private long shard;
    private long realm;
    private long num;

    public EntityId() {
        /* For RuntimeConstructable */
    }

    public EntityId(final long shard, final long realm, final long num) {
        this.shard = shard;
        this.realm = realm;
        this.num = num;
    }

    public static EntityId fromNum(final long num) {
        return new EntityId(0, 0, num);
    }

    /* --- Object --- */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityId.class != o.getClass()) {
            return false;
        }
        final EntityId that = (EntityId) o;
        return shard == that.shard && realm == that.realm && num == that.num;
    }

    public boolean matches(final AccountID aId) {
        return shard == aId.getShardNum() && realm == aId.getRealmNum() && num == aId.getAccountNum();
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

    public void setNum(final long num) {
        this.num = num;
    }

    /* --- Helpers --- */
    public static EntityId fromGrpcAccountId(final AccountID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    public static EntityId fromPbjAccountId(final com.hedera.hapi.node.base.AccountID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.shardNum(), id.realmNum(), id.accountNum());
    }

    public static EntityId fromGrpcFileId(final FileID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getFileNum());
    }

    public static EntityId fromGrpcTopicId(final TopicID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getTopicNum());
    }

    public static EntityId fromGrpcTokenId(final TokenID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getTokenNum());
    }

    public static EntityId fromGrpcScheduleId(final ScheduleID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getScheduleNum());
    }

    public static EntityId fromGrpcContractId(final ContractID id) {
        if (id == null) {
            return MISSING_ENTITY_ID;
        }
        return new EntityId(id.getShardNum(), id.getRealmNum(), id.getContractNum());
    }

    public ContractID toGrpcContractId() {
        return ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
    }

    public TokenID toGrpcTokenId() {
        return TokenID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setTokenNum(num)
                .build();
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

    public com.hedera.hapi.node.base.AccountID toPbjAccountId() {
        return com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .accountNum(num)
                .build();
    }

    public EntityNum asNum() {
        return EntityNum.fromLong(num);
    }
}
