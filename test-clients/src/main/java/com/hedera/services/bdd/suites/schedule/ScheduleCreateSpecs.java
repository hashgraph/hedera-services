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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateNonsense;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
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
		/*
2021-01-18 20:46:34.104 INFO  147  ScheduleCreateTransitionLogic -
>>> START ScheduleCreate >>>
 - Created new schedule...
 - Resolved scheduleId: 0.0.1003
 - The resolved schedule has now witnessed 2 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611024393, nanos=580513000}, signers=[], signatories=[8479e6a35f11f9b4f9e5cd0062b2c8b4add6356af49f8b0d68d66f9f88469561, a17fe7d29389a3c37bb95ae15127502e7304fc6501e6ae19cd6345ad4a43d8a2], adminKey=<N/A>}
 - Ready for execution!
<<< END ScheduleCreate END <<<
...
>>> START ScheduleCreate >>>
 - Created new schedule...
 - Resolved scheduleId: 0.0.1004
 - The resolved schedule has now witnessed 1 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611024394, nanos=580295000}, signers=[], signatories=[807ddbdac4a1c5ac9da10a00830e4ca4d37a1d05d09d954a6d3b05ba921d5a39], adminKey=<N/A>}
 - Not ready for execution yet.
<<< END ScheduleCreate END <<<
...
>>> START ScheduleCreate >>>
 - Resolved scheduleId: 0.0.1004
 - The resolved schedule has now witnessed 1 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611024394, nanos=580295000}, signers=[], signatories=[807ddbdac4a1c5ac9da10a00830e4ca4d37a1d05d09d954a6d3b05ba921d5a39, e889abcf521f9bd4d96c928c55f205758249ef9b9848b3418207d59f5f89e896], adminKey=<N/A>}
 - Ready for execution!
<<< END ScheduleCreate END <<<
		*/
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
				).when().then(
						scheduleCreate(
								"validRevocation",
								fileDelete(shouldBeInstaDeleted)
										.sigControl(forKey(shouldBeInstaDeleted, adequateSigs))
						),
						scheduleCreate(
								"notYetValidRevocation",
								fileDelete(shouldBeDeletedEventually)
										.sigControl(forKey(shouldBeDeletedEventually, inadequateSigs))
						),
						scheduleCreate(
								"nowValidRevocation",
								fileDelete(shouldBeDeletedEventually)
										.sigControl(forKey(shouldBeDeletedEventually, compensatorySigs))
						)
				);
	}

	public HapiApiSpec detectsKeysChangedBetweenExpandSigsAndHandleTxn() {
		/*
2021-01-18 22:53:14.752 INFO  147  ScheduleCreateTransitionLogic -
>>> START ScheduleCreate >>>
 - Created new schedule...
 - Resolved scheduleId: 0.0.1003
 - The resolved schedule has now witnessed 1 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611031994, nanos=286014000}, signers=[], signatories=[5c86f7bc94a13e9f1b1d6502c3d9e8bc3a532311ce2157ebed0343d746da39f4], adminKey=<N/A>}
 - Not ready for execution yet.
<<< END ScheduleCreate END <<<
...
>>> START ScheduleCreate >>>
 - Resolved scheduleId: 0.0.1003
 - The resolved schedule has now witnessed 2 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611031994, nanos=286014000}, signers=[], signatories=[5c86f7bc94a13e9f1b1d6502c3d9e8bc3a532311ce2157ebed0343d746da39f4, c1e69c8765941bb68198dd6d8588abdfe6b906b02078727f1204d3d54f6df204, 5985f1afb3a3983a2f31bea45682c5958c3a6236f5bc2349f6b6088896165c07], adminKey=<N/A>}
 - Ready for execution!
<<< END ScheduleCreate END <<<
		 */
		KeyShape firstShape = listOf(3);
		KeyShape secondShape = threshOf(2, 4);
		SigControl justEnough = secondShape.signedWith(sigs(ON, OFF, ON, OFF));

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
						cryptoUpdate("receiver").key("b").deferStatusResolution(),
						scheduleCreate(
								"outdatedXferSigs",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR).signedBy("sender", "a")
						),
						scheduleCreate(
								"currentXferSigs",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR).signedBy("sender", "b")
										.sigControl(forKey("b", justEnough))
						)
				);
	}

	public HapiApiSpec onlySchedulesWithMissingReqSimpleSigs() {
		/*
>>> START ScheduleCreate >>>
 - Resolved scheduleId: 0.0.1003
 - Sigs not yet valid.
<<< END ScheduleCreate END <<<
		*/
		return defaultHapiSpec("onlySchedulesWithMissingReqSimpleSigs")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when().then(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).signedBy("sender")
						)
				);
	}

	public HapiApiSpec triggersImmediatelyWithBothReqSimpleSigs() {
		/*
>>> START ScheduleCreate >>>
 - Resolved scheduleId: 0.0.1006
 - Sigs are already valid!
<<< END ScheduleCreate END <<<
		 */
		return defaultHapiSpec("TriggersImmediatelyWithBothReqSimpleSigs")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when().then(
						scheduleCreate(
								"basicXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).signedBy("sender", "receiver")
						)
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
