package com.hedera.services.state.migration;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.hedera.services.state.migration.ReleaseTwentyFiveMigration.initTreasuryTitleCounts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentyFiveMigrationTest {
	private final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
	private final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	private final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

	@Mock
	private ServicesState state;

	@Test
	void initsLinksAsExpected() {
		final var legacyTokens = new MerkleAccountTokens();
		legacyTokens.associateAll(Set.of(bNum.toGrpcTokenId(), cNum.toGrpcTokenId(), dNum.toGrpcTokenId()));
		final var legacyAccount = new MerkleAccount(List.of(
				new MerkleAccountState(), new FCQueue<ExpirableTxnRecord>(), legacyTokens));
		accounts.put(aNum, legacyAccount);
		List.of(bNum, cNum, dNum).forEach(num -> tokenRels.put(
				EntityNumPair.fromNums(aNum, num), new MerkleTokenRelStatus(1L, true, false, true)));

		ReleaseTwentyFiveMigration.buildAccountTokenAssociationsLinkedList(accounts, tokenRels);

		assertEquals(3, legacyAccount.getNumAssociations());
		assertEquals(3, legacyAccount.getNumPositiveBalances());
		assertEquals(dNum.longValue(), legacyAccount.getHeadTokenId());
	}

	@Test
	void initsTitleCountsAsExpected() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleToken.class, MerkleToken::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));

		tokens.put(secondANum, aSecondToken);
		tokens.put(firstANum, aFirstToken);
		tokens.put(deletedNum, deletedToken);
		tokens.put(firstBNum, bFirstToken);
		tokens.put(problemNum, theProblemToken);
		accounts.put(aNum, a);
		accounts.put(bNum, b);
		accounts.put(cNum, c);

		given(state.tokens()).willReturn(tokens);
		given(state.accounts()).willReturn(accounts);

		initTreasuryTitleCounts(state);

		final var updatedA = accounts.get(aNum);
		final var updatedB = accounts.get(bNum);
		final var updatedC = accounts.get(cNum);
		assertEquals(2, updatedA.getNumTreasuryTitles());
		assertEquals(1, updatedB.getNumTreasuryTitles());
		assertEquals(0, updatedC.getNumTreasuryTitles());
	}

	private final EntityNum aNum = EntityNum.fromLong(1234);
	private final EntityNum bNum = EntityNum.fromLong(2345);
	private final EntityNum cNum = EntityNum.fromLong(3456);
	private final EntityNum dNum = EntityNum.fromLong(4567);
	private final EntityNum deletedNum = EntityNum.fromLong(666);
	private final EntityNum firstANum = EntityNum.fromLong(777);
	private final EntityNum firstBNum = EntityNum.fromLong(888);
	private final EntityNum secondANum = EntityNum.fromLong(999);
	private final EntityNum problemNum = EntityNum.fromLong(1000);
	private final MerkleAccount a = new MerkleAccount();
	private final MerkleAccount b = new MerkleAccount();
	private final MerkleAccount c = new MerkleAccount();
	private final MerkleToken deletedToken = new MerkleToken();
	private final MerkleToken aFirstToken = new MerkleToken();
	private final MerkleToken bFirstToken = new MerkleToken();
	private final MerkleToken aSecondToken = new MerkleToken();
	private final MerkleToken theProblemToken = new MerkleToken();
	{
		deletedToken.setDeleted(true);
		deletedToken.setTreasury(bNum.toEntityId());
		aFirstToken.setTreasury(aNum.toEntityId());
		aSecondToken.setTreasury(aNum.toEntityId());
		bFirstToken.setTreasury(bNum.toEntityId());
		theProblemToken.setTreasury(dNum.toEntityId());
	}
}
