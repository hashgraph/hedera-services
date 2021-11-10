package com.hedera.services.bdd.suites.contract.precompile;

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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
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
import static java.lang.System.arraycopy;

public class ContractMusicalChairsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractMusicalChairsSuite.class);

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ContractMusicalChairsSuite().runSuiteAsync();
	}

	public static byte[] asAddress(final AccountID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum());
	}

	public static byte[] asSolidityAddress(final int shard, final long realm, final long num) {
		final byte[] solidityAddress = new byte[20];

		arraycopy(Ints.toByteArray(shard), 0, solidityAddress, 0, 4);
		arraycopy(Longs.toByteArray(realm), 0, solidityAddress, 4, 8);
		arraycopy(Longs.toByteArray(num), 0, solidityAddress, 12, 8);

		return solidityAddress;
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
