package com.hedera.services.context.properties;

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

import com.hedera.services.config.HederaNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;

public enum StaticPropertiesHolder {
	STATIC_PROPERTIES;

	private long shard = 0;
	private long realm = 0;

	public void setNumbersFrom(HederaNumbers hederaNum) {
		shard = hederaNum.shard();
		realm = hederaNum.realm();
	}

	public long getShard() {
		return shard;
	}

	public long getRealm() {
		return realm;
	}

	public AccountID scopedAccountWith(long num) {
		return AccountID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setAccountNum(num)
				.build();
	}

	public TokenID scopedTokenWith(long num) {
		return TokenID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setTokenNum(num)
				.build();
	}

	public ScheduleID scopedScheduleWith(long num) {
		return ScheduleID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setScheduleNum(num)
				.build();
	}

	public String scopedIdLiteralWith(long num) {
		return shard + "." + realm + "." + num;
	}
}
