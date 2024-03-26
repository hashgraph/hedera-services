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

package com.swirlds.platform.consensus;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.platform.state.MinimumJudgeInfo;

/**
 * Configuration for the consensus algorithm
 *
 * @param roundsNonAncient The number of consensus rounds that are defined to be non-ancient. There can be more
 *                         non-ancient rounds, but these rounds will not have reached consensus. Once consensus is
 *                         reached on a new round (i.e.,fame is decided for all its witnesses), another round will
 *                         become ancient. Events, whose generation is older than the last non-ancient round generation,
 *                         are ancient. If they don't have consensus yet, they're stale, and will never reach consensus
 *                         and never have their transactions handled. Must not exceed the constant
 *                         {@link MinimumJudgeInfo#MAX_MINIMUM_JUDGE_INFO_SIZE}.
 * @param roundsExpired    Events this many rounds old are expired, and can be deleted from memory
 * @param coinFreq         a coin round happens every coinFreq rounds during an election (every other one is all true)
 */
@ConfigData("consensus")
public record ConsensusConfig(
        @ConfigProperty(defaultValue = "26") int roundsNonAncient,
        @ConfigProperty(defaultValue = "500") int roundsExpired,
        @ConfigProperty(defaultValue = "12") int coinFreq) {}
