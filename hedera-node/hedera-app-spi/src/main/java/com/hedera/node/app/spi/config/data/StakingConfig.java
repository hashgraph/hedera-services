/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.Map;

@ConfigData("staking")
public record StakingConfig(@ConfigProperty long periodMins,
                            @ConfigProperty("rewardHistory.numStoredPeriods") int rewardHistoryNumStoredPeriods,
                            //ConfigProperty("startupHelper.recompute") Set<StakeStartupHelper.RecomputeType> startupHelperRecompute
                            @ConfigProperty("fees.nodeRewardPercentage") int feesNodeRewardPercentage,
                            @ConfigProperty("fees.stakingRewardPercentage") int feesStakingRewardPercentage,
                            @ConfigProperty Map<Long, Long> nodeMaxToMinStakeRatios,
                            @ConfigProperty(defaultValue = "true") boolean isEnabled,
                            @ConfigProperty long maxDailyStakeRewardThPerH,
                            @ConfigProperty(defaultValue = "false") boolean requireMinStakeToReward,
                            @ConfigProperty(defaultValue = "0") long rewardRate,
                            @ConfigProperty(defaultValue = "25000000000000000") long startThreshold,
                            @ConfigProperty int sumOfConsensusWeights
) {


}
