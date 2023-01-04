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
package com.hedera.node.app.service.mono.ledger.accounts.staking;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_STARTUP_HELPER_RECOMPUTE;
import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper.RecomputeType.NODE_STAKES;
import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper.RecomputeType.PENDING_REWARDS;
import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakingUtils.roundedToHbar;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.properties.PropertyNames;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccountState;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakeStartupHelperTest {
    private static final long removedNodeId = 2L;
    private static final long addedNodeId = 4L;
    private static final EntityNum removedNum = EntityNum.fromLong(removedNodeId);
    private static final EntityNum addedNum = EntityNum.fromLong(addedNodeId);
    private static final long[] preUpgradeNodeIds = {0L, 1L, 2L, 3L};
    private static final List<Long> postUpgradeNodeIds = List.of(0L, 1L, 3L, 4L);
    private static final List<Long> genesisNodeIds = List.of(0L, 1L, 2L, 3L, 4L);
    private static final SplittableRandom r = new SplittableRandom(1_234_567L);
    private static final int numStakingAccounts = 50;
    private static final long currentStakingPeriod = 1_234_567L;

    @Mock private StakeInfoManager stakeInfoManager;
    @Mock private PropertySource properties;
    @Mock private MerkleNetworkContext networkContext;
    @Mock private AddressBook addressBook;
    @Mock private RewardCalculator rewardCalculator;

    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;

    private StakeStartupHelper subject;

    @Test
    void alwaysPrepsStakeInfoManagerForNewAddressBookAndUpdatesMap() {
        givenPostRestartSubject();

        Assertions.assertTrue(stakingInfos.containsKey(removedNum));
        Assertions.assertFalse(stakingInfos.containsKey(addedNum));

        subject.doRestartHousekeeping(addressBook, stakingInfos);

        verify(stakeInfoManager).prepForManaging(postUpgradeNodeIds);
        assertNull(stakingInfos.get(EntityNum.fromLong(removedNodeId)));

        final var addedInfo = stakingInfos.get(EntityNum.fromLong(addedNodeId));
        assertNotNull(addedInfo);
        assertEquals(366, addedInfo.numRewardablePeriods());
    }

    @Test
    void prepsStakeInfoManagerForGenesisAddressBook() {
        givenGenesisSubject();

        subject.doGenesisHousekeeping(addressBook);

        verify(stakeInfoManager).prepForManaging(genesisNodeIds);
    }

    @Test
    void okToRequestNothingPostUpgrade() {
        givenPostUpgradeSubjectDoing();

        assertDoesNotThrow(
                () ->
                        subject.doUpgradeHousekeeping(
                                networkContext,
                                AccountStorageAdapter.fromInMemory(accounts),
                                stakingInfos));
    }

    @Test
    void recomputationsWorkAsExpected() {
        registerConstructables();
        final var expectedQuantities = givenStakingAccountsWithExpectedQuantities();
        givenPostUpgradeSubjectDoing(NODE_STAKES, PENDING_REWARDS);

        subject.doUpgradeHousekeeping(
                networkContext, AccountStorageAdapter.fromInMemory(accounts), stakingInfos);

        verify(networkContext).setPendingRewards(expectedQuantities.pendingRewards);

        for (final var postUpgradeInfo : stakingInfos.values()) {
            final var num = postUpgradeInfo.getKey();

            final var actualStakeToReward = stakingInfos.get(num).getStakeToReward();
            final var expectedStakeToReward =
                    Optional.ofNullable(expectedQuantities.nodeStakesToReward.get(num.longValue()))
                            .orElse(0L);
            assertEquals(
                    expectedStakeToReward,
                    actualStakeToReward,
                    "Wrong stake to reward for node " + num);

            final var actualStakeToNotReward = stakingInfos.get(num).getStakeToNotReward();
            final var expectedStakeToNotReward =
                    Optional.ofNullable(
                                    expectedQuantities.nodeStakesToNotReward.get(num.longValue()))
                            .orElse(0L);
            assertEquals(
                    expectedStakeToNotReward,
                    actualStakeToNotReward,
                    "Wrong stake to not reward for node " + num);
        }
    }

    private record ExpectedQuantities(
            long pendingRewards,
            Map<Long, Long> nodeStakesToReward,
            Map<Long, Long> nodeStakesToNotReward) {}

    private ExpectedQuantities givenStakingAccountsWithExpectedQuantities() {
        givenPostUpgradeNodeInfos();

        accounts = new MerkleMap<>();
        long pendingRewards = 0L;
        final Map<Long, Long> nodeStakesToReward = new HashMap<>();
        final Map<Long, Long> nodeStakesToNotReward = new HashMap<>();
        final EntityNum[] nums = new EntityNum[numStakingAccounts];
        for (int i = 0; i < numStakingAccounts; i++) {
            nums[i] = EntityNum.fromLong(r.nextLong(1_000_000L) + 1001L);
            final var account = new MerkleAccount();
            accounts.put(nums[i], account);
        }
        for (final var num : nums) {
            final var account = accounts.getForModify(num);
            account.setBalanceUnchecked(r.nextInt(123) * 100_000_000L);
            account.setStakedToMe(r.nextInt(123) * 100_000_000L);
            // Stake to a node 80% of the time
            if (r.nextDouble() < 0.80) {
                account.setStakePeriodStart(currentStakingPeriod - r.nextInt(2));
                final var nodeId = preUpgradeNodeIds[r.nextInt(preUpgradeNodeIds.length)];
                account.setStakedId(-nodeId - 1);
                // Delete the account 10% of the time
                if (r.nextDouble() < 0.10) {
                    account.setDeleted(true);
                    continue;
                }
                final var pretendReward = r.nextInt(123) * 100_000_000L;
                given(
                                rewardCalculator.estimatePendingRewards(
                                        account,
                                        stakingInfos.get(
                                                EntityNum.fromLong(
                                                        account.getStakedNodeAddressBookId()))))
                        .willReturn(pretendReward);
                pendingRewards += pretendReward;
                // Should this account decline rewards?
                account.setDeclineReward(r.nextBoolean());
                final var stake = roundedToHbar(account.totalStake());
                if (account.isDeclinedReward()) {
                    nodeStakesToNotReward.merge(nodeId, stake, Long::sum);
                } else {
                    nodeStakesToReward.merge(nodeId, stake, Long::sum);
                }
            }
        }

        return new ExpectedQuantities(pendingRewards, nodeStakesToReward, nodeStakesToNotReward);
    }

    private void givenGenesisSubject() {
        givenGenesisAddressBook();
        subject = new StakeStartupHelper(stakeInfoManager, properties, rewardCalculator);
    }

    private void givenPostRestartSubject() {
        givenWellKnownAddressBookAndInfosMap();
        given(properties.getIntProperty(PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(365);
        subject = new StakeStartupHelper(stakeInfoManager, properties, rewardCalculator);
    }

    private void givenPostUpgradeSubjectDoing(StakeStartupHelper.RecomputeType... types) {
        given(properties.getRecomputeTypesProperty(STAKING_STARTUP_HELPER_RECOMPUTE))
                .willReturn(Set.of(types));

        subject = new StakeStartupHelper(stakeInfoManager, properties, rewardCalculator);
    }

    void givenGenesisAddressBook() {
        given(addressBook.getSize()).willReturn(preUpgradeNodeIds.length + 1);
        int nextIndex = 0;
        for (final long preUpgradeNodeId : preUpgradeNodeIds) {
            given(addressBook.getId(nextIndex++)).willReturn(preUpgradeNodeId);
        }
        given(addressBook.getId(nextIndex)).willReturn(addedNodeId);
    }

    void givenWellKnownAddressBookAndInfosMap() {
        stakingInfos = new MerkleMap<>();
        given(addressBook.getSize()).willReturn(preUpgradeNodeIds.length);
        int nextIndex = 0;
        for (final long preUpgradeNodeId : preUpgradeNodeIds) {
            if (preUpgradeNodeId == removedNodeId) {
                stakingInfos.put(EntityNum.fromLong(preUpgradeNodeId), new MerkleStakingInfo());
                continue;
            }
            addPreUpgrade(nextIndex++, preUpgradeNodeId);
        }
        addPostUpgrade(nextIndex, addedNodeId);
    }

    void givenPostUpgradeNodeInfos() {
        stakingInfos = new MerkleMap<>();
        for (final long preUpgradeNodeId : preUpgradeNodeIds) {
            if (preUpgradeNodeId == removedNodeId) {
                continue;
            }
            stakingInfos.put(EntityNum.fromLong(preUpgradeNodeId), new MerkleStakingInfo());
        }
        stakingInfos.put(EntityNum.fromLong(addedNodeId), new MerkleStakingInfo());
    }

    private void addPreUpgrade(int index, long nodeId) {
        given(addressBook.getId(index)).willReturn(nodeId);
        stakingInfos.put(EntityNum.fromLong(nodeId), new MerkleStakingInfo());
    }

    private void addPostUpgrade(int index, long nodeId) {
        given(addressBook.getId(index)).willReturn(nodeId);
    }

    private static void registerConstructables() {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleBinaryTree.class, MerkleBinaryTree::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(
                            new ClassConstructorPair(
                                    MerkleAccountState.class, MerkleAccountState::new));
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(FCQueue.class, FCQueue::new));
        } catch (ConstructableRegistryException e) {
            throw new IllegalStateException(e);
        }
    }
}
