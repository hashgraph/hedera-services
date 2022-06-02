package com.hedera.services.ledger.accounts.staking;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalDeclineRewardGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalStakedToMeGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.getAccountStakeeNum;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.getNodeStakeeNum;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.hasStakeFieldChanges;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateBalance;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateStakedToMe;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class StakingUtilsTest {
	@Mock
	private AddressBook addressBook;
	@Mock
	private Address address1;
	@Mock
	private Address address2;
	@Mock
	private BootstrapProperties bootstrapProperties;

	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo = new MerkleMap<>();
	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();

	@BeforeEach
	void setUp() {
		stakingInfo = buildsStakingInfoMap();
	}


	@Test
	void validatesIfAnyStakedFieldChanges() {
		assertTrue(hasStakeFieldChanges(randomStakeFieldChanges(100L)));
		assertFalse(hasStakeFieldChanges(randomNotStakeFieldChanges()));
	}

	@Test
	void updatesBalance() {
		var changes = randomStakeFieldChanges(100L);
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(100L, pendingChanges.changes(0).get(BALANCE));

		updateBalance(20L, 0, pendingChanges);
		assertEquals(120L, pendingChanges.changes(0).get(BALANCE));

		changes = randomNotStakeFieldChanges();
		pendingChanges.clear();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(null, pendingChanges.changes(0).get(BALANCE));
		updateBalance(20L, 0, pendingChanges);
		assertEquals(counterpartyBalance + 20L, pendingChanges.changes(0).get(BALANCE));
	}

	@Test
	void updatesStakedToMe() {
		var changes = randomStakeFieldChanges(100L);
		final long[] stakedToMeUpdates = new long[] { 2000L };
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);

		updateStakedToMe(0, 20L, stakedToMeUpdates, pendingChanges);
		assertEquals(2020L, stakedToMeUpdates[0]);

		changes = randomNotStakeFieldChanges();
		pendingChanges.clear();
		pendingChanges.include(counterpartyId, counterparty, changes);
		stakedToMeUpdates[0] = -1;

		updateStakedToMe(0, 20L, stakedToMeUpdates, pendingChanges);
		assertEquals(counterparty.getStakedToMe() + 20L, stakedToMeUpdates[0]);
	}

	@Test
	void worksAroundStakingToNewlyCreatedAccount() {
		final var changes = randomNotStakeFieldChanges();
		final var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.clear();
		pendingChanges.include(counterpartyId, null, changes);
		final var stakedToMeUpdates = new long[] { -1L };

		updateStakedToMe(0, 20L, stakedToMeUpdates, pendingChanges);
		assertEquals(20L, stakedToMeUpdates[0]);
	}

	@Test
	void getsFieldsCorrectlyFromChanges() {
		final var changes = randomStakeFieldChanges(100L);
		final long[] stakedToMeUpdates = new long[] { 2000L };

		assertEquals(0L, getAccountStakeeNum(changes));
		assertEquals(-2L, getNodeStakeeNum(changes));
		assertEquals(100L, finalBalanceGiven(counterparty, changes));
		assertEquals(true, finalDeclineRewardGiven(counterparty, changes));
		assertEquals(2000L, finalStakedToMeGiven(0, counterparty, stakedToMeUpdates));
	}

	@Test
	void getsFieldsCorrectlyIfNotFromChanges() {
		final var changes = randomNotStakeFieldChanges();
		final long[] stakedToMeUpdates = new long[] { -1l };

		assertEquals(0L, getAccountStakeeNum(changes));
		assertEquals(0L, getNodeStakeeNum(changes));
		assertEquals(counterpartyBalance, finalBalanceGiven(counterparty, changes));
		assertEquals(false, finalDeclineRewardGiven(counterparty, changes));
		assertEquals(counterPartyStake, finalStakedToMeGiven(0, counterparty, stakedToMeUpdates));
	}

	@Test
	void returnsDefaultsWhenAccountIsNull() {
		final var changes = randomNotStakeFieldChanges();
		final long[] stakedToMeUpdates = new long[] { -1l };

		assertEquals(0, finalBalanceGiven(null, changes));
		assertEquals(false, finalDeclineRewardGiven(null, changes));
		assertEquals(0, finalStakedToMeGiven(0, null, stakedToMeUpdates));
	}

	public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
		given(bootstrapProperties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(2_000_000_000L);
		given(bootstrapProperties.getIntProperty("staking.rewardHistory.numStoredPeriods")).willReturn(2);
		given(addressBook.getSize()).willReturn(2);
		given(addressBook.getAddress(0)).willReturn(address1);
		given(address1.getId()).willReturn(0L);
		given(addressBook.getAddress(1)).willReturn(address2);
		given(address2.getId()).willReturn(1L);

		final var info = buildStakingInfoMap(addressBook, bootstrapProperties);
		info.forEach((a, b) -> {
			b.setStakeToReward(300L);
			b.setStake(1000L);
			b.setStakeToNotReward(400L);
		});
		return info;
	}


	public static EntityChangeSet<AccountID, MerkleAccount, AccountProperty> buildPendingNodeStakeChanges() {
		var changes = randomStakeFieldChanges(100L);
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		return pendingChanges;
	}

	public static Map<AccountProperty, Object> randomStakeFieldChanges(final long newBalance) {
		final var map = new HashMap<AccountProperty, Object>();
		map.put(BALANCE, newBalance);
		map.put(AccountProperty.STAKED_ID, -2L);
		map.put(AccountProperty.DECLINE_REWARD, true);
		return map;
	}

	public static Map<AccountProperty, Object> randomNotStakeFieldChanges() {
		final var map = new HashMap<AccountProperty, Object>();
		map.put(AccountProperty.ALIAS, ByteString.copyFromUtf8("testing"));
		return map;
	}

	private final long amount = 1L;
	private static final long counterpartyBalance = 555L;
	private static final long counterPartyStake = 100L;
	private static final AccountID counterpartyId = AccountID.newBuilder().setAccountNum(321).build();
	private final AccountID stakingFundId = AccountID.newBuilder().setAccountNum(800).build();
	private static final MerkleAccount counterparty = MerkleAccountFactory.newAccount()
			.stakedId(-1)
			.stakedToMe(counterPartyStake)
			.number(EntityNum.fromAccountId(counterpartyId))
			.balance(counterpartyBalance)
			.get();
	private final MerkleAccount stakingFund = MerkleAccountFactory.newAccount()
			.number(EntityNum.fromAccountId(stakingFundId))
			.balance(amount)
			.get();
}
