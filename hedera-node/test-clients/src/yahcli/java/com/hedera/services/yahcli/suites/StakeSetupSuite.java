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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.crypto.staking.StakingSuite;
import com.hedera.services.yahcli.config.ConfigManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StakeSetupSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(StakeSetupSuite.class);
    private final long stakePerNode;
    private final long stakingRewardRate;
    private final long rewardAccountBalance;
    private final ConfigManager configManager;
    private final Map<String, String> specConfig;

    private final Map<String, Long> accountsToStakedNodes = new HashMap<>();

    public Map<String, Long> getAccountsToStakedNodes() {
        return accountsToStakedNodes;
    }

    public StakeSetupSuite(
            final long stakePerNode,
            final long stakingRewardRate,
            final long rewardAccountBalance,
            @NonNull final ConfigManager configManager) {
        this.stakePerNode = stakePerNode;
        this.stakingRewardRate = stakingRewardRate;
        this.rewardAccountBalance = rewardAccountBalance;
        this.configManager = configManager;
        this.specConfig = configManager.asSpecConfig();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(startStakingAndExportCreatedStakers());
    }

    private HapiSpec startStakingAndExportCreatedStakers() {
        return HapiSpec.customHapiSpec("StartStakingAndExportCreatedStakers")
                .withProperties(specConfig)
                .given(
                        overriding(StakingSuite.STAKING_REWARD_RATE, String.valueOf(stakingRewardRate)),
                        TxnVerbs.cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(
                                HapiSuite.DEFAULT_PAYER, HapiSuite.STAKING_REWARD, rewardAccountBalance)))
                .when()
                .then(UtilVerbs.inParallel(configManager.nodeIdsInTargetNet().stream()
                        .map(nodeId -> TxnVerbs.cryptoCreate("stakerFor" + nodeId)
                                .stakedNodeId(nodeId)
                                .balance(stakePerNode)
                                .key(HapiSuite.DEFAULT_PAYER)
                                .exposingCreatedIdTo(
                                        id -> accountsToStakedNodes.put("0.0." + id.getAccountNum(), nodeId)))
                        .toArray(HapiSpecOperation[]::new)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
