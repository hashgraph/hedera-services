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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.Arrays;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces
 * the risk of hash collisions in structured data using this type,
 * when compared to the {@code java.lang.Integer} boxed type.
 */
public class EntityNum {
	static final EntityNum MISSING_NUM = new EntityNum(0);

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
		if (!areValidNums(id.getShard(), id.getRealm())) {
			return MISSING_NUM;
		}
		return fromLong(id.getNum());
	}

	public static EntityNum fromAccountId(AccountID grpc) {
		if (!areValidNums(grpc.getShardNum(), grpc.getRealmNum())) {
			return MISSING_NUM;
		}
		return fromLong(grpc.getAccountNum());
	}

	public static EntityNum fromEntityId(EntityId serializable) {
		if (!areValidNums(serializable.shard(), serializable.realm())) {
			return MISSING_NUM;
		}
		return fromLong(serializable.num());
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

	public static EntityNum fromAddress(final Address address) {
		return fromAddressBytes(address.toArrayUnsafe());
	}

	public static EntityNum fromAddressBytes(final byte[] solidityAddress) {
		final long shard = Ints.fromByteArray(Arrays.copyOfRange(solidityAddress, 0, 4));
		final long realm = Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 4, 12));
		if (!areValidNums(shard, realm)) {
			return MISSING_NUM;
		}
		return EntityNum.fromLong(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 12, 20)));
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

	public Id toModelId() {
		return STATIC_PROPERTIES.scopedModelIdWith(numFromCode(value));
	}

	public Address toTypedSolidityAddress() {
		return Address.wrap(Bytes.wrap(asSolidityAddress(
				(int) STATIC_PROPERTIES.getShard(),
				STATIC_PROPERTIES.getRealm(),
				longValue())));
	}

	public ContractID toGrpcContractId() {
		return STATIC_PROPERTIES.scopedContractWith(numFromCode(value));
	}

	public EntityId toEntityId() {
		return STATIC_PROPERTIES.scopedEntityIdWith(numFromCode(value));
	}

	public TokenID toGrpcTokenId() {
		return STATIC_PROPERTIES.scopedTokenWith(numFromCode(value));
	}

	public ScheduleID toGrpcScheduleId() {
		return STATIC_PROPERTIES.scopedScheduleWith(numFromCode(value));
	}

	public String toString() {
		return STATIC_PROPERTIES.scopedIdLiteralWith(numFromCode(value));
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
}
