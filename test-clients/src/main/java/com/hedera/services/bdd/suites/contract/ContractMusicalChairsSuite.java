package com.hedera.services.bdd.suites.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MUSICAL_CHAIRS_SIT_DOWN;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;

public class ContractMusicalChairsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractMusicalChairsSuite.class);

	public static void main(String... args) {
		new ContractMusicalChairsSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(playGame());
	}

	private HapiApiSpec playGame() {
		final var dj = "dj";
		final var players = IntStream.range(1, 30).mapToObj(i -> "Player" + i).collect(Collectors.toList());

		List<HapiSpecOperation> given = new ArrayList<>();
		List<HapiSpecOperation> when = new ArrayList<>();
		List<HapiSpecOperation> then = new ArrayList<>();

		////// Create contract //////
		given.add(UtilVerbs.overriding("contracts.throttle.throttleByGas", "false"));
		given.add(cryptoCreate(dj).balance(10 * ONE_HUNDRED_HBARS));
		given.add(fileCreate("bytecode").path(ContractResources.MUSICAL_CHAIRS_CONTRACT));
		given.add(withOpContext((spec, opLog) ->
				allRunFor(
						spec,
						contractCreate("Musical Chairs", ContractResources.MUSICAL_CHAIRS_CONSTRUCTOR,
								(Object) asAddress(spec.registry().getAccountID(dj)))
								.payingWith(dj)
								.bytecode("bytecode"))));

		////// Add the players //////
		players.stream()
				.map(TxnVerbs::cryptoCreate)
				.forEach(given::add);

		////// Start the music! //////
		when.add(contractCall("Musical Chairs",
				ContractResources.MUSICAL_CHAIRS_START_MUSIC)
				.payingWith("dj"));

		////// 100 "random" seats taken //////
		new Random(0x1337)
				.ints(100, 0, 29)
				.forEach(i ->
						when.add(contractCall("Musical Chairs",
								MUSICAL_CHAIRS_SIT_DOWN)
								.payingWith(players.get(i))
								.hasAnyStatusAtAll())); // sometimes a player sits too soon, so don't fail on reverts


		////// Stop the music! //////
		then.add(contractCall("Musical Chairs",
				ContractResources.MUSICAL_CHAIRS_STOP_MUSIC)
				.payingWith("dj"));

		////// And the winner is..... //////
		then.add(withOpContext((spec, opLog) ->
				allRunFor(
						spec,
						contractCallLocal("Musical Chairs",
								ContractResources.MUSICAL_CHAIRS_WHO_IS_ON_THE_BUBBLE)
								.has(resultWith().resultThruAbi(ContractResources.MUSICAL_CHAIRS_WHO_IS_ON_THE_BUBBLE,
										isLiteralResult(new Object[] { asAddress(
												spec.registry().getAccountID("Player13")) }))))));
		then.add(UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties"));

		return defaultHapiSpec("playGame")
				.given(given.toArray(HapiSpecOperation[]::new))
				.when(when.toArray(HapiSpecOperation[]::new))
				.then(then.toArray(HapiSpecOperation[]::new));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
