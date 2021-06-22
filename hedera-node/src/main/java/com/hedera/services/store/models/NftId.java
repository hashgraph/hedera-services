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

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.TokenID;

public class NftId {
	private final long shard;
	private final long realm;
	private final long num;
	private final long serialNo;

	public NftId(long shard, long realm, long num, long serialNo) {
		this.shard = shard;
		this.realm = realm;
		this.num = num;
		this.serialNo = serialNo;
	}

	public long shard() {
		return shard;
	}

	public long realm() {
		return realm;
	}

	public long num() {
		return num;
	}

	public long serialNo() {
		return serialNo;
	}

	public TokenID tokenId() {
		return TokenID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setTokenNum(num)
				.build();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(NftId.class)
				.add("shard", shard)
				.add("realm", realm)
				.add("num", num)
				.add("serialNo", serialNo)
				.toString();
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(shard);
		result = 31 * result + Long.hashCode(realm);
		result = 31 * result + Long.hashCode(num);
		return 31 * result + Long.hashCode(serialNo);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || NftId.class != o.getClass()) {
			return false;
		}

		var that = (NftId) o;
		return this.shard == that.shard &&
				this.realm == that.realm &&
				this.num == that.num &&
				this.serialNo == that.serialNo;
	}
}
