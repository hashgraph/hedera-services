package com.hedera.services.bdd.suites.schedule;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.schedule.ScheduleExecutionSpecs.transferListCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ScheduleLongTermSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleLongTermSpecs.class);

	private static final String defaultLongTermEnabled =
			HapiSpecSetup.getDefaultNodeProps().get("scheduling.longTermEnabled");


	public static void main(String... args) {
		new ScheduleLongTermSpecs().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
			enableLongTermScheduledTransactions(),
			waitForExpiryWorks(),
			setLongTermScheduledTransactionsToDefault()
		);
	}

	private HapiApiSpec waitForExpiryWorks() {
		return defaultHapiSpec("WaitForExpiryWorks")
				.given(
						cryptoCreate("payingAccount"),
						cryptoCreate("receiver"),
						cryptoCreate("sender").via("senderTxn")
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								)
						)
								.designatingPayer("payingAccount")
								.waitForExpiry()
								.withRelativeExpiry("senderTxn", 8)
								.recordingScheduledTxn()
								.via("createTx"),
						scheduleSign("basicXfer")
								.alsoSigningWith("sender", "payingAccount")
								.via("signTx")
								.hasKnownStatus(SUCCESS)
				).then(
						getScheduleInfo("basicXfer")
								.hasScheduleId("basicXfer")
								.hasWaitForExpiry()
								.isNotExecuted()
								.isNotDeleted()
								.hasRelativeExpiry("senderTxn", 8)
								.hasRecordedScheduledTxn(),
						sleepFor(9000),
						cryptoCreate("foo").via("triggeringTxn"),
						getScheduleInfo("basicXfer")
								.hasCostAnswerPrecheck(INVALID_SCHEDULE_ID),
						withOpContext((spec, opLog) -> {
							var createTx = getTxnRecord("createTx");
							var signTx = getTxnRecord("signTx");
							var triggeringTx = getTxnRecord("triggeringTxn");
							var triggeredTx = getTxnRecord("createTx").scheduled();
							allRunFor(spec, createTx, signTx, triggeredTx, triggeringTx);

							Assertions.assertEquals(SUCCESS,
									triggeredTx.getResponseRecord().getReceipt().getStatus(),
									"Scheduled transaction be successful!");

							Instant triggerTime = Instant.ofEpochSecond(
									triggeringTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeringTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Instant triggeredTime = Instant.ofEpochSecond(
									triggeredTx.getResponseRecord().getConsensusTimestamp().getSeconds(),
									triggeredTx.getResponseRecord().getConsensusTimestamp().getNanos());

							Assertions.assertTrue(
									triggerTime.isBefore(triggeredTime),
									"Wrong consensus timestamp!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									triggeredTx.getResponseRecord().getTransactionID().getTransactionValidStart(),
									"Wrong transaction valid start!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getTransactionID().getAccountID(),
									triggeredTx.getResponseRecord().getTransactionID().getAccountID(),
									"Wrong record account ID!");

							Assertions.assertTrue(
									triggeredTx.getResponseRecord().getTransactionID().getScheduled(),
									"Transaction not scheduled!");

							Assertions.assertEquals(
									createTx.getResponseRecord().getReceipt().getScheduleID(),
									triggeredTx.getResponseRecord().getScheduleRef(),
									"Wrong schedule ID!");

							Assertions.assertTrue(
									transferListCheck(triggeredTx, asId("sender", spec), asId("receiver", spec),
											asId("payingAccount", spec), 1L),
									"Wrong transfer list!");
						})
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private static HapiApiSpec enableLongTermScheduledTransactions() {
		return defaultHapiSpec("EnableLongTermScheduledTransactions")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.longTermEnabled", "true"
								))
				);
	}

	private static HapiApiSpec disableLongTermScheduledTransactions() {
		return defaultHapiSpec("DisableLongTermScheduledTransactions")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.longTermEnabled", "false"
								))
				);
	}

	private static HapiApiSpec setLongTermScheduledTransactionsToDefault() {
		return defaultHapiSpec("SetLongTermScheduledTransactionsToDefault")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"scheduling.longTermEnabled", defaultLongTermEnabled
								))
				);
	}

	public static List<HapiApiSpec> withAndWithoutLongTermEnabled(Supplier<List<HapiApiSpec>> getSpecs) {
		List<HapiApiSpec> list = new ArrayList<>();
		list.add(disableLongTermScheduledTransactions());
		list.addAll(getSpecs.get());
		list.add(enableLongTermScheduledTransactions());
		var withEnabled = getSpecs.get();
		withEnabled.forEach(s -> s.appendToName("WithLongTermEnabled"));
		list.addAll(withEnabled);
		list.add(setLongTermScheduledTransactionsToDefault());
		return list;
	}
}
