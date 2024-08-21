/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculator;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculatorImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandlerImpl;
import dagger.Binds;
import dagger.Module;

/**
 * Dagger module of the token service
 */
@Module
public interface TokenServiceInjectionModule {
    /**
     * Binds the {@link CryptoSignatureWaivers} to the {@link CryptoSignatureWaiversImpl}
     * @param impl the implementation of the {@link CryptoSignatureWaivers}
     * @return the bound implementation
     */
    @Binds
    CryptoSignatureWaivers cryptoSignatureWaivers(CryptoSignatureWaiversImpl impl);

    /**
     * Binds the {@link StakingRewardsHandler} to the {@link StakingRewardsHandlerImpl}
     * @param stakingRewardsHandler the implementation of the {@link StakingRewardsHandler}
     * @return the bound implementation
     */
    @Binds
    StakingRewardsHandler stakingRewardHandler(StakingRewardsHandlerImpl stakingRewardsHandler);
    /**
     * Binds the {@link StakeRewardCalculator} to the {@link StakeRewardCalculatorImpl}
     * @param rewardCalculator the implementation of the {@link StakeRewardCalculator}
     * @return the bound implementation
     */
    @Binds
    StakeRewardCalculator stakeRewardCalculator(StakeRewardCalculatorImpl rewardCalculator);
}
