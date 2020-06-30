package com.hedera.services.bdd.suites.fees;

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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/* --------------------------- SPEC STATIC IMPORTS --------------------------- */
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.HapiApiSpec.CostSnapshotMode.*;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.*;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.*;
/* --------------------------------------------------------------------------- */

public class CostOfEverythingSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CostOfEverythingSuite.class);

//	CostSnapshotMode costSnapshotMode = TAKE;
	CostSnapshotMode costSnapshotMode = COMPARE;

	public static void main(String... args) {
		new CostOfEverythingSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return Stream.of(
//				cryptoCreatePaths(),
//				cryptoTransferPaths(),
//				cryptoGetAccountInfoPaths(),
//				cryptoGetAccountRecordsPaths(),
				transactionGetRecordPaths()
		).map(Stream::of).reduce(Stream.empty(), Stream::concat).collect(toList());
	}

	HapiApiSpec[] transactionGetRecordPaths() {
		return new HapiApiSpec[] {
				txnGetCreateRecord(),
				txnGetSmallTransferRecord(),
				txnGetLargeTransferRecord(),
		};
	}

	HapiApiSpec txnGetCreateRecord() {
		return customHapiSpec("TxnGetCreateRecord")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("hairTriggerPayer")
								.balance(99_999_999_999L)
								.sendThreshold(1L)
				).when(
						cryptoCreate("somebodyElse")
								.payingWith("hairTriggerPayer")
								.via("txn")
				).then(
						getTxnRecord("txn").logged()
				);
	}

	HapiApiSpec txnGetSmallTransferRecord() {
		return customHapiSpec("TxnGetSmalTransferRecord")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("hairTriggerPayer")
								.sendThreshold(1L)
				).when(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
								.payingWith("hairTriggerPayer")
								.via("txn")
				).then(
						getTxnRecord("txn").logged()
				);
	}

	HapiApiSpec txnGetLargeTransferRecord() {
		return customHapiSpec("TxnGetLargeTransferRecord")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("hairTriggerPayer").sendThreshold(1L),
						cryptoCreate("a"),
						cryptoCreate("b"),
						cryptoCreate("c"),
						cryptoCreate("d")
				).when(
						cryptoTransfer(spec -> TransferList.newBuilder()
											.addAccountAmounts(aa(spec, GENESIS, -4L))
											.addAccountAmounts(aa(spec, "a", 1L))
											.addAccountAmounts(aa(spec, "b", 1L))
											.addAccountAmounts(aa(spec, "c", 1L))
											.addAccountAmounts(aa(spec, "d", 1L)).build())
								.payingWith("hairTriggerPayer")
								.via("txn")
				).then(
						getTxnRecord("txn").logged()
				);
	}

	private AccountAmount aa(HapiApiSpec spec, String id, long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(spec.registry().getAccountID(id))
				.build();
	}

	HapiApiSpec[] cryptoGetAccountRecordsPaths() {
		return new HapiApiSpec[] {
				cryptoGetRecordsHappyPathS(),
				cryptoGetRecordsHappyPathM(),
				cryptoGetRecordsHappyPathL(),
		};
	}

	HapiApiSpec cryptoGetRecordsHappyPathS() {
		return customHapiSpec("CryptoGetRecordsHappyPathS")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("hairTriggerPayer")
								.sendThreshold(1L)
				).when(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
								.payingWith("hairTriggerPayer")
				).then(
						getAccountRecords("hairTriggerPayer").has(inOrder(recordWith()))
				);
	}

	HapiApiSpec cryptoGetRecordsHappyPathM() {
		return customHapiSpec("CryptoGetRecordsHappyPathM")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("hairTriggerPayer").sendThreshold(1L)
				).when(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer")
				).then(
						getAccountRecords("hairTriggerPayer").has(inOrder(recordWith(), recordWith()))
				);
	}

	HapiApiSpec cryptoGetRecordsHappyPathL() {
		return customHapiSpec("CryptoGetRecordsHappyPathL")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("hairTriggerPayer").sendThreshold(1L)
				).when(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).payingWith("hairTriggerPayer")
				).then(
						getAccountRecords("hairTriggerPayer").has(inOrder(
								recordWith(), recordWith(), recordWith(), recordWith(), recordWith()
						))
				);
	}

	HapiApiSpec[] cryptoGetAccountInfoPaths() {
		return new HapiApiSpec[] {
				cryptoGetAccountInfoHappyPath()
		};
	}

	HapiApiSpec cryptoGetAccountInfoHappyPath() {
		KeyShape smallKey = threshOf(1, 3);
		KeyShape midsizeKey = listOf(SIMPLE, listOf(2), threshOf(1, 2));
		KeyShape hugeKey = threshOf(4, SIMPLE, SIMPLE, listOf(4), listOf(3), listOf(2));

		return customHapiSpec("CryptoGetAccountInfoHappyPath")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						newKeyNamed("smallKey").shape(smallKey),
						newKeyNamed("midsizeKey").shape(midsizeKey),
						newKeyNamed("hugeKey").shape(hugeKey)
				).when(
						cryptoCreate("small").key("smallKey"),
						cryptoCreate("midsize").key("midsizeKey"),
						cryptoCreate("huge").key("hugeKey")
				).then(
						getAccountInfo("small"),
						getAccountInfo("midsize"),
						getAccountInfo("huge")
				);
	}

	HapiApiSpec[] cryptoCreatePaths() {
		return new HapiApiSpec[] {
			cryptoCreateSimpleKey(),
		};
	}

	HapiApiSpec cryptoCreateSimpleKey() {
		KeyShape shape = SIMPLE;

		return customHapiSpec("SuccessfulCryptoCreate")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						newKeyNamed("key").shape(shape)
				).when().then(
						cryptoCreate("a").key("key")
				);
	}

	HapiApiSpec[] cryptoTransferPaths() {
		return new HapiApiSpec[] {
				cryptoTransferGenesisToFunding(),
		};
	}

	HapiApiSpec cryptoTransferGenesisToFunding() {
		return customHapiSpec("CryptoTransferGenesisToFunding")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given().when().then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private final String PATH_TO_PAYABLE_CONTRACT_BYTECODE = "src/main/resource/PayReceivable.bin";
	private final String DEPOSIT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
}
