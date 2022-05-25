package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StakeInfoManagerTest {
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
	@Mock
	private AddressBook addressBook;
	@Mock
	private Address address1;
	@Mock
	private Address address2;

	private StakeInfoManager subject;

	private final EntityNum node0Id = EntityNum.fromLong(0L);
	private final EntityNum node1Id = EntityNum.fromLong(1L);

	@BeforeEach
	void setUp() {
		stakingInfo = buildsStakingInfoMap();
		subject = new StakeInfoManager(() -> stakingInfo);
	}

	@Test
	void resetsRewardSUmHistory() {
		stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] { 5, 5 }));
		assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
		assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
		subject.clearRewardsHistory();

		assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
		assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
	}

	@Test
	void getsCorrectStakeInfo() {
		final var expectedInfo = stakingInfo.get(node0Id);
		final var actual = subject.mutableStakeInfoFor(0L);
		assertEquals(expectedInfo, actual);
	}

	@Test
	void getsCachedInput(){
		final var oldStakingInfo = buildsStakingInfoMap();
		oldStakingInfo.forEach((a, b) -> b.setStakeToReward(500L));

		final var expectedInfo = stakingInfo.get(node0Id);
		var actual = subject.mutableStakeInfoFor(0L);
		assertEquals(expectedInfo, actual);

		subject.setCurrentStakingInfos(new MerkleStakingInfo[]{oldStakingInfo.get(node0Id)});
		actual = subject.mutableStakeInfoFor(0L);
		assertEquals(oldStakingInfo.get(node0Id).getStakeToReward(), actual.getStakeToReward());
	}

	public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
		given(addressBook.getSize()).willReturn(2);
		given(addressBook.getAddress(0)).willReturn(address1);
		given(address1.getId()).willReturn(0L);
		given(addressBook.getAddress(1)).willReturn(address2);
		given(address2.getId()).willReturn(1L);

		final var info = buildStakingInfoMap(addressBook);
		info.forEach((a, b) -> {
			b.setStakeToReward(300L);
			b.setStake(1000L);
			b.setStakeToNotReward(400L);
		});
		return info;
	}
}
