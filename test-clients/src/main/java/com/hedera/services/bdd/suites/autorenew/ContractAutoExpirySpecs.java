package com.hedera.services.bdd.suites.autorenew;

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
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.assertTinybarAmountIsApproxUsd;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.INSTANT_HOG_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.literalInitcodeFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enableContractAutoRenewWith;

public class ContractAutoExpirySpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractAutoExpirySpecs.class);

	public static void main(String... args) {
		new ContractAutoExpirySpecs().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						renewsUsingContractFundsIfNoAutoRenewAccount(),
				}
		);
	}

	private HapiApiSpec renewsUsingContractFundsIfNoAutoRenewAccount() {
		final var initcode = "initcode";
		final var contractToRenew = "contractToRenew";
		final var initBalance = ONE_HBAR;
		final var minimalLifetime = 3;
		final var standardLifetime = 7776000L;
		final var creation = "creation";
		final var expectedExpiryPostRenew = new AtomicLong();

		return defaultHapiSpec("RenewsUsingContractFundsIfNoAutoRenewAccount")
				.given(
						createLargeFile(GENESIS, initcode, literalInitcodeFor("InstantStorageHog")),
						enableContractAutoRenewWith(minimalLifetime, 0),
						contractCreate(contractToRenew, INSTANT_HOG_CONS_ABI, 63)
								.gas(2_000_000)
								.entityMemo("")
								.bytecode(initcode)
								.autoRenewSecs(minimalLifetime)
								.balance(initBalance)
								.via(creation),
						withOpContext((spec, opLog) -> {
							final var lookup = getTxnRecord(creation);
							allRunFor(spec, lookup);
							final var record = lookup.getResponseRecord();
							final var birth = record.getConsensusTimestamp().getSeconds();
							expectedExpiryPostRenew.set(birth + minimalLifetime + standardLifetime);
							opLog.info("Expecting post-renewal expiry of {}", expectedExpiryPostRenew.get());
						}),
						contractUpdate(contractToRenew).newAutoRenew(7776000L),
						sleepFor(minimalLifetime * 1_000L + 500L)
				).when(
						// Any transaction will do
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L))
				).then(
						assertionsHold((spec, opLog) -> {
							final var lookup =
									getContractInfo(contractToRenew)
											.has(contractWith().expiry(expectedExpiryPostRenew.get()))
											.logged();
							allRunFor(spec, lookup);
							final var balance = lookup.getResponse().getContractGetInfo().getContractInfo().getBalance();
							final var renewalFee = initBalance - balance;
							final var canonicalUsdFee = 0.026;
							assertTinybarAmountIsApproxUsd(spec, canonicalUsdFee, renewalFee, 5.0);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
