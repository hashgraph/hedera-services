package com.hedera.services.bdd.suites.schedule;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateNonsense;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNPARSEABLE_SCHEDULED_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

public class ScheduleCreateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleCreateSpecs.class);

	public static void main(String... args) {
		new ScheduleCreateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						rejectsUnparseableTxn(),
						rejectsUnresolvableReqSigners(),
						triggersImmediatelyWithBothReqSimpleSigs(),
						onlySchedulesWithMissingReqSimpleSigs(),
						preservesRevocationServiceSemanticsForFileDelete(),
						detectsKeysChangedBetweenExpandSigsAndHandleTxn(),
				}
		);
	}

	private HapiApiSpec preservesRevocationServiceSemanticsForFileDelete() {
		KeyShape waclShape = listOf(SIMPLE, threshOf(2, 3));
		SigControl adequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, ON, OFF)));
		SigControl inadequateSigs = waclShape.signedWith(sigs(OFF, sigs(ON, OFF, OFF)));
		SigControl compensatorySigs = waclShape.signedWith(sigs(OFF, sigs(OFF, OFF, ON)));

		String shouldBeInstaDeleted = "tbd";
		String shouldBeDeletedEventually = "tbdl";

		return defaultHapiSpec("PreservesRevocationServiceSemanticsForFileDelete")
				.given(
						fileCreate(shouldBeInstaDeleted).waclShape(waclShape),
						fileCreate(shouldBeDeletedEventually).waclShape(waclShape)
				).when(
						scheduleCreate(
								"validRevocation",
								fileDelete(shouldBeInstaDeleted)
										.signedBy(shouldBeInstaDeleted)
										.sigControl(forKey(shouldBeInstaDeleted, adequateSigs))
						),
						getFileInfo(shouldBeInstaDeleted).hasDeleted(true)
				).then(
						scheduleCreate(
								"notYetValidRevocation",
								fileDelete(shouldBeDeletedEventually)
										.signedBy(shouldBeDeletedEventually)
										.sigControl(forKey(shouldBeDeletedEventually, inadequateSigs))
						),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(false),
						scheduleCreate(
								"nowValidRevocation",
								fileDelete(shouldBeDeletedEventually)
										.signedBy(shouldBeDeletedEventually)
										.sigControl(forKey(shouldBeDeletedEventually, compensatorySigs))
						),
						getFileInfo(shouldBeDeletedEventually).hasDeleted(true)
				);
	}

	public HapiApiSpec detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
		KeyShape firstShape = listOf(3);
		KeyShape secondShape = threshOf(2, 4);

		return defaultHapiSpec("DetectsKeysChangedBetweenExpandSigsAndHandleTxn")
				.given(
						newKeyNamed("a").shape(firstShape),
						newKeyNamed("b").shape(secondShape)
				).when(
						cryptoCreate("sender"),
						cryptoCreate("receiver")
								.key("a")
								.receiverSigRequired(true)
				).then(
						cryptoUpdate("receiver")
								.key("b")
								.deferStatusResolution(),
						scheduleCreate(
								"outdatedXferSigs",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR).signedBy("sender", "a")
						).hasKnownStatus(SOME_SIGNATURES_WERE_INVALID)
				);
	}

	public HapiApiSpec onlySchedulesWithMissingReqSimpleSigs() {
		return defaultHapiSpec("onlySchedulesWithMissingReqSimpleSigs")
				.given(
						cryptoCreate("sender").balance(1L),
						cryptoCreate("receiver").balance(0L).receiverSigRequired(true)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).signedBy("sender")
						)
				).then(
						getAccountBalance("sender").hasTinyBars(1L)
				);
	}

	public HapiApiSpec triggersImmediatelyWithBothReqSimpleSigs() {
		long initialBalance = HapiSpecSetup.getDefaultInstance().defaultBalance();
		long transferAmount = 1;

		return defaultHapiSpec("TriggersImmediatelyWithBothReqSimpleSigs")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", transferAmount)
								).signedBy("sender", "receiver")
						)
				).then(
						getAccountBalance("sender").hasTinyBars(initialBalance - transferAmount),
						getAccountBalance("receiver").hasTinyBars(initialBalance + transferAmount)
				);
	}

	public HapiApiSpec rejectsUnresolvableReqSigners() {
		return defaultHapiSpec("RejectsUnresolvableReqSigners")
				.given().when().then(
						scheduleCreate(
								"xferWithImaginaryAccount",
								cryptoTransfer(
										tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1),
										tinyBarsFromTo("1.2.3", FUNDING, 1)
								).signedBy(DEFAULT_PAYER)
						).hasKnownStatus(UNRESOLVABLE_REQUIRED_SIGNERS)
				);
	}

	public HapiApiSpec rejectsUnparseableTxn() {
		return defaultHapiSpec("RejectsUnparseableTxn")
				.given().when().then(
						scheduleCreateNonsense("absurd")
								.hasKnownStatus(UNPARSEABLE_SCHEDULED_TRANSACTION)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
