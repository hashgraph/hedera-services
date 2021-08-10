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
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.CommonUtils;

import java.util.Arrays;
import java.util.Optional;

import static java.lang.System.arraycopy;

public class EntityIdUtils {
	private static final String ENTITY_ID_FORMAT = "%d.%d.%d";
	private static final String CANNOT_PARSE_PREFIX = "Cannot parse '";

	public static String readableId(Object o) {
		if (o instanceof Id) {
			Id id = (Id) o;
			return String.format("%d.%d.%d", id.getShard(), id.getRealm(), id.getNum());
		} else if (o instanceof AccountID) {
			AccountID id = (AccountID) o;
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
		} else if (o instanceof FileID) {
			FileID id = (FileID) o;
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getFileNum());
		} else if (o instanceof TopicID) {
			TopicID id = (TopicID) o;
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTopicNum());
		} else if (o instanceof TokenID) {
			TokenID id = (TokenID) o;
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTokenNum());
		} else if (o instanceof ScheduleID) {
			ScheduleID id = (ScheduleID) o;
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getScheduleNum());
		} else if (o instanceof NftID) {
			NftID id = (NftID) o;
			TokenID tokenID = id.getTokenID();
			return String.format(ENTITY_ID_FORMAT, tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), id.getSerialNumber());
		} else {
			return String.valueOf(o);
		}
	}

	/**
	 * Returns the {@code AccountID} represented by a literal of the form {@code <shard>.<realm>.<num>}.
	 *
	 * @param literal the account literal
	 * @return the corresponding id
	 * @throws IllegalArgumentException if the literal is not formatted correctly
	 */
	public static AccountID parseAccount(String literal) {
		try {
			final long[] parts = parseLongTriple(literal);
			return AccountID.newBuilder()
					.setShardNum(parts[0])
					.setRealmNum(parts[1])
					.setAccountNum(parts[2])
					.build();
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException(String.format("Argument 'literal=%s' is not an account", literal), e);
		}
	}

	private static long[] parseLongTriple(String dotDelimited) {
		final long[] triple = new long[3];
		int i = 0;
		long v = 0;
		for (char c : dotDelimited.toCharArray()) {
			if (c == '.') {
				triple[i++] = v;
				v = 0;
			} else if (c < '0' || c > '9') {
				throw new NumberFormatException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to character '" + c + "'");
			} else {
				v = 10 * v + (c - '0');
				if (v < 0) {
					throw new IllegalArgumentException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to overflow");
				}
			}
		}
		if (i < 2) {
			throw new IllegalArgumentException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to only " + i + " dots");
		}
		triple[i] = v;
		return triple;
	}

	public static AccountID asAccount(ContractID cid) {
		return AccountID.newBuilder()
				.setRealmNum(cid.getRealmNum())
				.setShardNum(cid.getShardNum())
				.setAccountNum(cid.getContractNum())
				.build();
	}

	public static ContractID asContract(AccountID id) {
		return ContractID.newBuilder()
				.setRealmNum(id.getRealmNum())
				.setShardNum(id.getShardNum())
				.setContractNum(id.getAccountNum())
				.build();
	}

	public static FileID asFile(AccountID id) {
		return FileID.newBuilder()
				.setRealmNum(id.getRealmNum())
				.setShardNum(id.getShardNum())
				.setFileNum(id.getAccountNum())
				.build();
	}

	public static AccountID asAccount(EntityId jId) {
		return Optional
				.ofNullable(jId)
				.map(id ->
						AccountID.newBuilder()
								.setRealmNum(id.realm())
								.setShardNum(id.shard())
								.setAccountNum(id.num())
								.build())
				.orElse(AccountID.getDefaultInstance());
	}

	public static String asSolidityAddressHex(AccountID id) {
		return CommonUtils.hex(asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum()));
	}

	public static byte[] asSolidityAddress(ContractID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum());
	}

	public static byte[] asSolidityAddress(int shard, long realm, long num) {
		byte[] solidityAddress = new byte[20];

		arraycopy(Ints.toByteArray(shard), 0, solidityAddress, 0, 4);
		arraycopy(Longs.toByteArray(realm), 0, solidityAddress, 4, 8);
		arraycopy(Longs.toByteArray(num), 0, solidityAddress, 12, 8);

		return solidityAddress;
	}

	public static AccountID accountParsedFromSolidityAddress(byte[] solidityAddress) {
		return AccountID.newBuilder()
				.setShardNum(Ints.fromByteArray(Arrays.copyOfRange(solidityAddress, 0, 4)))
				.setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 4, 12)))
				.setAccountNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 12, 20)))
				.build();
	}

	public static ContractID contractParsedFromSolidityAddress(byte[] solidityAddress) {
		return ContractID.newBuilder()
				.setShardNum(Ints.fromByteArray(Arrays.copyOfRange(solidityAddress, 0, 4)))
				.setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 4, 12)))
				.setContractNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 12, 20)))
				.build();
	}

	public static String asLiteralString(AccountID id) {
		return String.format(
				ENTITY_ID_FORMAT,
				id.getShardNum(),
				id.getRealmNum(),
				id.getAccountNum());
	}

	public static String asLiteralString(FileID id) {
		return String.format(
				ENTITY_ID_FORMAT,
				id.getShardNum(),
				id.getRealmNum(),
				id.getFileNum());
	}
}
