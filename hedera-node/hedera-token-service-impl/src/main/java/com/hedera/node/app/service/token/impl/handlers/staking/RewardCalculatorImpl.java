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

package com.hedera.node.app.service.token.impl.handlers.staking;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.utils.RewardCalculator;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;

@Singleton
public class RewardCalculatorImpl implements RewardCalculator {
    @Inject
    public RewardCalculatorImpl() {}

    @Override
    public long epochSecondAtStartOfPeriod(final long stakePeriod) {
        throw new NotImplementedException("epochSecondAtStartOfPeriod is not implemented");
    }

    @Override
    public long estimatePendingRewards(final Account account, final StakingNodeInfo stakingNodeInfo) {
        throw new NotImplementedException("estimatePendingRewards is not implemented");
    }

    @Override
    public long computePendingReward(final Account account) {
        throw new NotImplementedException("computePendingReward is not implemented");
    }
}
