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

import java.time.LocalDate;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.isWithinRange;
import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.zoneUTC;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StakeChangeManagerTest {
	@Mock
	private AddressBook addressBook;
	@Mock
	private Address address1 = mock(Address.class);
	@Mock
	private Address address2 = mock(Address.class);
	@Mock
	private MerkleAccount account;
	@Mock
	private StakingInfoManager stakingInfoManager;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;

	private StakeChangeManager subject;
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;

	public static final long stakePeriodStart = LocalDate.now(zoneUTC).toEpochDay() - 1;

	@BeforeEach
	void setUp() {
		stakingInfo = buildsStakingInfoMap();
		subject = new StakeChangeManager(stakingInfoManager, () -> accounts);
	}

	@Test
	void validatesIfAnyStakedFieldChanges() {
		assertTrue(subject.hasStakeFieldChanges(randomStakeFieldChanges(100L)));
		assertFalse(subject.hasStakeFieldChanges(randomNotStakeFieldChanges()));
	}

	@Test
	void validatesIfStartPeriodIsWithinRange() {
		assertTrue(isWithinRange(stakePeriodStart - 365, stakePeriodStart));
		assertFalse(isWithinRange(-1, stakePeriodStart));
		assertFalse(isWithinRange(stakePeriodStart, stakePeriodStart));
	}

	@Test
	void updatesBalance() {
		var changes = randomStakeFieldChanges(100L);
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(100L, pendingChanges.changes(0).get(AccountProperty.BALANCE));

		subject.updateBalance(20L, 0, pendingChanges);
		assertEquals(120L, pendingChanges.changes(0).get(AccountProperty.BALANCE));

		changes = randomNotStakeFieldChanges();
		pendingChanges.clear();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(null, pendingChanges.changes(0).get(AccountProperty.BALANCE));
		subject.updateBalance(20L, 0, pendingChanges);
		assertEquals(20L, pendingChanges.changes(0).get(AccountProperty.BALANCE));
	}

	@Test
	void updatesStakedToMe() {
		var changes = randomStakeFieldChanges(100L);
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(2000L, pendingChanges.changes(0).get(AccountProperty.STAKED_TO_ME));

		subject.updateStakedToMe(0, 20L, pendingChanges);
		assertEquals(2020L, pendingChanges.changes(0).get(AccountProperty.STAKED_TO_ME));


		changes = randomNotStakeFieldChanges();
		pendingChanges.clear();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(null, pendingChanges.changes(0).get(AccountProperty.STAKED_TO_ME));
		subject.updateStakedToMe(0, 20L, pendingChanges);
		assertEquals(20L, pendingChanges.changes(0).get(AccountProperty.STAKED_TO_ME));
	}

	@Test
	void withdrawsStakeCorrectly() {
		assertEquals(1000L, stakingInfo.get(EntityNum.fromLong(3L)).getStake());
		assertEquals(300L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToReward());
		assertEquals(400L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToNotReward());
		subject.withdrawStake(3L, 100L, false);

		assertEquals(1000L, stakingInfo.get(EntityNum.fromLong(3L)).getStake());
		assertEquals(200L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToReward());
		assertEquals(400L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToNotReward());

		subject.withdrawStake(3L, 100L, true);

		assertEquals(1000L, stakingInfo.get(EntityNum.fromLong(3L)).getStake());
		assertEquals(200L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToReward());
		assertEquals(300L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToNotReward());
	}

	@Test
	void awardsStakeCorrectly() {
		assertEquals(1000L, stakingInfo.get(EntityNum.fromLong(3L)).getStake());
		assertEquals(300L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToReward());
		assertEquals(400L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToNotReward());
		subject.awardStake(3L, 100L, false);

		assertEquals(1000L, stakingInfo.get(EntityNum.fromLong(3L)).getStake());
		assertEquals(400L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToReward());
		assertEquals(400L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToNotReward());

		subject.awardStake(3L, 100L, true);

		assertEquals(1000L, stakingInfo.get(EntityNum.fromLong(3L)).getStake());
		assertEquals(400L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToReward());
		assertEquals(500L, stakingInfo.get(EntityNum.fromLong(3L)).getStakeToNotReward());
	}

	@Test
	void getsFieldsCorrectlyFromChanges() {
		final var changes = randomStakeFieldChanges(100L);

		assertEquals(0L, subject.getAccountStakeeNum(changes));
		assertEquals(-2L, subject.getNodeStakeeNum(changes));
		assertEquals(100L, subject.finalBalanceGiven(account, changes));
		assertEquals(true, subject.finalDeclineRewardGiven(account, changes));
		assertEquals(2000L, subject.finalStakedToMeGiven(account, changes));
	}

	@Test
	void getsFieldsCorrectlyIfNotFromChanges() {
		final var changes = randomNotStakeFieldChanges();

		given(account.getBalance()).willReturn(1000L);
		given(account.isDeclinedReward()).willReturn(true);
		given(account.getStakedToMe()).willReturn(200L);

		assertEquals(0L, subject.getAccountStakeeNum(changes));
		assertEquals(0L, subject.getNodeStakeeNum(changes));
		assertEquals(1000L, subject.finalBalanceGiven(account, changes));
		assertEquals(true, subject.finalDeclineRewardGiven(account, changes));
		assertEquals(200L, subject.finalStakedToMeGiven(account, changes));
	}


	private Map<AccountProperty, Object> randomStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.STAKED_ID, -2L,
				AccountProperty.DECLINE_REWARD, true,
				AccountProperty.STAKED_TO_ME, 2000L);
	}

	private Map<AccountProperty, Object> randomNotStakeFieldChanges() {
		return Map.of(
				AccountProperty.ALIAS, ByteString.copyFromUtf8("testing"));
	}


	public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
		given(addressBook.getSize()).willReturn(2);
		given(addressBook.getAddress(0)).willReturn(address1);
		given(address1.getMemo()).willReturn("0.0.3");
		given(addressBook.getAddress(1)).willReturn(address2);
		given(address2.getMemo()).willReturn("0.0.4");

		final var info = buildStakingInfoMap(addressBook);
		info.forEach((a, b) -> {
			b.setStakeToReward(300L);
			b.setStake(1000L);
			b.setStakeToNotReward(400L);
		});
		return info;
	}

	private static final long amount = 1L;
	private static final long partyBalance = 111L;
	private static final long counterpartyBalance = 555L;
	private static final AccountID partyId = AccountID.newBuilder().setAccountNum(123).build();
	private static final AccountID counterpartyId = AccountID.newBuilder().setAccountNum(321).build();
	private static final AccountID stakingFundId = AccountID.newBuilder().setAccountNum(800).build();
	private static final MerkleAccount party = MerkleAccountFactory.newAccount()
			.number(EntityNum.fromAccountId(partyId))
			.balance(partyBalance)
			.get();
	private static final MerkleAccount counterparty = MerkleAccountFactory.newAccount()
			.stakedId(-1)
			.number(EntityNum.fromAccountId(counterpartyId))
			.balance(counterpartyBalance)
			.get();
	private static final MerkleAccount stakingFund = MerkleAccountFactory.newAccount()
			.number(EntityNum.fromAccountId(stakingFundId))
			.balance(amount)
			.get();
}
