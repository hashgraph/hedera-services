package com.hedera.services.state.migration;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.hedera.services.state.migration.Release0180Migration.cleanInconsistentTokenLists;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class Release0180MigrationTest {
	private MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> associations = new MerkleMap<>();

	@BeforeEach
	void setUp() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
	}

	@Test
	void cleansBrokenAssociationList() {
		addToken(consistentToken);
		addToken(inconsistentToken);
		addAccountWith(accountWithNoLinks);
		addAccountWith(accountWithOneLink, consistentToken);
		addAssociation(accountWithOneLink, consistentToken);
		addAccountWith(aAccountWithBrokenLinks, missingToken, consistentToken, inconsistentToken);
		addAssociation(aAccountWithBrokenLinks, consistentToken);
		addAccountWith(bAccountWithBrokenLinks, inconsistentToken);

		cleanInconsistentTokenLists(tokens, accounts, associations);

		final var aCleanedAccount = accounts.get(EntityNum.fromAccountId(aAccountWithBrokenLinks));
		final var aCleanedTokens = aCleanedAccount.tokens();
		assertEquals(1, aCleanedTokens.numAssociations());
		assertEquals(List.of(consistentToken), aCleanedTokens.asTokenIds());

		final var bCleanedAccount = accounts.get(EntityNum.fromAccountId(bAccountWithBrokenLinks));
		final var bCleanedTokens = bCleanedAccount.tokens();
		assertEquals(0, bCleanedTokens.numAssociations());
	}

	private void addAccountWith(final AccountID aid, final TokenID... tids)	{
		final var tokens = new MerkleAccountTokens();
		tokens.associateAll(Set.of(tids));
		final var account = new MerkleAccount();
		account.setTokens(tokens);
		accounts.put(EntityNum.fromAccountId(aid), account);
	}
	
	private void addToken(final TokenID tid) {
		final var token = new MerkleToken();
		tokens.put(EntityNum.fromTokenId(tid), token);
	}
	
	private void addAssociation(final AccountID aid, final TokenID tid) {
		final var rel = new MerkleTokenRelStatus();
		associations.put(EntityNumPair.fromLongs(aid.getAccountNum(), tid.getTokenNum()), rel);
	}

	private static final TokenID missingToken = IdUtils.asToken("0.0.9876");
	private static final TokenID consistentToken = IdUtils.asToken("0.0.3333");
	private static final TokenID inconsistentToken = IdUtils.asToken("0.0.1234");
	private static final AccountID accountWithNoLinks = IdUtils.asAccount("0.0.7777");
	private static final AccountID accountWithOneLink = IdUtils.asAccount("0.0.6666");
	private static final AccountID aAccountWithBrokenLinks = IdUtils.asAccount("0.0.5555");
	private static final AccountID bAccountWithBrokenLinks = IdUtils.asAccount("0.0.4444");
}
