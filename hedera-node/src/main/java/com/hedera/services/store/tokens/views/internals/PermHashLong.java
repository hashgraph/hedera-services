package com.hedera.services.store.tokens.views.internals;

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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedNums;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedLowOrder32From;

public class PermHashLong {
	private final long value;

	public PermHashLong(long value) {
		this.value = value;
	}

	public static PermHashLong fromLongs(long hi, long lo) {
		final var value = packedNums(hi, lo);
		return new PermHashLong(value);
	}

	public static PermHashLong fromNftId(NftId id) {
		return fromLongs(id.num(), id.serialNo());
	}

	public PermHashInteger getHiPhi() {
		return PermHashInteger.fromLong(unsignedHighOrder32From(value));
	}

	public static PermHashLong fromModelRel(TokenRelationship tokenRelationship) {
		final var token = tokenRelationship.getToken();
		final var account = tokenRelationship.getAccount();
		return fromLongs(account.getId().getNum(), token.getId().getNum());
	}

	public Pair<AccountID, TokenID> asAccountTokenRel() {
		return Pair.of(
				STATIC_PROPERTIES.scopedAccountWith(unsignedHighOrder32From(value)),
				STATIC_PROPERTIES.scopedTokenWith(unsignedLowOrder32From(value)));
	}

	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(value);
	}

	public long getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || PermHashLong.class != o.getClass()) {
			return false;
		}

		var that = (PermHashLong) o;

		return this.value == that.value;
	}

	@Override
	public String toString() {
		return "PermHashLong("
				+ BitPackUtils.unsignedHighOrder32From(value)
				+ ", "
				+ BitPackUtils.unsignedLowOrder32From(value) + ")";
	}
}
