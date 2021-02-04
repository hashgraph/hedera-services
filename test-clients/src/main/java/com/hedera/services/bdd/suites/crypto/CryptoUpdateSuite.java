package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyLabel.complex;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;

import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;

public class CryptoUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoUpdateSuite.class);
	private static final int MAX_MEMO_LENGTH = 100;

	public static void main(String... args) {
		new CryptoUpdateSuite().runSuiteAsync();
	}

	private final SigControl TWO_LEVEL_THRESH = KeyShape.threshSigs(2,
			KeyShape.threshSigs(1, ANY, ANY, ANY, ANY, ANY, ANY, ANY),
			KeyShape.threshSigs(3, ANY, ANY, ANY, ANY, ANY, ANY, ANY));
	private final KeyLabel OVERLAPPING_KEYS = complex(
			complex("A", "B", "C", "D", "E", "F", "G"),
			complex("H", "I", "J", "K", "L", "M", "A"));

	private final SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
			KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
			KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
	private final SigControl NOT_ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
			KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
			KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
	private final SigControl ENOUGH_OVERLAPPING_SIGS = KeyShape.threshSigs(2,
			KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
			KeyShape.threshSigs(3, ON, ON, OFF, OFF, OFF, OFF, ON));

	private final String TARGET_KEY = "twoLevelThreshWithOverlap";
	private final String TARGET_ACCOUNT = "complexKeyAccount";

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				updateWithUniqueSigs(),
				updateWithOverlappingSigs(),
				updateWithOneEffectiveSig(),
				updateMemoWorks()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
//				updateFailsWithInsufficientSigs(),
//				updateFailsIfMissingSigs()
//				cannotSetThresholdNegative()
				updateWithEmptyKey(),
				updateWithTooLongKey()
		);
	}

	private HapiApiSpec updateWithTooLongKey() {
		var tooLongMemo = "ORIGINAL" + TxnUtils.randomUppercase(MAX_MEMO_LENGTH + 1);

		return defaultHapiSpec("UpdateWithTooLongMemo")
				.given(
						cryptoCreate("NormalAccount")
								.memo("defaultMemo")
				).when().then(
						cryptoUpdate("NormalAccount")
						.memo(tooLongMemo)
						.hasPrecheck(MEMO_TOO_LONG)
				);
	}

	private HapiApiSpec updateMemoWorks() {
		var oldMemo = "defaultMemo";
		var newMemo = "newerMemo";
		return defaultHapiSpec("UpdateMemoWorks")
				.given(
						cryptoCreate("NormalAccount")
								.memo(oldMemo)
				).when(
						getAccountInfo("NormalAccount")
						.has(AccountInfoAsserts.accountWith().memo(oldMemo)),
						cryptoUpdate("NormalAccount")
								.memo(newMemo)
				).then(
						getAccountInfo("NormalAccount")
								.has(AccountInfoAsserts.accountWith().memo(newMemo))
				);
	}

	private HapiApiSpec updateWithUniqueSigs() {
		return defaultHapiSpec("UpdateWithUniqueSigs")
				.given(
					newKeyNamed(TARGET_KEY).shape(TWO_LEVEL_THRESH).labels(OVERLAPPING_KEYS),
					cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY)
				).when().then(
					cryptoUpdate(TARGET_ACCOUNT)
							.sigControl(forKey(TARGET_KEY, ENOUGH_UNIQUE_SIGS))
							.receiverSigRequired(true)
				);
	}

	private HapiApiSpec updateWithOneEffectiveSig() {
		KeyLabel ONE_UNIQUE_KEY = complex(
				complex("X", "X", "X", "X", "X", "X", "X"),
				complex("X", "X", "X", "X", "X", "X", "X"));
		SigControl SINGLE_SIG = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
				KeyShape.threshSigs(3, OFF, OFF, OFF, ON, OFF, OFF, OFF));

		return defaultHapiSpec("UpdateWithOneEffectiveSig")
				.given(
						newKeyNamed("repeatingKey").shape(TWO_LEVEL_THRESH).labels(ONE_UNIQUE_KEY),
						cryptoCreate(TARGET_ACCOUNT).key("repeatingKey").balance(1_000_000_000L)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.sigControl(forKey("repeatingKey", SINGLE_SIG))
								.receiverSigRequired(true)
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec updateWithOverlappingSigs() {
		return defaultHapiSpec("UpdateWithOverlappingSigs")
				.given(
					newKeyNamed(TARGET_KEY).shape(TWO_LEVEL_THRESH).labels(OVERLAPPING_KEYS),
					cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.sigControl(forKey(TARGET_KEY, ENOUGH_OVERLAPPING_SIGS))
								.receiverSigRequired(true)
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec updateFailsWithInsufficientSigs() {
		return defaultHapiSpec("UpdateFailsWithInsufficientSigs")
				.given(
					newKeyNamed(TARGET_KEY).shape(TWO_LEVEL_THRESH).labels(OVERLAPPING_KEYS),
					cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.sigControl(forKey(TARGET_KEY, NOT_ENOUGH_UNIQUE_SIGS))
								.receiverSigRequired(true)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}


	private HapiApiSpec cannotSetThresholdNegative() {
		return defaultFailingHapiSpec("CannotSetThresholdNegative")
				.given(
						cryptoCreate("testAccount")
				).when().then(
						cryptoUpdate("testAccount")
								.sendThreshold(-1L)
				);
	}

	private HapiApiSpec updateFailsIfMissingSigs() {
		SigControl origKeySigs = KeyShape.threshSigs(3, ON, ON, KeyShape.threshSigs(1, OFF, ON));
		SigControl updKeySigs = KeyShape.listSigs(ON, ON, KeyShape.threshSigs(1, ON, OFF, OFF, OFF));

		return defaultFailingHapiSpec("UpdateFailsIfMissingSigs")
				.given(
						newKeyNamed("origKey").shape(origKeySigs),
						newKeyNamed("updKey").shape(updKeySigs)
				).when(
						cryptoCreate("testAccount")
								.receiverSigRequired(true)
								.key("origKey")
								.sigControl(forKey("origKey", origKeySigs))
				).then(
						cryptoUpdate("testAccount")
								.key("updKey")
								.sigControl(
										forKey("testAccount", origKeySigs),
										forKey("updKey", updKeySigs))
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec updateWithEmptyKey() {
		SigControl origKeySigs = KeyShape.SIMPLE;
		SigControl updKeySigs = threshOf(0, 0);

		return defaultHapiSpec("UpdateWithEmptyKey")
				.given(
						newKeyNamed("origKey").shape(origKeySigs),
						newKeyNamed("updKey").shape(updKeySigs)
				).when(
						cryptoCreate("testAccount")
								.key("origKey")
				).then(
						cryptoUpdate("testAccount")
								.key("updKey")
								.hasPrecheck(BAD_ENCODING)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
