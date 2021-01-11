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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.CostSnapshotMode;
import static com.hedera.services.bdd.spec.HapiApiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BALANCE_LOOKUP_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BALANCE_LOOKUP_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BELIEVE_IN_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CONSPICUOUS_DONATION_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.LUCKY_NO_LOOKUP_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPURPOSE_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static java.util.stream.Collectors.toList;

public class CostOfEverythingSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CostOfEverythingSuite.class);

	CostSnapshotMode costSnapshotMode = TAKE;
//	CostSnapshotMode costSnapshotMode = COMPARE;

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
//				transactionGetRecordPaths()
				miscContractCreatesAndCalls()
		).map(Stream::of).reduce(Stream.empty(), Stream::concat).collect(toList());
	}

	HapiApiSpec[] transactionGetRecordPaths() {
		return new HapiApiSpec[] {
				txnGetCreateRecord(),
				txnGetSmallTransferRecord(),
				txnGetLargeTransferRecord(),
		};
	}

	HapiApiSpec miscContractCreatesAndCalls() {
		Object[] donationArgs = new Object[] { 2, "Hey, Ma!" };

		return customHapiSpec("MiscContractCreatesAndCalls")
				.withProperties(Map.of("cost.snapshot.mode", costSnapshotMode.toString()))
				.given(
						cryptoCreate("civilian")
								.balance(A_HUNDRED_HBARS),
						fileCreate("multiBytecode")
								.payingWith("civilian")
								.path(MULTIPURPOSE_BYTECODE_PATH),
						fileCreate("lookupBytecode")
								.payingWith("civilian")
								.path(BALANCE_LOOKUP_BYTECODE_PATH)
				).when(
						contractCreate("multi")
								.payingWith("civilian")
								.bytecode("multiBytecode")
								.balance(652),
						contractCreate("lookup")
								.payingWith("civilian")
								.bytecode("lookupBytecode")
								.balance(256)
				).then(
						contractCall("multi", BELIEVE_IN_ABI, 256)
								.payingWith("civilian"),
						contractCallLocal("multi", LUCKY_NO_LOOKUP_ABI)
								.payingWith("civilian").logged()
								.has(resultWith().resultThruAbi(
										LUCKY_NO_LOOKUP_ABI,
										isLiteralResult(new Object[] { BigInteger.valueOf(256) }))),
						contractCall("multi", CONSPICUOUS_DONATION_ABI, donationArgs)
								.payingWith("civilian"),
						contractCallLocal(
								"lookup",
								BALANCE_LOOKUP_ABI,
								spec -> new Object[] {
										spec.registry().getAccountID("civilian").getAccountNum()
								}
						).payingWith("civilian").logged()
				);
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
}
