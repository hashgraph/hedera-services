package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PendingRewardsManagementTest {
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private EntityCreator creator;
	@Mock
	private PropertySource properties;

	private EndOfStakingPeriodCalculator subject;

	@BeforeEach
	void setUp() {
		subject = new EndOfStakingPeriodCalculator(
				() -> accounts,
				() -> stakingInfos,
				() -> networkCtx,
				syntheticTxnFactory,
				recordsHistorian,
				creator,
				properties);
	}

	@Test
	void rewardRateIsZeroIfPendingRewardsExceed800Balance() {
		given800Balance(123);
		given(networkCtx.getPendingRewards()).willReturn(124L);

		Assertions.assertEquals(0, subject.effectiveRateForCurrentPeriod());
	}

	private void given800Balance(final long balance) {
		given(properties.getLongProperty("accounts.stakingRewardAccount")).willReturn(800L);
		given(accounts.get(EntityNum.fromLong(800)))
				.willReturn(MerkleAccountFactory.newAccount().balance(balance).get());
	}
}