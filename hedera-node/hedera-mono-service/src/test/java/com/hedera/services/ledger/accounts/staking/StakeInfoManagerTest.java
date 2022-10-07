/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.ledger.accounts.staking;

import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakeInfoManagerTest {
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
    @Mock private AddressBook addressBook;
    @Mock private Address address1;
    @Mock private Address address2;
    @Mock private BootstrapProperties bootstrapProperties;

    private StakeInfoManager subject;

    private final EntityNum node0Id = EntityNum.fromLong(0L);
    private final EntityNum node1Id = EntityNum.fromLong(1L);

    @BeforeEach
    void setUp() {
        stakingInfo = buildsStakingInfoMap();
        subject = new StakeInfoManager(() -> stakingInfo);
    }

    @Test
    void canUnclaimRewards() {
        subject.unclaimRewardsForStakeStart(0, 333);
        final var newNode0Info = stakingInfo.get(node0Id);
        assertEquals(333L, newNode0Info.getUnclaimedStakeRewardStart());
    }

    @Test
    void resetsRewardSUmHistory() {
        stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] {5, 5}));
        assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
        assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
        subject.clearAllRewardHistory();

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
    void getsCachedInput() {
        // old and new are same
        var oldStakingInfo = stakingInfo;
        oldStakingInfo.forEach((a, b) -> b.setStakeToReward(500L));
        subject.setPrevStakingInfos(oldStakingInfo);

        var expectedInfo = stakingInfo.get(node0Id);
        var actual = subject.mutableStakeInfoFor(0L);
        assertEquals(expectedInfo.getStakeToReward(), actual.getStakeToReward());

        // old and new are not same instances, but the cached value is null
        oldStakingInfo = buildsStakingInfoMap();
        oldStakingInfo.forEach((a, b) -> b.setStakeToReward(500L));
        subject.setPrevStakingInfos(oldStakingInfo);

        expectedInfo = stakingInfo.get(node0Id);
        actual = subject.mutableStakeInfoFor(0L);
        assertEquals(expectedInfo, actual);

        // old and new are not same instances, and the cached value is not null
        subject.setCache(new MerkleStakingInfo[] {oldStakingInfo.get(node0Id)});
        assertEquals(oldStakingInfo.get(node0Id), subject.getCache()[0]);
        actual = subject.mutableStakeInfoFor(0L);
        assertEquals(oldStakingInfo.get(node0Id).getStakeToReward(), actual.getStakeToReward());
    }

    public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT))
                .willReturn(2_000_000_000L);
        given(bootstrapProperties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(2);
        given(addressBook.getSize()).willReturn(2);
        given(addressBook.getAddress(0)).willReturn(address1);
        given(address1.getId()).willReturn(0L);
        given(addressBook.getAddress(1)).willReturn(address2);
        given(address2.getId()).willReturn(1L);

        final var info = buildStakingInfoMap(addressBook, bootstrapProperties);
        info.forEach(
                (a, b) -> {
                    b.setStakeToReward(300L);
                    b.setStake(1000L);
                    b.setStakeToNotReward(400L);
                    b.setStakeRewardStart(666L);
                });
        return info;
    }
}
