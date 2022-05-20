package com.hedera.services.ledger.interceptors;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;
import static com.hedera.services.ledger.interceptors.StakeChangeManager.isWithinRange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class StakeChangeManagerTest {
	private StakeChangeManager subject;

	@Mock
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;

	private static final long stakePeriodStart = LocalDate.now(zoneUTC).toEpochDay() - 1;

	@BeforeEach
	void setUp() {
		subject = new StakeChangeManager(() -> stakingInfo);
	}

	@Test
	void validatesIfAnyStakedFieldChanges() {
		assertTrue(subject.hasStakeFieldChanges(randomStakeFieldChanges(100L)));
		assertFalse(subject.hasStakeFieldChanges(randomNotStakeFieldChanges(100L)));
	}

	@Test
	void validatesIfStartPeriodIsWithinRange() {
		assertTrue(isWithinRange(stakePeriodStart - 365 , stakePeriodStart));
		assertFalse(isWithinRange(-1 , stakePeriodStart));
		assertFalse(isWithinRange(stakePeriodStart , stakePeriodStart));
	}

	@Test
	void updatesBalance(){
		final var changes = randomStakeFieldChanges(100L);
		final var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		assertEquals(100L, pendingChanges.changes(0).get(AccountProperty.BALANCE));

		subject.updateBalance(20L, 0, pendingChanges);

		assertEquals(120L, pendingChanges.changes(0).get(AccountProperty.BALANCE));
	}


	private Map<AccountProperty, Object> randomStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.STAKED_ID, -2L);
	}

	private Map<AccountProperty, Object> randomNotStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.ALIAS, ByteString.copyFromUtf8("testing"));
	}

	private Map<AccountProperty, Object> randomStakeFieldDeclineRewardChanges(final long newBalance) {
		return Map.of(
				AccountProperty.DECLINE_REWARD, false,
				AccountProperty.STAKED_ID, -2L);
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
