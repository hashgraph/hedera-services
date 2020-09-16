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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;

import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;

public class TopicCreateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TopicCreateSuite.class);

	public static void main(String... args) {
		new TopicCreateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				signingRequirementsEnforced(),
				autoRenewPeriodIsValidated(),
				autoRenewAccountIdNeedsAdminKeyToo(),
				submitKeyIsValidated(),
				adminKeyIsValidated(),
				autoRenewAccountIsValidated(),
				memoTooLong(),
				noAutoRenewPeriod(),
				allFieldsSetHappyCase(),
				feeAsExpected()
		);
	}

	private HapiApiSpec adminKeyIsValidated() {
		return defaultHapiSpec("AdminKeyIsValidated")
				.given()
				.when()
				.then(
						createTopic("testTopic")
								.adminKeyName(NONSENSE_KEY)
								.signedBy(GENESIS)
								.hasPrecheck(BAD_ENCODING)
				);
	}

	private HapiApiSpec submitKeyIsValidated() {
		return defaultHapiSpec("SubmitKeyIsValidated")
				.given()
				.when()
				.then(
						createTopic("testTopic")
								.submitKeyName(NONSENSE_KEY)
								.signedBy(GENESIS)
								.hasKnownStatus(BAD_ENCODING)
				);
	}

	private HapiApiSpec autoRenewAccountIsValidated() {
		return defaultHapiSpec("AutoRenewAccountIsValidated")
				.given()
				.when()
				.then(
						createTopic("testTopic")
								.autoRenewAccountId("1.2.3")
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT)
				);
	}

	private HapiApiSpec autoRenewAccountIdNeedsAdminKeyToo() {
		return defaultHapiSpec("autoRenewAccountIdNeedsAdminKeyToo")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("autoRenewAccount")
				)
				.when()
				.then(
						createTopic("noAdminKeyExplicitAutoRenewAccount")
								.payingWith("payer")
								.autoRenewAccountId("autoRenewAccount")
								.signedBy("payer", "autoRenewAccount")
								/* If autoRenewAccount is specified, adminKey should be present */
								.hasKnownStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED)
				);
	}

	private HapiApiSpec autoRenewPeriodIsValidated() {
		return defaultHapiSpec("autoRenewPeriodIsValidated")
				.given()
				.when()
				.then(
						createTopic("testTopic")
								.autoRenewPeriod(0L)
								.hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE),
						createTopic("testTopic")
								.autoRenewPeriod(Long.MAX_VALUE)
								.hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE)
				);
	}

	private HapiApiSpec memoTooLong() {
		byte[] longBytes = new byte[1000];
		Arrays.fill(longBytes, (byte) 33);
		String longMemo = new String(longBytes, StandardCharsets.UTF_8);
		return defaultHapiSpec("memoTooLong")
				.given()
				.when()
				.then(
						createTopic("testTopic")
								.topicMemo(longMemo)
								.hasKnownStatus(MEMO_TOO_LONG)
				);
	}

	private HapiApiSpec noAutoRenewPeriod() {
		return defaultHapiSpec("noAutoRenewPeriod")
				.given()
				.when()
				.then(
						createTopic("testTopic")
								.clearAutoRenewPeriod()
								.hasKnownStatus(INVALID_RENEWAL_PERIOD)
				);
	}


	private HapiApiSpec signingRequirementsEnforced() {
		long PAYER_BALANCE = 1_999_999_999_999L;

		return defaultHapiSpec("SigningRequirementsEnforced")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("submitKey"),
						newKeyNamed("wrongKey"),
						cryptoCreate("payer").balance(PAYER_BALANCE),
						cryptoCreate("autoRenewAccount"),
						contractCreate("nonCryptoAccount")
				)
				.when(
						createTopic("testTopic")
								.payingWith("payer")
								.signedBy("wrongKey")
								.hasPrecheck(INVALID_SIGNATURE),
						createTopic("nonExistentAutoRenewAccount")
								.autoRenewAccountId("nonCryptoAccount")
								.hasKnownStatus(INVALID_AUTORENEW_ACCOUNT),
						createTopic("testTopic")
								.payingWith("payer")
								.autoRenewAccountId("autoRenewAccount")
								/* SigMap missing signature from auto-renew account's key. */
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						createTopic("testTopic")
								.payingWith("payer")
								.adminKeyName("adminKey")
								/* SigMap missing signature from adminKey. */
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						createTopic("testTopic")
								.payingWith("payer")
								.adminKeyName("adminKey")
								.autoRenewAccountId("autoRenewAccount")
								/* SigMap missing signature from auto-renew account's key. */
								.signedBy("payer", "adminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						createTopic("testTopic")
								.payingWith("payer")
								.adminKeyName("adminKey")
								.autoRenewAccountId("autoRenewAccount")
								/* SigMap missing signature from adminKey. */
								.signedBy("payer", "autoRenewAccount")
								.hasKnownStatus(INVALID_SIGNATURE)
				)
				.then(
						createTopic("noAdminKeyNoAutoRenewAccount"),
						getTopicInfo("noAdminKeyNoAutoRenewAccount")
								.hasNoAdminKey().logged(),

						createTopic("explicitAdminKeyNoAutoRenewAccount")
								.adminKeyName("adminKey"),
						getTopicInfo("explicitAdminKeyNoAutoRenewAccount")
								.hasAdminKey("adminKey").logged(),

						createTopic("explicitAdminKeyExplicitAutoRenewAccount")
								.adminKeyName("adminKey").autoRenewAccountId("autoRenewAccount"),
						getTopicInfo("explicitAdminKeyExplicitAutoRenewAccount")
								.hasAdminKey("adminKey").hasAutoRenewAccount("autoRenewAccount").logged()
				);
	}

	private HapiApiSpec allFieldsSetHappyCase() {
		return defaultHapiSpec("AllFieldsSetHappyCase")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("submitKey"),
						cryptoCreate("autoRenewAccount")
				)
				.when()
				.then(
						createTopic("testTopic")
								.topicMemo("testmemo")
								.adminKeyName("adminKey")
								.submitKeyName("submitKey")
								.autoRenewAccountId("autoRenewAccount")
				);
	}

	private HapiApiSpec feeAsExpected() {
		return defaultHapiSpec("feeAsExpected")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("submitKey"),
						cryptoCreate("autoRenewAccount"),
						cryptoCreate("payer")
				)
				.when(
						createTopic("testTopic")
								.topicMemo("testmemo")
								.adminKeyName("adminKey")
								.submitKeyName("submitKey")
								.autoRenewAccountId("autoRenewAccount")
								.payingWith("payer")
								.via("topicCreate")
				)
				.then(
						validateFee("topicCreate", 0.0226)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
