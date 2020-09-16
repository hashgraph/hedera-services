package com.hedera.services.bdd.suites.records;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

public class ContractRecordsSanityCheckSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractRecordsSanityCheckSuite.class);

	public static void main(String... args) {
		new ContractRecordsSanityCheckSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						contractCallWithSendRecordSanityChecks(),
						circularTransfersRecordSanityChecks(),
						contractCreateRecordSanityChecks(),
						contractUpdateRecordSanityChecks(),
						contractDeleteRecordSanityChecks(),
				}
		);
	}

	private HapiApiSpec contractDeleteRecordSanityChecks() {
		return defaultHapiSpec("ContractDeleteRecordSanityChecks")
				.given(flattened(
						fileCreate("bytecodeWithPayableConstructor")
								.path(PATH_TO_LOOKUP_BYTECODE),
						contractCreate("toBeDeleted")
								.bytecode("bytecodeWithPayableConstructor")
								.balance(1_000L),
						takeBalanceSnapshots("toBeDeleted", FUNDING, NODE, GENESIS)
				)).when(
						contractDelete("toBeDeleted").via("txn").transferAccount(GENESIS)
				).then(
						validateTransferListForBalances(
								"txn",
								List.of(FUNDING, NODE, GENESIS, "toBeDeleted"),
								Set.of("toBeDeleted")),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec contractCreateRecordSanityChecks() {
		return defaultHapiSpec("ContractCreateRecordSanityChecks")
				.given(flattened(
						fileCreate("bytecode").path(PATH_TO_LOOKUP_BYTECODE),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				)).when(
						contractCreate("test")
								.bytecode("bytecode")
								.balance(1_000L)
								.via("txn")
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS, "test")),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec contractCallWithSendRecordSanityChecks() {
		return defaultHapiSpec("ContractCallWithSendRecordSanityChecks")
				.given(flattened(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE),
						contractCreate("test").bytecode("bytecode"),
						UtilVerbs.takeBalanceSnapshots("test", FUNDING, NODE, GENESIS)
				)).when(
						contractCall("test", DEPOSIT_ABI, 1_000L).via("txn").sending(1_000L)
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS, "test")),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec circularTransfersRecordSanityChecks() {
		int NUM_ALTRUISTS = 3;
		Function<String, Long> INIT_BALANCE_FN = ignore -> 1_000_000L;
		int INIT_KEEP_AMOUNT_DIVISOR = 2;
		BigInteger STOP_BALANCE = BigInteger.valueOf(399_999L);

		String[] CANONICAL_ACCOUNTS = { FUNDING, NODE, GENESIS };
		String[] altruists = IntStream
				.range(0, NUM_ALTRUISTS)
				.mapToObj(i -> String.format("Altruist%s", new String(new char[] { (char) ('A' + i) })))
				.toArray(n -> new String[n]);

		return defaultHapiSpec("CircularTransfersRecordSanityChecks")
				.given(flattened(
						fileCreate("bytecode").path(PATH_TO_CIRCULAR_TRANSFERS_BYTECODE),
						Stream.of(altruists)
								.map(name -> contractCreate(name).bytecode("bytecode"))
								.toArray(n -> new HapiSpecOperation[n]),
						Stream.of(altruists)
								.map(name ->
										contractCall(
												name,
												SET_NODES_ABI,
												spec -> new Object[] {
														Stream.of(altruists)
																.map(a -> spec.registry().getContractId(
																		a).getContractNum())
																.toArray()
												}
										).via("txnFor" + name).sending(INIT_BALANCE_FN.apply(name))
								).toArray(n -> new HapiSpecOperation[n]),
						UtilVerbs.takeBalanceSnapshots(
								Stream.of(Stream.of(altruists), Stream.of(CANONICAL_ACCOUNTS))
										.flatMap(identity()).toArray(n -> new String[n])
						)
				)).when(
						contractCall(altruists[0], RECEIVE_AND_SEND_ABI, INIT_KEEP_AMOUNT_DIVISOR, STOP_BALANCE)
								.via("altruisticTxn")
				).then(
						validateTransferListForBalances(
								"altruisticTxn",
								Stream.concat(Stream.of(CANONICAL_ACCOUNTS), Stream.of(altruists))
										.collect(toList())
						),
						validateRecordTransactionFees("altruisticTxn"),
						addLogInfo((spec, infoLog) -> {
							long[] finalBalances = IntStream.range(0, NUM_ALTRUISTS)
									.mapToLong(ignore -> INIT_BALANCE_FN.apply("")).toArray();
							int i = 0, divisor = INIT_KEEP_AMOUNT_DIVISOR;
							while (true) {
								long toKeep = finalBalances[i] / divisor;
								if (toKeep < STOP_BALANCE.longValue()) {
									break;
								}
								int j = (i + 1) % NUM_ALTRUISTS;
								finalBalances[j] += (finalBalances[i] - toKeep);
								finalBalances[i] = toKeep;
								i = j;
								divisor++;
							}

							infoLog.info("Expected Final Balances");
							infoLog.info("-----------------------");
							for (i = 0; i < NUM_ALTRUISTS; i++) {
								infoLog.info("  " + i + " = " + finalBalances[i] + " tinyBars");
							}
						})
				);
	}

	private HapiApiSpec contractUpdateRecordSanityChecks() {
		return defaultHapiSpec("ContractUpdateRecordSanityChecks")
				.given(flattened(
						newKeyNamed("newKey").type(KeyFactory.KeyType.SIMPLE),
						fileCreate("bytecode").path(PATH_TO_LOOKUP_BYTECODE),
						contractCreate("test").bytecode("bytecode").balance(1_000L),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				)).when(
						contractUpdate("test").newKey("newKey").via("txn").fee(95_000_000L)
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	final String PATH_TO_PAYABLE_CONTRACT_BYTECODE = "src/main/resource/PayReceivable.bin";
	final String DEPOSIT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}]," +
			"\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\"," +
			"\"type\":\"function\"}";

	final String PATH_TO_LOOKUP_BYTECODE = bytecodePath("BalanceLookup");

	final String PATH_TO_CIRCULAR_TRANSFERS_BYTECODE = bytecodePath("CircularTransfers");
	final String SET_NODES_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint64[]\"," +
			"\"name\":\"accounts\"," +
			"\"type\":\"uint64[]\"}],\"name\":\"setNodes\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\"    ,\"type\":\"function\"}";
	final String RECEIVE_AND_SEND_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"keepAmountDivisor\",\"type\":\"uint32\"},{\"internalType\":\"uint256\"," +
			"\"name\":\"stopBalance\",\"type\":    \"uint256\"}],\"name\":\"receiveAndSend\",\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
}

