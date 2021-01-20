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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreateNonsense;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class ScheduleSignSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ScheduleSignSpecs.class);

	public static void main(String... args) {
		new ScheduleSignSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						triggersUponAdditionalNeededSig(),
						requiresSharedKeyToSignBothSchedulingAndScheduledTxns(),
				}
		);
	}

	public HapiApiSpec triggersUponAdditionalNeededSig() {
		/*
>>> START ScheduleCreate >>>
 - Created new schedule...
 - Resolved scheduleId: 0.0.1003
 - The resolved schedule has now witnessed 1 (additional) valid keys sign.
 - MerkleSchedule{deleted=false,
 transactionBody
 =120218031880c2d72f220208783220c383c2aec382c2b7c383c2b9744638c382c2ae4ac383c28bc383c290c383c28e72140a120a070a0318ea0710020a070a0318e9071001, payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611117800, nanos=408267001}, signers=[], signatories=[e8c038ebd8bef9dc63c56d363a31b837c3120bd924db81a3b044e5f87bd9179b], adminKey=<N/A>}
 - Not ready for execution yet.
<<< END ScheduleCreate END <<<
...
2021-01-19 22:43:21.312 INFO  85   ScheduleSignTransitionLogic -
>>> START ScheduleSign >>>
 - The resolved schedule has now witnessed 1 (additional) valid keys sign.
 - MerkleSchedule{deleted=false,
 transactionBody
 =120218031880c2d72f220208783220c383c2aec382c2b7c383c2b9744638c382c2ae4ac383c28bc383c290c383c28e72140a120a070a0318ea0710020a070a0318e9071001, payer=0.0.2, schedulingAccount=EntityId{shard=0, realm=0, num=2}, schedulingTXValidStart=RichInstant{seconds=1611117800, nanos=408267001}, signers=[], signatories=[e8c038ebd8bef9dc63c56d363a31b837c3120bd924db81a3b044e5f87bd9179b, d3778080334e3a32b895b5a56ddcd5531fa68f8b4f70760283a4bc48c95e2d15], adminKey=<N/A>}
 - Ready for execution!
<<< END ScheduleSign END <<<
		 */
		return defaultHapiSpec("TriggersUponAdditionalNeededSig")
				.given(
						cryptoCreate("sender"),
						cryptoCreate("receiver").receiverSigRequired(true)
				).when().then(
						scheduleCreate(
								"twoSigXfer",
								cryptoTransfer(
										tinyBarsFromTo("sender", "receiver", 1)
								).fee(ONE_HBAR).signedBy("sender")
						).logged(),
						scheduleSign("twoSigXfer").withSignatories("receiver")
				);
	}

	public HapiApiSpec requiresSharedKeyToSignBothSchedulingAndScheduledTxns() {
		/*
2021-01-19 23:33:25.101 INFO  111  ScheduleCreateTransitionLogic -
>>> START ScheduleCreate >>>
 - Created new schedule...
 - Resolved scheduleId: 0.0.1002
 - The resolved schedule has now witnessed 0 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.1001, schedulingAccount=EntityId{shard=0, realm=0, num=1001}, schedulingTXValidStart=RichInstant{seconds=1611120804, nanos=728019000}, signers=[], signatories=[], adminKey=<N/A>}
 - Not ready for execution yet.
<<< END ScheduleCreate END <<<
...
2021-01-19 23:33:25.713 INFO  85   ScheduleSignTransitionLogic -
>>> START ScheduleSign >>>
 - The resolved schedule has now witnessed 1 (additional) valid keys sign.
 - MerkleSchedule{deleted=false, transactionBody=..., payer=0.0.1001, schedulingAccount=EntityId{shard=0, realm=0, num=1001}, schedulingTXValidStart=RichInstant{seconds=1611120804, nanos=728019000}, signers=[], signatories=[0b22b6de88511664e2e9db5ba6ad2022aa9edf55caee2878f2e96fc12a910402], adminKey=<N/A>}
 - Ready for execution!
<<< END ScheduleSign END <<<
		 */
		return defaultHapiSpec("RequiresSharedKeyToSignBothSchedulingAndScheduledTxns")
				.given(
						newKeyNamed("sharedKey"),
						cryptoCreate("payerWithSharedKey").key("sharedKey")
				).when().then(
						scheduleCreate(
								"deferredCreation",
								cryptoCreate("yetToBe")
										.signedBy()
										.receiverSigRequired(true)
										.key("sharedKey")
										.fee(ONE_HBAR)
						).payingWith("payerWithSharedKey").logged(),
						scheduleSign("deferredCreation")
								.withSignatories("sharedKey")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
