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
package com.hedera.services.utils;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.numFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.realmFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.shardFromEvmAddress;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces the risk of hash collisions in
 * structured data using this type, when compared to the {@code java.lang.Integer} boxed type.
 */
public class EntityNum implements Comparable<EntityNum> {
    public static final EntityNum MISSING_NUM = new EntityNum(0);

    private final int value;

    public EntityNum(int value) {
        this.value = value;
    }

    public static EntityNum fromInt(int i) {
        return new EntityNum(i);
    }

    public static EntityNum fromLong(long l) {
        if (!isValidNum(l)) {
            return MISSING_NUM;
        }
        final var value = codeFromNum(l);
        return new EntityNum(value);
    }

    public static EntityNum fromModel(Id id) {
        if (!areValidNums(id.shard(), id.realm())) {
            return MISSING_NUM;
        }
        return fromLong(id.num());
    }

    public static EntityNum fromAccountId(AccountID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getAccountNum());
    }

    public static EntityNum fromEvmAddress(final Address address) {
        final var bytes = address.toArrayUnsafe();
        final var shard = shardFromEvmAddress(bytes);
        final var realm = realmFromEvmAddress(bytes);
        if (!areValidNums(shard, realm)) {
            return MISSING_NUM;
        }
        return fromLong(numFromEvmAddress(bytes));
    }

    public static EntityNum fromMirror(final byte[] evmAddress) {
        return EntityNum.fromLong(numFromEvmAddress(evmAddress));
    }

    public static EntityNum fromTokenId(TokenID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getTokenNum());
    }

    public static EntityNum fromTopicId(TopicID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getTopicNum());
    }

    public static EntityNum fromContractId(ContractID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getContractNum());
    }

    public static EntityNum fromScheduleId(ScheduleID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getScheduleNum());
    }

    public int intValue() {
        return value;
    }

    public long longValue() {
        return numFromCode(value);
    }

    public AccountID toGrpcAccountId() {
        return STATIC_PROPERTIES.scopedAccountWith(numFromCode(value));
    }

    public EntityId toEntityId() {
        return STATIC_PROPERTIES.scopedEntityIdWith(numFromCode(value));
    }

    public Id toId() {
        return STATIC_PROPERTIES.scopedIdWith(numFromCode(value));
    }

    public ContractID toGrpcContractID() {
        return STATIC_PROPERTIES.scopedContractIdWith(numFromCode(value));
    }

    public TokenID toGrpcTokenId() {
        return STATIC_PROPERTIES.scopedTokenWith(numFromCode(value));
    }

    public ScheduleID toGrpcScheduleId() {
        return STATIC_PROPERTIES.scopedScheduleWith(numFromCode(value));
    }

    public String toIdString() {
        return STATIC_PROPERTIES.scopedIdLiteralWith(numFromCode(value));
    }

    public Address toEvmAddress() {
        return Address.wrap(Bytes.wrap(toRawEvmAddress()));
    }

    public byte[] toRawEvmAddress() {
        return asEvmAddress(
                (int) STATIC_PROPERTIES.getShard(),
                STATIC_PROPERTIES.getRealm(),
                numFromCode(value));
    }

    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityNum.class != o.getClass()) {
            return false;
        }

        var that = (EntityNum) o;

        return this.value == that.value;
    }

    static boolean areValidNums(long shard, long realm) {
        return shard == STATIC_PROPERTIES.getShard() && realm == STATIC_PROPERTIES.getRealm();
    }

    @Override
    public String toString() {
        return "EntityNum{" + "value=" + value + '}';
    }

    @Override
    public int compareTo(@NotNull final EntityNum that) {
        return Integer.compare(this.value, that.value);
    }
}
