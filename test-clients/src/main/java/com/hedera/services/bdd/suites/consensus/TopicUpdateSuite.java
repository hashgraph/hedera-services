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
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateFee;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TopicUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TopicUpdateSuite.class);

	public static void main(String... args) {
		new TopicUpdateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						validateMultipleFields(),
						topicUpdateSigReqsEnforcedAtConsensus(),
						updateSubmitKeyToDiffKey(),
						updateAdminKeyToDiffKey(),
						updateAdminKeyToEmpty(),
						updateMultipleFields(),
						expirationTimestampIsValidated(),
						updateExpiryOnTopicWithNoAdminKey(),
						updateSubmitKeyOnTopicWithNoAdminKeyFails(),
						feeAsExpected(),
						clearingAdminKeyWhenAutoRenewAccountPresent(),
				}
		);
	}

	private HapiApiSpec validateMultipleFields() {
		byte[] longBytes = new byte[1000];
		Arrays.fill(longBytes, (byte) 33);
		String longMemo = new String(longBytes, StandardCharsets.UTF_8);
		return defaultHapiSpec("validateMultipleFields")
				.given(
						newKeyNamed("adminKey"),
						createTopic("testTopic")
								.adminKeyName("adminKey")
				)
				.when()
				.then(
						updateTopic("testTopic")
								.adminKey(NONSENSE_KEY)
								.hasPrecheck(BAD_ENCODING),
						updateTopic("testTopic")
								.submitKey(NONSENSE_KEY)
								.hasKnownStatus(BAD_ENCODING),
						updateTopic("testTopic")
								.topicMemo(longMemo)
								.hasKnownStatus(MEMO_TOO_LONG),
						updateTopic("testTopic")
								.autoRenewPeriod(0)
								.hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE),
						updateTopic("testTopic")
								.autoRenewPeriod(Long.MAX_VALUE)
								.hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE)
				);
	}

	private HapiApiSpec topicUpdateSigReqsEnforcedAtConsensus() {
		long PAYER_BALANCE = 9_999_999_999_999_999L;
		Function<String[], HapiTopicUpdate> updateTopicSignedBy = (signers) -> {
			return updateTopic("testTopic")
					.payingWith("payer")
					.adminKey("newAdminKey")
					.autoRenewAccountId("newAutoRenewAccount")
					.signedBy(signers);
		};
		return defaultHapiSpec("topicUpdateSigReqsEnforcedAtConsensus")
				.given(
						newKeyNamed("oldAdminKey"), cryptoCreate("oldAutoRenewAccount"),
						newKeyNamed("newAdminKey"), cryptoCreate("newAutoRenewAccount"),
						cryptoCreate("payer").balance(PAYER_BALANCE),
						createTopic("testTopic")
								.adminKeyName("oldAdminKey")
								.autoRenewAccountId("oldAutoRenewAccount")
				).when(
						updateTopicSignedBy.apply(new String[] { "payer", "oldAdminKey" })
								.hasKnownStatus(INVALID_SIGNATURE),
						updateTopicSignedBy.apply(new String[] { "payer", "oldAdminKey", "newAdminKey" })
								.hasKnownStatus(INVALID_SIGNATURE),
						updateTopicSignedBy.apply(new String[] { "payer", "oldAdminKey", "newAutoRenewAccount" })
								.hasKnownStatus(INVALID_SIGNATURE),
						updateTopicSignedBy.apply(new String[] { "payer", "newAdminKey", "newAutoRenewAccount" })
								.hasKnownStatus(INVALID_SIGNATURE),
						updateTopicSignedBy.apply(
								new String[] { "payer", "oldAdminKey", "newAdminKey", "newAutoRenewAccount" })
								.hasKnownStatus(SUCCESS)
				).then(
						getTopicInfo("testTopic")
								.logged()
								.hasAdminKey("newAdminKey")
								.hasAutoRenewAccount("newAutoRenewAccount")
				);
	}

	private HapiApiSpec updateSubmitKeyToDiffKey() {
		return defaultHapiSpec("updateSubmitKeyToDiffKey")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("submitKey"),

						createTopic("testTopic")
								.adminKeyName("adminKey")
				)
				.when(
						updateTopic("testTopic")
								.submitKey("submitKey")
				)
				.then(
						getTopicInfo("testTopic")
								.hasSubmitKey("submitKey")
								.hasAdminKey("adminKey")
								.logged()
				);
	}

	private HapiApiSpec updateAdminKeyToDiffKey() {
		return defaultHapiSpec("updateAdminKeyToDiffKey")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("updateAdminKey"),

						createTopic("testTopic")
								.adminKeyName("adminKey")
				)
				.when(
						updateTopic("testTopic")
								.adminKey("updateAdminKey")
				)
				.then(
						getTopicInfo("testTopic")
								.hasAdminKey("updateAdminKey")
								.logged()
				);
	}

	private HapiApiSpec updateAdminKeyToEmpty() {
		return defaultHapiSpec("updateAdminKeyToEmpty")
				.given(
						newKeyNamed("adminKey"),
						createTopic("testTopic")
								.adminKeyName("adminKey")
				)
				/* if adminKey is empty list should clear adminKey */
				.when(
						updateTopic("testTopic")
								.adminKey(EMPTY_KEY)
				)
				.then(
						getTopicInfo("testTopic")
								.hasNoAdminKey()
								.logged()
				);
	}

	private HapiApiSpec updateMultipleFields() {
		long updateAutoRenewPeriod = 1200L;
		long expirationTimestamp = Instant.now().getEpochSecond() + 10000000; // more than default.autorenew
		// .secs=7000000
		return defaultHapiSpec("updateMultipleFields")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("adminKey2"),
						newKeyNamed("submitKey"),

						cryptoCreate("autoRenewAccount"),
						cryptoCreate("nextAutoRenewAccount"),

						createTopic("testTopic")
								.topicMemo("initialmemo")
								.adminKeyName("adminKey")
								.autoRenewPeriod(updateAutoRenewPeriod)
								.autoRenewAccountId("autoRenewAccount")
				)
				.when(
						updateTopic("testTopic")
								.topicMemo("updatedmemo")
								.submitKey("submitKey")
								.adminKey("adminKey2")
								.expiry(expirationTimestamp)
								.autoRenewPeriod(updateAutoRenewPeriod * 2)
								.autoRenewAccountId("nextAutoRenewAccount")
								.hasKnownStatus(SUCCESS)
				)
				.then(
						getTopicInfo("testTopic")
								.hasMemo("updatedmemo")
								.hasSubmitKey("submitKey")
								.hasAdminKey("adminKey2")
								.hasExpiry(expirationTimestamp)
								.hasAutoRenewPeriod(updateAutoRenewPeriod * 2)
								.hasAutoRenewAccount("nextAutoRenewAccount")
								.logged()
				);
	}

	private HapiApiSpec expirationTimestampIsValidated() {
		long now = Instant.now().getEpochSecond();
		return defaultHapiSpec("expirationTimestampIsValidated")
				.given(
						createTopic("testTopic")
								.autoRenewPeriod(3600)
				)
				.when()
				.then(
						updateTopic("testTopic")
								.expiry(now - 1) // less than consensus time
								.hasKnownStatus(INVALID_EXPIRATION_TIME),
						updateTopic("testTopic")
								.expiry(now + 1000)  // 1000 < autoRenewPeriod
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED)
				);
	}

	/* If admin key is not set, only expiration timestamp updates are allowed */
	private HapiApiSpec updateExpiryOnTopicWithNoAdminKey() {
		// some time in future, otherwise update operation will fail
		long expirationTimestamp = Instant.now().getEpochSecond() + 10000000; // more than default.autorenew
		// .secs=7000000
		return defaultHapiSpec("updateExpiryOnTopicWithNoAdminKey")
				.given(
						createTopic("testTopic")
				)
				.when(
						updateTopic("testTopic")
								.expiry(expirationTimestamp)
				)
				.then(
						getTopicInfo("testTopic")
								.hasExpiry(expirationTimestamp)
				);
	}

	private HapiApiSpec clearingAdminKeyWhenAutoRenewAccountPresent() {
		return defaultHapiSpec("clearingAdminKeyWhenAutoRenewAccountPresent")
				.given(
						newKeyNamed("adminKey"),
						cryptoCreate("autoRenewAccount"),
						createTopic("testTopic")
								.adminKeyName("adminKey")
								.autoRenewAccountId("autoRenewAccount")
				)
				.when(
						updateTopic("testTopic")
								.adminKey(EMPTY_KEY)
								.hasKnownStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED),
						updateTopic("testTopic")
								.adminKey(EMPTY_KEY)
								.autoRenewAccountId("0.0.0")
				)
				.then(
						getTopicInfo("testTopic")
								.hasNoAdminKey()
				);
	}

	private HapiApiSpec updateSubmitKeyOnTopicWithNoAdminKeyFails() {
		return defaultHapiSpec("updateSubmitKeyOnTopicWithNoAdminKeyFails")
				.given(
						newKeyNamed("submitKey"),
						createTopic("testTopic")
				)
				.when(
						updateTopic("testTopic")
								.submitKey("submitKey")
								.hasKnownStatus(UNAUTHORIZED)
				)
				.then();
	}

	private HapiApiSpec feeAsExpected() {
		return defaultHapiSpec("feeAsExpected")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("submitKey"),
						cryptoCreate("autoRenewAccount"),
						createTopic("testTopic")
								.adminKeyName("adminKey"),
						cryptoCreate("payer")
				)
				.when(
						updateTopic("testTopic")
								.topicMemo("memo")
								.submitKey("submitKey")
								.autoRenewPeriod(10)
								.payingWith("payer")
								.autoRenewAccountId("autoRenewAccount")
								.via("updateTopic")
				)
				.then(
						validateFee("updateTopic", 0.0004)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
