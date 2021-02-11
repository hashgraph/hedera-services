package com.hedera.services.bdd.suites.file;

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
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;

public class FileCreateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FileCreateSuite.class);

	public static void main(String... args) {
		new FileCreateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				createWithMemoWorks()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
				createFailsWithMissingSigs(),
				createFailsWithPayerAccountNotFound()
		);
	}

	private HapiApiSpec createWithMemoWorks() {
		String memo = "Really quite something!";

		return defaultHapiSpec("createWithMemoWorks")
				.given(
						fileCreate("memorable").entityMemo(memo)
				).when().then(
						getFileInfo("memorable").hasMemo(memo)
				);
	}

	private HapiApiSpec createFailsWithMissingSigs() {
		KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
		SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
		SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

		return defaultHapiSpec("CreateFailsWithMissingSigs")
				.given().when().then(
						fileCreate("test")
								.waclShape(shape)
								.sigControl(forKey("test", invalidSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						fileCreate("test")
								.waclShape(shape)
								.sigControl(forKey("test", validSig))
				);
	}

	private static Transaction replaceTxnNodeAccount(Transaction txn) {
		AccountID badNodeAccount = AccountID.newBuilder().setAccountNum(2000).setRealmNum(0).setShardNum(0).build();
		return TxnUtils.replaceTxnNodeAccount(txn, badNodeAccount);
	}

	private HapiApiSpec createFailsWithPayerAccountNotFound() {
		KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
		SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

		return defaultHapiSpec("CreateFailsWithPayerAccountNotFound")
				.given( ).when( ).then(
						fileCreate("test")
								.withLegacyProtoStructure()
								.waclShape(shape)
								.sigControl(forKey("test", validSig))
								.scrambleTxnBody(FileCreateSuite::replaceTxnNodeAccount)
								.hasPrecheckFrom(INVALID_NODE_ACCOUNT)
				);
	}
	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
