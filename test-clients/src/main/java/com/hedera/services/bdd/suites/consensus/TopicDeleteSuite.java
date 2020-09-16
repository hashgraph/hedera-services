package com.hedera.services.bdd.suites.consensus;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateFee;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TopicDeleteSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TopicDeleteSuite.class);

	public static void main(String... args) {
		new TopicDeleteSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				cannotDeleteAccountAsTopic(),
				topicIdIsValidated(),
				noAdminKeyCannotDelete(),
				deleteWithAdminKey(),
				deleteFailedWithWrongKey(),
				feeAsExpected()
		);
	}

	private HapiApiSpec cannotDeleteAccountAsTopic() {
		return defaultHapiSpec("CannotDeleteAccountAsTopic")
				.given(
						cryptoCreate("nonTopicId")
				)
				.when()
				.then(
						deleteTopic(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
								.hasKnownStatus(INVALID_TOPIC_ID)
				);
	}

	private HapiApiSpec topicIdIsValidated() {
		return defaultHapiSpec("topicIdIsValidated")
				.given()
				.when()
				.then(
						deleteTopic((String) null)
								.hasKnownStatus(INVALID_TOPIC_ID),
						deleteTopic("100.232.4534")  // non-existent id
								.hasKnownStatus(INVALID_TOPIC_ID)
				);
    }

	private HapiApiSpec noAdminKeyCannotDelete() {
		return defaultHapiSpec("noAdminKeyCannotDelete")
				.given(
						createTopic("testTopic")
				)
				.when(
						deleteTopic("testTopic")
								.hasKnownStatus(UNAUTHORIZED)
				)
				.then(
				);
	}

	private HapiApiSpec deleteWithAdminKey() {
		return defaultHapiSpec("deleteWithAdminKey")
				.given(
						newKeyNamed("adminKey"),
						createTopic("testTopic")
								.adminKeyName("adminKey")
				)
				.when(
						deleteTopic("testTopic")
								.hasPrecheck(ResponseCodeEnum.OK)
				)
				.then(
						getTopicInfo("testTopic").hasCostAnswerPrecheck(INVALID_TOPIC_ID)
				);
	}

	private HapiApiSpec deleteFailedWithWrongKey() {
		long PAYER_BALANCE = 1_999_999_999_999L;
		return defaultHapiSpec("deleteFailedWithWrongKey")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("wrongKey"),
						cryptoCreate("payer").balance(PAYER_BALANCE),
						createTopic("testTopic")
								.adminKeyName("adminKey")
				)
				.when(
						deleteTopic("testTopic")
								.payingWith("payer")
								.signedBy("payer", "wrongKey")
								.hasKnownStatus(ResponseCodeEnum.INVALID_SIGNATURE)
				)
				.then(
				);
	}

	private HapiApiSpec feeAsExpected() {
		return defaultHapiSpec("feeAsExpected")
				.given(
						newKeyNamed("adminKey"),
						createTopic("testTopic")
								.adminKeyName("adminKey"),
						cryptoCreate("payer")
				)
				.when(
						deleteTopic("testTopic")
								.payingWith("payer")
								.via("topicDelete")
				)
				.then(
				        validateFee("topicDelete", 0.0081)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
