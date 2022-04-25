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
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.migration.ReleaseTwentyFiveMigration.initTreasuryTitleCounts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentyFiveMigrationTest {
	private MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	@Mock
	private ServicesState state;

	@Test
	void migratesAsExpected() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleToken.class, MerkleToken::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));

		tokens.put(secondANum, aSecondToken);
		tokens.put(firstANum, aFirstToken);
		tokens.put(deletedNum, deletedToken);
		tokens.put(firstBNum, bFirstToken);
		tokens.put(problemNum, theProblemToken);
		accounts.put(aAccountNum, a);
		accounts.put(bAccountNum, b);
		accounts.put(cAccountNum, c);

		given(state.tokens()).willReturn(tokens);
		given(state.accounts()).willReturn(accounts);

		initTreasuryTitleCounts(state);

		final var updatedA = accounts.get(aAccountNum);
		final var updatedB = accounts.get(bAccountNum);
		final var updatedC = accounts.get(cAccountNum);
		assertEquals(2, updatedA.getNumTreasuryTitles());
		assertEquals(1, updatedB.getNumTreasuryTitles());
		assertEquals(0, updatedC.getNumTreasuryTitles());
	}

	private final EntityNum aAccountNum = EntityNum.fromLong(1234);
	private final EntityNum bAccountNum = EntityNum.fromLong(2345);
	private final EntityNum cAccountNum = EntityNum.fromLong(3456);
	private final EntityNum dAccountNum = EntityNum.fromLong(4567);
	private final EntityNum deletedNum = EntityNum.fromLong(666);
	private final EntityNum firstANum = EntityNum.fromLong(777);
	private final EntityNum firstBNum = EntityNum.fromLong(888);
	private final EntityNum secondANum = EntityNum.fromLong(999);
	private final EntityNum problemNum = EntityNum.fromLong(1000);
	private MerkleAccount a = new MerkleAccount();
	private MerkleAccount b = new MerkleAccount();
	private MerkleAccount c = new MerkleAccount();
	private MerkleToken deletedToken = new MerkleToken();
	private MerkleToken aFirstToken = new MerkleToken();
	private MerkleToken bFirstToken = new MerkleToken();
	private MerkleToken aSecondToken = new MerkleToken();
	private MerkleToken theProblemToken = new MerkleToken();
	{
		deletedToken.setDeleted(true);
		deletedToken.setTreasury(bAccountNum.toEntityId());
		aFirstToken.setTreasury(aAccountNum.toEntityId());
		aSecondToken.setTreasury(aAccountNum.toEntityId());
		bFirstToken.setTreasury(bAccountNum.toEntityId());
		theProblemToken.setTreasury(dAccountNum.toEntityId());
	}
}
