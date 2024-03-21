/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.utils;

import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.numFromEvmAddress;

import com.hedera.node.app.service.evm.contracts.execution.StaticProperties;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces the risk of hash collisions in
 * structured data using this type, when compared to the {@code java.lang.Integer} boxed type.
 */
public class EntityNum implements Comparable<EntityNum> {
    public static final EntityNum MISSING_NUM = new EntityNum(0);

    private final int value;

    public EntityNum(final int value) {
        this.value = value;
    }

    public static EntityNum fromInt(final int i) {
        return new EntityNum(i);
    }

    public static EntityNum fromLong(final long l) {
        if (!isValidNum(l)) {
            return MISSING_NUM;
        }
        final var value = codeFromNum(l);
        return new EntityNum(value);
    }

    public static EntityNum fromModel(final Id id) {
        if (!areValidNums(id.shard(), id.realm())) {
            return MISSING_NUM;
        }
        return fromLong(id.num());
    }

    public static EntityNum fromAccountId(final AccountID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getAccountNum());
    }

    public static EntityNum fromEvmAddress(final Address address) {
        final var bytes = address.toArrayUnsafe();
        return fromLong(numFromEvmAddress(bytes));
    }

    public static EntityNum fromMirror(final byte[] evmAddress) {
        return EntityNum.fromLong(numFromEvmAddress(evmAddress));
    }

    public static EntityNum fromTokenId(final TokenID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getTokenNum());
    }

    public static @NonNull EntityNum fromTopicId(@NonNull final TopicID grpc) {
        Objects.requireNonNull(grpc);
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getTopicNum());
    }

    public static EntityNum fromTopicId(final com.hedera.hapi.node.base.TopicID pbj) {
        if (!areValidNums(pbj.shardNum(), pbj.realmNum())) {
            return MISSING_NUM;
        }
        return fromLong(pbj.topicNum());
    }

    public static EntityNum fromFileId(final com.hedera.hapi.node.base.FileID pbj) {
        if (!areValidNums(pbj.shardNum(), pbj.realmNum())) {
            return MISSING_NUM;
        }
        return fromLong(pbj.fileNum());
    }

    public static EntityNum fromContractId(final ContractID grpc) {
        if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
            return MISSING_NUM;
        }
        return fromLong(grpc.getContractNum());
    }

    public static EntityNum fromScheduleId(final ScheduleID grpc) {
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
        return asEvmAddress(numFromCode(value));
    }

    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityNum.class != o.getClass()) {
            return false;
        }

        final var that = (EntityNum) o;

        return this.value == that.value;
    }

    static boolean areValidNums(final long shard, final long realm) {
        return shard == StaticProperties.getShard() && realm == StaticProperties.getRealm();
    }

    @Override
    public String toString() {
        return "EntityNum{" + "value=" + value + '}';
    }

    @Override
    public int compareTo(@NonNull final EntityNum that) {
        return Integer.compare(this.value, that.value);
    }
}
