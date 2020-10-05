package com.hedera.services.bdd.suites.misc;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;

import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;

import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.stream.Collectors.toList;

public class R5BugChecks extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(R5BugChecks.class);

	final String PATH_TO_FUSE_BYTECODE = "src/main/resource/testfiles/Fuse.bin";

	private static String CONSPICUOUS_DONATION_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"toNum\",\"type\":\"uint32\"},{\"internalType\":\"string\",\"name\":\"saying\"," +
			"\"type\":\"string\"}],\"name\":\"donate\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	private static String TRACKING_SEND_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\"," +
			"\"name\":\"toNum\",\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"amount\"," +
			"\"type\":\"uint32\"}],\"name\":\"uncheckedTransfer\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	private static String HOW_MUCH_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"howMuch\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static void main(String... args) {
		new R5BugChecks().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						genesisUpdatesFeesForFree(),
//						canGetDeletedFileInfo(),
//						enforcesSigRequirements(),
//						contractCannotTransferToReceiverSigRequired(),
//						cannotTransferEntirePayerBalance(),
//						costAnswerGetAccountInfoRejectsInvalidId(),
//						cannotUseThresholdWithM0(),

						/* --- MISC --- */
//						cannotTransferToDeleted(),
				}
		);
	}

	private HapiApiSpec cannotUseThresholdWithM0() {
		KeyShape invalid = listOf(SIMPLE, SIMPLE, threshOf(0, 3));

		return defaultHapiSpec("CannotUseThresholdWithM0")
				.given().when().then(
						cryptoCreate("sketchy")
								.keyShape(invalid)
								.hasPrecheck(BAD_ENCODING)
				);
	}

	private HapiApiSpec cannotTransferEntirePayerBalance() {
		var balance = 1_234_567L;
		return defaultHapiSpec("CannotTransferEntirePayerBalance")
				.given(
						cryptoCreate("sketchy")
								.balance(balance)
				).when().then(
						cryptoTransfer(tinyBarsFromTo("sketchy", FUNDING, balance))
								.payingWith("sketchy")
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	private HapiApiSpec canGetDeletedFileInfo() {
		return defaultHapiSpec("CanGetDeletedFileInfo")
				.given(
						fileCreate("tbd")
				).when(
						fileDelete("tbd")
				).then(
						getFileInfo("tbd")
								.hasCostAnswerPrecheck(OK)
								.hasAnswerOnlyPrecheck(OK)
				);
	}

	private HapiApiSpec costAnswerGetAccountInfoRejectsInvalidId() {
		return defaultHapiSpec("CostAnswerGetAccountInfoRejectsInvalidId")
				.given().when().then(
						getAccountInfo("1.2.3").hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec contractCannotTransferToReceiverSigRequired() {
		var bytecodeLoc = "src/main/resource/validation-scenarios/Multipurpose.bin";

		return defaultHapiSpec("ContractCannotTransferToReceiverSigRequired")
				.given(
						fileCreate("bytecode")
								.path(bytecodeLoc),
						contractCreate("sponsor")
								.bytecode("bytecode")
								.balance(1)
				).when(
						cryptoCreate("sr")
								.receiverSigRequired(true)
				).then(
						contractCall(
								"sponsor",
								CONSPICUOUS_DONATION_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("sr").getAccountNum(),
										"Hey, Ma!"
								}).hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec enforcesSigRequirements() {
		var bytecodeLoc = "src/main/resource/testfiles/LastTrackingSender.bin";
		KeyShape complexSrShape = listOf(SIMPLE, threshOf(1, 3));
		SigControl activeSig = complexSrShape.signedWith(sigs(ON, sigs(OFF, OFF, ON)));
		SigControl inactiveSig = complexSrShape.signedWith(sigs(OFF, sigs(ON, ON, ON)));

		return defaultHapiSpec("ExceptionReverts")
				.given(
						newKeyNamed("srKey").shape(complexSrShape),
						fileCreate("bytecode")
								.path(bytecodeLoc),
						contractCreate("sponsor")
								.bytecode("bytecode")
								.balance(10),
						cryptoCreate("noSr")
								.balance(0L),
						cryptoCreate("sr")
								.key("srKey")
								.balance(0L)
								.receiverSigRequired(true)
				).when(
						contractCall(
								"sponsor",
								TRACKING_SEND_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("sr").getAccountNum(),
										5
								}).hasKnownStatus(INVALID_SIGNATURE),
						contractCall(
								"sponsor",
								TRACKING_SEND_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("sr").getAccountNum(),
										5
								}).signedBy(GENESIS, "sr")
								.sigControl(ControlForKey.forKey("sr", inactiveSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						contractCallLocal("sponsor", HOW_MUCH_ABI).has(
								resultWith().resultThruAbi(HOW_MUCH_ABI,
										isLiteralResult(new Object[] { BigInteger.valueOf(0) }))),
						getAccountBalance("sr").hasTinyBars(0L)
				).then(
						contractCall(
								"sponsor",
								TRACKING_SEND_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("noSr").getAccountNum(),
										1
								}),
						contractCall(
								"sponsor",
								TRACKING_SEND_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("sr").getAccountNum(),
										5
								}).signedBy(GENESIS, "sr")
								.sigControl(ControlForKey.forKey("sr", activeSig)),
						contractCallLocal("sponsor", HOW_MUCH_ABI).has(
								resultWith().resultThruAbi(HOW_MUCH_ABI,
										isLiteralResult(new Object[] { BigInteger.valueOf(5) }))),
						getAccountBalance("sr").hasTinyBars(5L),
						getAccountBalance("noSr").hasTinyBars(1L)
				);
	}


	private HapiApiSpec cannotTransferToDeleted() {
		var bytecodeLoc = "src/main/resource/testfiles/LastTrackingSender.bin";

		return defaultHapiSpec("CannotTransferToDeleted")
				.given(
						cryptoCreate("tbd"),
						fileCreate("bytecode")
								.path(bytecodeLoc),
						contractCreate("sponsor")
								.bytecode("bytecode")
								.balance(10)
				).when(
						contractCall(
								"sponsor",
								TRACKING_SEND_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("tbd").getAccountNum(),
										1
								}),
						cryptoDelete("tbd")
				).then(
						contractCall(
								"sponsor",
								TRACKING_SEND_ABI,
								spec -> new Object[] {
										(int) spec.registry().getAccountID("tbd").getAccountNum(),
										2
								}).hasKnownStatus(INVALID_SOLIDITY_ADDRESS)
				);
	}

	private HapiApiSpec genesisUpdatesFeesForFree() {
		AtomicReference<ByteString> schedulePart1 = new AtomicReference<>();
		AtomicReference<ByteString> schedulePart2 = new AtomicReference<>();

		return defaultHapiSpec("GenesisUpdatesFeesForFree")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(FEE_SCHEDULE);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							var bytes = contents.toByteArray();
							var n = bytes.length;
							schedulePart1.set(ByteString.copyFrom(bytes, 0, 4096));
							schedulePart2.set(ByteString.copyFrom(bytes, 4096, n - 4096));
						}),
						cryptoCreate("payer")
				).when(
						balanceSnapshot("preUpdate", GENESIS),
						fileUpdate(FEE_SCHEDULE)
								.fee(5_000_000_000L)
								.contents(ignore -> schedulePart1.get())
								.hasKnownStatus(FEE_SCHEDULE_FILE_PART_UPLOADED),
						fileAppend(FEE_SCHEDULE)
								.fee(5_000_000_000L)
								.contentFrom(() -> schedulePart2.get().toByteArray())
				).then(
						getAccountBalance(GENESIS).hasTinyBars(changeFromSnapshot("preUpdate", 0))
				);
	}

	/* Run from clean local environment to test need for state migration vis-a-vis JContractFunctionResult. */
	private HapiApiSpec genRecordWithCreations() {
		return defaultHapiSpec("CreateRecordViaExpensiveSubmit")
				.given(
						fileCreate("bytecode").path(PATH_TO_FUSE_BYTECODE)
				).when(
						contractCreate("fuse").bytecode("bytecode")
				).then(
						freeze().startingIn(1).minutes().andLasting(1).minutes()
				);
	}

	public static String readableTransferList(TransferList accountAmounts) {
		return accountAmounts.getAccountAmountsList()
				.stream()
				.map(aa -> String.format(
						"%s %s %s%s",
						HapiPropertySource.asAccountString(aa.getAccountID()),
						aa.getAmount() < 0 ? "->" : "<-",
						aa.getAmount() < 0 ? "-" : "+",
						BigInteger.valueOf(aa.getAmount()).abs().toString()))
				.collect(toList())
				.toString();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
