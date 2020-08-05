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

import com.hedera.services.bdd.spec.keys.SigStyle;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyLabel.complex;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigStyle.*;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

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
//				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				updateWithUniqueSigs(SigStyle.MAP),
				updateWithUniqueSigs(SigStyle.LIST),
				updateWithOverlappingSigs(SigStyle.MAP),
				updateWithOverlappingSigs(SigStyle.LIST),
				updateWithOneEffectiveSig(SigStyle.MAP),
				updateWithOneEffectiveSig(SigStyle.LIST)
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
//				updateFailsWithInsufficientSigs(SigStyle.MAP),
//				updateFailsWithInsufficientSigs(SigStyle.LIST),
//				updateFailsIfMissingSigs()
//				cannotSetThresholdNegative()
				updateWithEmptyKey()
		);
	}

	private HapiApiSpec updateWithUniqueSigs(SigStyle style) {
		String name = String.format("UpdateWithUnique%sSigs", (style == SigStyle.LIST) ? "Legacy" : "");
		return defaultHapiSpec(name)
				.given(
					newKeyNamed(TARGET_KEY).shape(TWO_LEVEL_THRESH).labels(OVERLAPPING_KEYS),
					cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY)
				).when().then(
					cryptoUpdate(TARGET_ACCOUNT)
							.sigControl(forKey(TARGET_KEY, ENOUGH_UNIQUE_SIGS))
							.sigStyle(style)
							.receiverSigRequired(true)
				);
	}

	private HapiApiSpec updateWithOneEffectiveSig(SigStyle style) {
		String name = String.format("UpdateWithOneEffective%sSig", (style == SigStyle.LIST) ? "Legacy" : "");
		KeyLabel ONE_UNIQUE_KEY = complex(
				complex("X", "X", "X", "X", "X", "X", "X"),
				complex("X", "X", "X", "X", "X", "X", "X"));
		SigControl SINGLE_SIG = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
				KeyShape.threshSigs(3, OFF, OFF, OFF, ON, OFF, OFF, OFF));

		return defaultHapiSpec(name)
				.given(
						newKeyNamed("repeatingKey").shape(TWO_LEVEL_THRESH).labels(ONE_UNIQUE_KEY),
						cryptoCreate(TARGET_ACCOUNT).key("repeatingKey").balance(1_000_000_000L)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.sigControl(forKey("repeatingKey", SINGLE_SIG))
								.sigStyle(style)
								.receiverSigRequired(true)
								.hasKnownStatus(style == MAP ? SUCCESS : INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec updateWithOverlappingSigs(SigStyle style) {
		String name = String.format("UpdateWithOverlapping%sSigs", (style == SigStyle.LIST) ? "Legacy" : "");
		return defaultHapiSpec(name)
				.given(
					newKeyNamed(TARGET_KEY).shape(TWO_LEVEL_THRESH).labels(OVERLAPPING_KEYS),
					cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.sigControl(forKey(TARGET_KEY, ENOUGH_OVERLAPPING_SIGS))
								.sigStyle(style)
								.receiverSigRequired(true)
								.hasKnownStatus(style == MAP ? SUCCESS : INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec updateFailsWithInsufficientSigs(SigStyle style) {
		String name = String.format("UpdateFailsWithInsufficient%sSigs", (style == SigStyle.LIST) ? "Legacy" : "");
		return defaultHapiSpec(name)
				.given(
					newKeyNamed(TARGET_KEY).shape(TWO_LEVEL_THRESH).labels(OVERLAPPING_KEYS),
					cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.sigControl(forKey(TARGET_KEY, NOT_ENOUGH_UNIQUE_SIGS))
								.sigStyle(style)
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

	/* XXX
	https://github.com/swirlds/services-hedera/issues/1529
	*/
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
