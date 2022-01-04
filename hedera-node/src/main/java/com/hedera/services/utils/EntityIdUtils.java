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
import com.hedera.services.context.primitives.StateView;
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
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.regex.Pattern;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedLowOrder32From;
import static java.lang.System.arraycopy;

public final class EntityIdUtils {
	private static final String ENTITY_ID_FORMAT = "%d.%d.%d";
	private static final String CANNOT_PARSE_PREFIX = "Cannot parse '";
	private static final Pattern ENTITY_NUM_RANGE_PATTERN = Pattern.compile("(\\d+)-(\\d+)");

	private EntityIdUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static String readableId(final Object o) {
		if (o instanceof Id id) {
			return String.format(ENTITY_ID_FORMAT, id.shard(), id.realm(), id.num());
		}
		if (o instanceof AccountID id) {
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
		}
		if (o instanceof FileID id) {
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getFileNum());
		}
		if (o instanceof TopicID id) {
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTopicNum());
		}
		if (o instanceof TokenID id) {
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTokenNum());
		}
		if (o instanceof ScheduleID id) {
			return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getScheduleNum());
		}
		if (o instanceof NftID id) {
			final var tokenID = id.getTokenID();
			return String.format(ENTITY_ID_FORMAT + ".%d",
					tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), id.getSerialNumber());
		}
		return String.valueOf(o);
	}

	/**
	 * Returns the {@code AccountID} represented by a literal of the form {@code <shard>.<realm>.<num>}.
	 *
	 * @param literal
	 * 		the account literal
	 * @return the corresponding id
	 * @throws IllegalArgumentException
	 * 		if the literal is not formatted correctly
	 */
	public static AccountID parseAccount(final String literal) {
		try {
			final var parts = parseLongTriple(literal);
			return AccountID.newBuilder()
					.setShardNum(parts[0])
					.setRealmNum(parts[1])
					.setAccountNum(parts[2])
					.build();
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException(String.format("Argument 'literal=%s' is not an account", literal), e);
		}
	}

	public static Pair<Long, Long> parseEntityNumRange(final String literal) {
		final var matcher = ENTITY_NUM_RANGE_PATTERN.matcher(literal);
		if (matcher.matches()) {
			try {
				final var left = Long.valueOf(matcher.group(1));
				final var right = Long.valueOf(matcher.group(2));
				if (left > right) {
					throw new IllegalArgumentException(
							"Range left endpoint " + left + " should be <= right endpoint " + right);
				}
				return Pair.of(left, right);
			} catch (NumberFormatException nfe) {
				throw new IllegalArgumentException("Argument literal='" + literal + "' has malformatted long value");
			}
		} else {
			throw new IllegalArgumentException("Argument literal='" + literal + "' is not a valid range literal");
		}
	}

	private static long[] parseLongTriple(final String dotDelimited) {
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

	public static AccountID asAccount(final ContractID cid) {
		return AccountID.newBuilder()
				.setRealmNum(cid.getRealmNum())
				.setShardNum(cid.getShardNum())
				.setAccountNum(cid.getContractNum())
				.build();
	}

	public static ContractID asContract(final AccountID id) {
		return ContractID.newBuilder()
				.setRealmNum(id.getRealmNum())
				.setShardNum(id.getShardNum())
				.setContractNum(id.getAccountNum())
				.build();
	}

	public static FileID asFile(final AccountID id) {
		return FileID.newBuilder()
				.setRealmNum(id.getRealmNum())
				.setShardNum(id.getShardNum())
				.setFileNum(id.getAccountNum())
				.build();
	}

	public static AccountID asAccount(final EntityId jId) {
		if (jId == null || jId.equals(EntityId.MISSING_ENTITY_ID)) {
			return StateView.WILDCARD_OWNER;
		}
		return AccountID.newBuilder()
				.setRealmNum(jId.realm())
				.setShardNum(jId.shard())
				.setAccountNum(jId.num())
				.build();
	}

	public static String asSolidityAddressHex(final AccountID id) {
		return CommonUtils.hex(asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum()));
	}

	public static byte[] asSolidityAddress(final ContractID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum());
	}

	public static byte[] asSolidityAddress(final AccountID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum());
	}

	public static String asSolidityAddressHex(Id id) {
		return CommonUtils.hex(asSolidityAddress((int) id.shard(), id.realm(), id.num()));
	}

	public static byte[] asSolidityAddress(final int shard, final long realm, final long num) {
		final byte[] solidityAddress = new byte[20];

		arraycopy(Ints.toByteArray(shard), 0, solidityAddress, 0, 4);
		arraycopy(Longs.toByteArray(realm), 0, solidityAddress, 4, 8);
		arraycopy(Longs.toByteArray(num), 0, solidityAddress, 12, 8);

		return solidityAddress;
	}

	public static AccountID accountParsedFromSolidityAddress(final byte[] solidityAddress) {
		return AccountID.newBuilder()
				.setShardNum(Ints.fromByteArray(Arrays.copyOfRange(solidityAddress, 0, 4)))
				.setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 4, 12)))
				.setAccountNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 12, 20)))
				.build();
	}

	public static ContractID contractParsedFromSolidityAddress(final byte[] solidityAddress) {
		return ContractID.newBuilder()
				.setShardNum(Ints.fromByteArray(Arrays.copyOfRange(solidityAddress, 0, 4)))
				.setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 4, 12)))
				.setContractNum(Longs.fromByteArray(Arrays.copyOfRange(solidityAddress, 12, 20)))
				.build();
	}

	public static String asLiteralString(final AccountID id) {
		return String.format(
				ENTITY_ID_FORMAT,
				id.getShardNum(),
				id.getRealmNum(),
				id.getAccountNum());
	}

	public static String asLiteralString(final FileID id) {
		return String.format(
				ENTITY_ID_FORMAT,
				id.getShardNum(),
				id.getRealmNum(),
				id.getFileNum());
	}

	public static String asRelationshipLiteral(long packedNumbers) {
		final var leftNum = unsignedHighOrder32From(packedNumbers);
		final var rightNum = unsignedLowOrder32From(packedNumbers);
		return "(" + STATIC_PROPERTIES.scopedIdLiteralWith(leftNum)
				+ ", " + STATIC_PROPERTIES.scopedIdLiteralWith(rightNum) + ")";
	}

	public static String asIdLiteral(int num) {
		return STATIC_PROPERTIES.scopedIdLiteralWith(numFromCode(num));
	}

	public static String asScopedSerialNoLiteral(long scopedSerialNo) {
		final var leftNum = unsignedHighOrder32From(scopedSerialNo);
		final var rightNum = unsignedLowOrder32From(scopedSerialNo);
		return STATIC_PROPERTIES.scopedIdLiteralWith(leftNum) + "." + rightNum;
	}

	public static boolean isAlias(final AccountID idOrAlias) {
		return idOrAlias.getAccountNum() == 0 && !idOrAlias.getAlias().isEmpty();
	}
}
