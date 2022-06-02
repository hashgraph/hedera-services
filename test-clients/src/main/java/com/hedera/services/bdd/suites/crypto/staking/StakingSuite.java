package com.hedera.services.bdd.suites.crypto.staking;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;

public class StakingSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(StakingSuite.class);

	public static void main(String... args) {
		new StakingSuite().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}
	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						enabledRewards()
				}
		);
	}

	private HapiApiSpec enabledRewards() {
		final var stakingAccount = "0.0.800";
		return defaultHapiSpec("AutoAccountCreationsHappyPath")
				.given(
						overriding("staking.startThreshold", "" + 10 * ONE_HBAR)
				).when(
						cryptoCreate("account").stakedNodeId(0L),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, stakingAccount, ONE_HBAR)
						).via("transferTxn"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, stakingAccount, 8 * ONE_HBAR)
						).via("moreTransfers"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, stakingAccount, ONE_HBAR)
						).via("shouldTriggerStaking"),
//						freezeOnly().payingWith(GENESIS).startingAt(Instant.now().plusSeconds(10))
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, "account", ONE_HBAR)
						).via("shouldSendRewards")
						// for now testing with the logs, once RewardCalculator is implemented this test will be complete.
						// tested
						// 1. Only on the last cryptoTransfer the following log is written `Staking rewards is activated and rewardSumHistory is cleared`
						// 2. that restarting from freeze, shows `Staking Rewards Activated ::true` from MerkleNetworkContext log
				).then(
				);
	}

}


