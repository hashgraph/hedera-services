package com.hedera.services.store.models;

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

import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.TokenID;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

public record NftId(long shard, long realm, long num, long serialNo) implements Comparable<NftId> {
	private static final Comparator<NftId> NATURAL_ORDER = Comparator.comparingLong(NftId::num)
			.thenComparingLong(NftId::serialNo)
			.thenComparingLong(NftId::shard)
			.thenComparingLong(NftId::realm);
	
	public TokenID tokenId() {
		return TokenID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setTokenNum(num)
				.build();
	}

	public static NftId withDefaultShardRealm(final long num, final long serialNo) {
		return new NftId(STATIC_PROPERTIES.getShard(), STATIC_PROPERTIES.getRealm(), num, serialNo);
	}

	public static NftId fromGrpc(final TokenID tokenId, final long serialNo) {
		return new NftId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum(), serialNo);
	}

	public EntityNumPair asEntityNumPair() {
		return EntityNumPair.fromLongs(num, serialNo);
	}

	@Override
	public int compareTo(final @NotNull NftId that) {
		return NATURAL_ORDER.compare(this, that);
	}
}
