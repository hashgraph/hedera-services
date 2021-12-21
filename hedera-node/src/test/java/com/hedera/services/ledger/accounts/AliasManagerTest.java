package com.hedera.services.ledger.accounts;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasManagerTest {
	private static final ByteString alias = ByteString.copyFromUtf8("aaaa");
	private static final EntityNum num = EntityNum.fromLong(1234L);

	@Test
	void createAliasAddsToMap() {
		final var subject = new AliasManager();
		subject.link(alias, num);

		assertEquals(Map.of(alias, num), subject.getAliases());
	}

	@Test
	void forgetAliasRemovesFromMap() {
		final var subject = new AliasManager();
		subject.getAliases().put(alias, num);

		subject.unlink(alias);

		assertFalse(subject.getAliases().containsKey(alias));
	}

	@Test
	void settersAndGettersWork() {
		final var a = new EntityNum(1);
		final var b = new EntityNum(2);
		ByteString aliasA = ByteString.copyFromUtf8("aaaa");
		ByteString aliasB = ByteString.copyFromUtf8("bbbb");
		Map<ByteString, EntityNum> expectedMap = new HashMap<>() {{
			put(aliasA, a);
			put(aliasB, b);
		}};

		var subject = new AliasManager();
		assertTrue(subject.getAliases().isEmpty());

		subject.setAliases(expectedMap);
		assertEquals(expectedMap, subject.getAliases());
		assertEquals(b, subject.lookupIdBy(ByteString.copyFromUtf8("bbbb")));
		assertTrue(subject.contains(aliasA));
	}

	@Test
	void rebuildsFromMap() {
		final var withNum = EntityNum.fromLong(1L);
		final var withoutNum = EntityNum.fromLong(2L);
		final var expiredAlias = ByteString.copyFromUtf8("zyxwvut");
		final var upToDateAlias = ByteString.copyFromUtf8("abcdefg");

		final var accountWithAlias = new MerkleAccount();
		accountWithAlias.setAlias(upToDateAlias);
		final var accountWithNoAlias = new MerkleAccount();

		final MerkleMap<EntityNum, MerkleAccount> liveAccounts = new MerkleMap<>();
		liveAccounts.put(withNum, accountWithAlias);
		liveAccounts.put(withoutNum, accountWithNoAlias);

		final var subject = new AliasManager();
		subject.getAliases().put(expiredAlias, withoutNum);
		subject.rebuildAliasesMap(liveAccounts);

		final var finalMap = subject.getAliases();
		assertEquals(1, finalMap.size());
		assertEquals(withNum, subject.getAliases().get(upToDateAlias));

		// finally when
		subject.forgetAliasIfPresent(withNum, liveAccounts);
		assertEquals(0, subject.getAliases().size());
	}
}
