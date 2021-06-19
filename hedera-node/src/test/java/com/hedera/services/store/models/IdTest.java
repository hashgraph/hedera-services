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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdTest {
	@Test
	void hashCodeDiscriminates() {
		// given:
		final var aId = new Id(1, 2, 3);
		final var bId = new Id(0,2, 3);
		final var cId = new Id(1, 0, 3);
		final var dId = new Id(1, 2, 0);
		final var eId = new Id(1, 2, 3);

		// expect:
		assertNotEquals(bId.hashCode(), aId.hashCode());
		assertNotEquals(cId.hashCode(), aId.hashCode());
		assertNotEquals(dId.hashCode(), aId.hashCode());
		assertEquals(eId.hashCode(), aId.hashCode());
	}

	@Test
	void equalsDiscriminates() {
		// given:
		final var aId = new Id(1, 2, 3);
		final var bId = new Id(0,2, 3);
		final var cId = new Id(1, 0, 3);
		final var dId = new Id(1, 2, 0);
		final var eId = new Id(1, 2, 3);

		// expect:
		assertNotEquals(bId, aId);
		assertNotEquals(cId, aId);
		assertNotEquals(dId, aId);
		assertEquals(eId, aId);
		// and:
		assertNotEquals(aId, null);
		assertNotEquals(aId, new Object());
		assertEquals(aId, aId);
	}

	@Test
	void toGrpcIdAsExpected() {
		//
		long shard = 123;
		long realm = 456;
		long num = 789;

		final var id = new Id(shard, realm, num);
		final var tokenGrpcId = TokenID.newBuilder().setShardNum(shard).setRealmNum(realm).setTokenNum(num).build();
		final var AccountGrpcId = AccountID.newBuilder().setShardNum(shard).setRealmNum(realm).setAccountNum(num).build();

		// when:
		var tokenID = id.asGrpcToken();
		var accountID = id.asGrpcAccount();

		// expect:
		assertEquals(tokenGrpcId.getShardNum(), tokenID.getShardNum());
		assertEquals(tokenGrpcId.getRealmNum(), tokenID.getRealmNum());
		assertEquals(tokenGrpcId.getTokenNum(), tokenID.getTokenNum());
		assertEquals(AccountGrpcId.getShardNum(), accountID.getShardNum());
		assertEquals(AccountGrpcId.getRealmNum(), accountID.getRealmNum());
		assertEquals(AccountGrpcId.getAccountNum(), accountID.getAccountNum());
	}
}
