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

package com.hedera.services.yahcli.commands.accounts;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.StakeSetupSuite;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "activate-staking",
        subcommands = {CommandLine.HelpCommand.class},
        description = "Activates staking on the target network (requires 0.0.2 payer)")
public class SetupStakeCommand implements Callable<Integer> {
    private static final long CANONICAL_REWARD_RATE = 6849L;
    private static final long CANONICAL_800_START_BALANCE = 250 * HapiSuite.ONE_MILLION_HBARS;
    private static final long TOTAL_HBAR_SUPPLY = 50_000_000_000L * 100_000_000L;

    @CommandLine.ParentCommand
    Yahcli yahcli;

    @CommandLine.Option(
            names = {"-p", "--per-node-amount"},
            paramLabel = "amount to stake to each node (use h/kh/mh/bh suffixes to scale)")
    String perNodeAmount;

    @CommandLine.Option(
            names = {"-r", "--staking-reward-rate"},
            paramLabel = "reward rate for staking (use h/kh/mh/bh suffixes to scale)")
    String stakingRewardRate;

    @CommandLine.Option(
            names = {"-b", "--reward-account-balance"},
            paramLabel = "balance of the reward account (use h/kh/mh/bh suffixes to scale)")
    String rewardAccountBalance;

    private static final Map<String, Long> SUFFIX_SCALES = Map.of(
            "h", HapiSuite.ONE_HBAR,
            "kh", 1_000L * HapiSuite.ONE_HBAR,
            "mh", 1_000_000L * HapiSuite.ONE_HBAR,
            "bh", 1_000_000_000L * HapiSuite.ONE_HBAR);

    @Override
    public Integer call() throws Exception {
        final var config = ConfigUtils.configFrom(yahcli);
        try {
            assertValidParams();
        } catch (Exception any) {
            COMMON_MESSAGES.warn("Please check the parameters and try again (got '" + any.getMessage() + "')");
            return 1;
        }
        long stakePerNode = TOTAL_HBAR_SUPPLY / config.numNodesInTargetNet() / 4;
        if (perNodeAmount != null) {
            stakePerNode = parseScaledAmount(perNodeAmount);
        }
        long rewardRate = CANONICAL_REWARD_RATE;
        if (stakingRewardRate != null) {
            rewardRate = parseScaledAmount(stakingRewardRate);
        }
        long start800Balance = CANONICAL_800_START_BALANCE;
        if (rewardAccountBalance != null) {
            start800Balance = parseScaledAmount(rewardAccountBalance);
        }
        final var delegate = new StakeSetupSuite(stakePerNode, rewardRate, start800Balance, config);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            final var msgSb = new StringBuilder("SUCCESS - staking activated on network '")
                    .append(config.getTargetName())
                    .append("' with,\n.i.   * Reward rate of               ")
                    .append(rewardRate)
                    .append("\n.i.   * 0.0.800 balance credit of    ")
                    .append(start800Balance)
                    .append("\n");
            final var perNodeStake = stakePerNode;
            delegate.getAccountsToStakedNodes().forEach((account, nodeId) -> msgSb.append(".i.   * ")
                    .append(account)
                    .append(" staked to node")
                    .append(nodeId)
                    .append(" for ")
                    .append(perNodeStake)
                    .append("\n"));
            COMMON_MESSAGES.info(msgSb.toString());
        } else {
            COMMON_MESSAGES.warn("FAILED - staking not initialized for '" + config.getTargetName() + "'");
            return 1;
        }

        return 0;
    }

    @SuppressWarnings({"java:S3776", "java:S1192"})
    private void assertValidParams() {
        if (perNodeAmount != null) {
            parseScaledAmount(perNodeAmount);
        }
        if (stakingRewardRate != null) {
            parseScaledAmount(stakingRewardRate);
        }
        if (rewardAccountBalance != null) {
            parseScaledAmount(rewardAccountBalance);
        }
    }

    private long parseScaledAmount(String amount) {
        if (amount.isBlank()) {
            return 0;
        }
        if ((amount.endsWith("h") || amount.endsWith("H")) && amount.length() > 1) {
            if (Character.isDigit(amount.charAt(amount.length() - 2))) {
                return Long.parseLong(amount.substring(0, amount.length() - 1)) * SUFFIX_SCALES.get("h");
            } else {
                return Long.parseLong(amount.substring(0, amount.length() - 2))
                        * SUFFIX_SCALES.get(
                                amount.substring(amount.length() - 2).toLowerCase());
            }
        } else {
            return Long.parseLong(amount);
        }
    }
}
