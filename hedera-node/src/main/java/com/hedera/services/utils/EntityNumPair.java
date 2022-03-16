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

import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Objects;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedNums;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedLowOrder32From;
import static com.hedera.services.utils.EntityNum.areValidNums;

public record EntityNumPair(long value) {
	static final EntityNumPair MISSING_NUM_PAIR = new EntityNumPair(0);

	public EntityNumPair {
		Objects.requireNonNull(value);
	}

	public static EntityNumPair fromLongs(long hi, long lo) {
		if (!isValidNum(hi) || !isValidNum(lo)) {
			return MISSING_NUM_PAIR;
		}
		final var value = packedNums(hi, lo);
		return new EntityNumPair(value);
	}

	public static EntityNumPair fromNftId(NftId id) {
		if (!areValidNums(id.shard(), id.realm())) {
			return MISSING_NUM_PAIR;
		}
		return fromLongs(id.num(), id.serialNo());
	}

	public EntityNum getHiPhi() {
		return EntityNum.fromLong(unsignedHighOrder32From(value));
	}

	public static EntityNumPair fromModelRel(TokenRelationship tokenRelationship) {
		final var token = tokenRelationship.getToken();
		final var account = tokenRelationship.getAccount();
		return fromLongs(account.getId().num(), token.getId().num());
	}

	public Pair<AccountID, TokenID> asAccountTokenRel() {
		return Pair.of(
				STATIC_PROPERTIES.scopedAccountWith(unsignedHighOrder32From(value)),
				STATIC_PROPERTIES.scopedTokenWith(unsignedLowOrder32From(value)));
	}

	public Pair<Long, Long> asTokenNumAndSerialPair() {
		return Pair.of(
				unsignedHighOrder32From(value),
				unsignedLowOrder32From(value));
	}

	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(value);
	}

	@Override
	public String toString() {
		return "PermHashLong("
				+ BitPackUtils.unsignedHighOrder32From(value)
				+ ", "
				+ BitPackUtils.unsignedLowOrder32From(value) + ")";
	}
}
