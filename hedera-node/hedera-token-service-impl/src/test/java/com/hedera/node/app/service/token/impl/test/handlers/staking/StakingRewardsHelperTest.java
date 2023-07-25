/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

public class StakingRewardsHelperTest {
    //    @Test
    //    void checksIfRewardableIfChangesHaveStakingFields() {
    //        counterparty.setStakePeriodStart(-1);
    //        counterparty.setStakedId(-1);
    //        final var changes = randomStakedNodeChanges(100L);
    //
    //        subject.setRewardsActivated(true);
    //
    //        // has changes to StakeMeta,
    //        assertTrue(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // valid fields, plus a ledger-managed staking field change
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        assertTrue(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // valid fields, plus a stakedToMe updated
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        assertTrue(subject.isRewardSituation(counterparty, 1_234_567, Collections.emptyMap()));
    //
    //        // rewards not activated
    //        subject.setRewardsActivated(false);
    //        assertFalse(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // declined reward on account, but changes have it as false
    //        counterparty.setDeclineReward(true);
    //        subject.setRewardsActivated(true);
    //        assertTrue(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // staked to account
    //        counterparty.setStakedId(2L);
    //        assertFalse(subject.isRewardSituation(counterparty, -1, changes));
    //    }
}
