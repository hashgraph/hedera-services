package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

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

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.numFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.realmFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.shardFromEvmAddress;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces
 * the risk of hash collisions in structured data using this type,
 * when compared to the {@code java.lang.Integer} boxed type.
 */
public class EntityNum implements Comparable<EntityNum> {
	public static final EntityNum MISSING_NUM = new EntityNum(0);

	private final long value;

	public EntityNum(long value) {
		this.value = value;
	}

	public static EntityNum fromInt(int i) {
		return fromLong(i & 0xFFFF_FFFFL);
	}

	public static EntityNum fromLong(long l) {
		if (!isValidNum(l)) {
			return MISSING_NUM;
		}
		return new EntityNum(l);
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
		return (int) value;
	}

	public long longValue() {
		return value;
	}

	public AccountID toGrpcAccountId() {
		return STATIC_PROPERTIES.scopedAccountWith(value);
	}

	public EntityId toEntityId() {
		return STATIC_PROPERTIES.scopedEntityIdWith(numFromCode(value));
	}

	public Id toId() {
		return STATIC_PROPERTIES.scopedIdWith(value);
	}

	public ContractID toGrpcContractID() {
		return STATIC_PROPERTIES.scopedContractIdWith(value);
	}

	public TokenID toGrpcTokenId() {
		return STATIC_PROPERTIES.scopedTokenWith(value);
	}

	public ScheduleID toGrpcScheduleId() {
		return STATIC_PROPERTIES.scopedScheduleWith(value);
	}

	public String toIdString() {
		return STATIC_PROPERTIES.scopedIdLiteralWith(value);
	}

	public Address toEvmAddress() {
		return Address.wrap(Bytes.wrap(toRawEvmAddress()));
	}

	public byte[] toRawEvmAddress() {
		return asEvmAddress(
				(int) STATIC_PROPERTIES.getShard(),
				STATIC_PROPERTIES.getRealm(),
				value);
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
		return "EntityNum{" +
				"value=" + value +
				'}';
	}

	@Override
	public int compareTo(@NotNull final EntityNum that) {
		return Long.compare(this.value, that.value);
	}
}
