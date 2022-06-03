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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

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
//						enabledRewards(),
//						previewnetPlannedTest(),
//						sendToCarol(),
						endOfStakingPeriodRecTest(),
				}
		);
	}

	private HapiApiSpec sendToCarol() {
		return defaultHapiSpec("SendToCarol")
				.given(
//						getAccountInfo("0.0.1001").logged()
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, "0.0.1001", 1))
				).when().then(
				);
	}

	private HapiApiSpec endOfStakingPeriodRecTest() {
		return defaultHapiSpec("EndOfStakingPeriodRecTest")
				.given(
						cryptoCreate("a1")
								.balance(ONE_HUNDRED_HBARS)
								.stakedNodeId(0),
						cryptoCreate("a2")
								.balance(ONE_HUNDRED_HBARS)
								.stakedNodeId(0),
						cryptoTransfer(tinyBarsFromTo(GENESIS, "0.0.800", ONE_MILLION_HBARS)) // will trigger staking
				)
				.when(
						sleepFor(70_000)
				)
				.then(
						cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR))
								.via("trigger"),
						getTxnRecord("trigger")
								.logged()
								.hasChildRecordCount(1),
						sleepFor(70_000),
						cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR))
								.via("transfer"),
						getTxnRecord("transfer")
								.andAllChildRecords()
								.logged(),
						sleepFor(70_000),
						cryptoTransfer(tinyBarsFromTo("a1", "a2", ONE_HBAR))
								.via("transfer1"),
						getTxnRecord("transfer1")
								.andAllChildRecords()
								.logged()
				);
	}

	private HapiApiSpec previewnetPlannedTest() {
		final var alice = "alice";
		final var bob = "bob";
		final var carol = "carol";
		final var civilian = "civilian";
		final var stakingAccount = "0.0.800";
		final var unrewardedTxn = "unrewardedTxn";
		final var rewardedTxn = "rewardedTxn";
		return defaultHapiSpec("PreviewnetPlannedTest")
				.given(
						overriding("staking.startThreshold", "" + 10 * ONE_HBAR),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, stakingAccount, ONE_MILLION_HBARS))
				).when(
						cryptoCreate(civilian),
						cryptoCreate(alice)
								.stakedNodeId(0)
								.balance(20_000 * ONE_MILLION_HBARS),
						cryptoCreate(bob).balance(5_000 * ONE_MILLION_HBARS),
						cryptoCreate(carol).stakedNodeId(0),
						cryptoUpdate(bob).newStakedNodeId(0),
						// End of period ONE
						sleepFor(75_000)
				).then(
						cryptoTransfer(movingHbar(ONE_HBAR).distributing(carol, alice, bob))
								.payingWith(civilian)
								.via(unrewardedTxn),
						getTxnRecord(unrewardedTxn).andAllChildRecords().logged(),
						sleepFor(75_000),
						// rewardSumHistory now: [3, 0, 0, 0, 0, 0, 0, 0, 0, 0]
						cryptoTransfer(movingHbar(ONE_HBAR).distributing(carol, alice, bob))
								.payingWith(civilian)
								.via(rewardedTxn),
						getTxnRecord(rewardedTxn).andAllChildRecords().logged(),
						getAccountBalance(alice).logged(),
						getAccountBalance(bob).logged(),
						getAccountBalance(carol).logged()
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
						// for now testing with the logs, once RewardCalculator is implemented this test will be
						// complete.
						// tested
						// 1. Only on the last cryptoTransfer the following log is written `Staking rewards is
						// activated and rewardSumHistory is cleared`
						// 2. that restarting from freeze, shows `Staking Rewards Activated ::true` from
						// MerkleNetworkContext log
				).then(
				);
	}

}


