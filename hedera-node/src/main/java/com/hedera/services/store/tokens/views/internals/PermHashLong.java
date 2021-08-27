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

import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

public class PermHashLong {
	private final long value;

	public PermHashLong(long value) {
		this.value = value;
	}

	public static PermHashLong fromModelRel(TokenRelationship tokenRelationship) {
		throw new AssertionError("Not implemented!");
	}

	public static PermHashLong asPhl(long i) {
		return new PermHashLong(i);
	}

	public static PermHashLong asPhl(int hi, int lo) {
		throw new AssertionError("Not implemented!");
	}

	public static PermHashLong asPhl(long hi, long lo) {
		throw new AssertionError("Not implemented!");
	}

	public static PermHashLong fromNftId(NftId id) {
		throw new AssertionError("Not implemented!");
	}

	public TokenID hiAsGrpcTokenId() {
		throw new AssertionError("Not implemented!");
	}

	public PermHashInteger hiAsPhi() {
		throw new AssertionError("Not implemented!");
	}

	public Pair<AccountID, TokenID> asAccountTokenRel() {
		return Pair.of(
				AccountID.newBuilder()
						.setAccountNum(-1)
						.build(),
				TokenID.newBuilder()
						.setTokenNum(-1)
						.build());
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
}
