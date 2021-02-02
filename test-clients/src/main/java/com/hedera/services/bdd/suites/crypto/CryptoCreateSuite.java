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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromMnemonic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;

public class CryptoCreateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoCreateSuite.class);

	public static void main(String... args) {
		new CryptoCreateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						createAnAccountEmptyThresholdKey(),
//						createAnAccountEmptyKeyList(),
//						createAnAccountEmptyNestedKey(),
//						createAnAccountInvalidKeyList(),
//						createAnAccountInvalidNestedKeyList(),
//						createAnAccountInvalidThresholdKey(),
//						createAnAccountInvalidNestedThresholdKey(),
//						createAnAccountThresholdKeyWithInvalidThreshold(),
//						createAnAccountInvalidED25519(),
//						invalidDurationGetsMeaningfulResponse(),
//						xferRequiresCrypto(),
//						createFromMnemonic(),
						payWithMnemonic(),
				}
		);
	}

	private HapiApiSpec payWithMnemonic() {
		String words = "borrow awful also talk jacket muffin frequent fever stay mercy " +
				"chaos equip bird speed east cricket start predict essay near broom insane curtain exhaust";
		String created = "0.0.1001";

		return customHapiSpec("PayWithMnemonic")
				.withProperties(Map.of(
						"default.payer", created,
						"default.payer.mnemonic", words
				)).given().when().then(
						getAccountInfo("0.0.1001")
				);
	}

	private HapiApiSpec createFromMnemonic() {
		String words = "borrow awful also talk jacket muffin frequent fever stay mercy " +
				"chaos equip bird speed east cricket start predict essay near broom insane curtain exhaust";
		return defaultHapiSpec("CreateFromMnemonic")
				.given(
						keyFromMnemonic("futurePayerKey", words)
				).when().then(
						cryptoCreate("futurePayer").key("futurePayerKey")
				);
	}

	private HapiApiSpec xferRequiresCrypto() {
		return defaultHapiSpec("XferRequiresCrypto")
				.given(
						fileCreate("bytecode").path(ContractResources.MULTIPURPOSE_BYTECODE_PATH),
						contractCreate("multiAdmin")
								.bytecode("bytecode")
								.balance(1_234),
						contractCreate("multiNoAdmin")
								.bytecode("bytecode")
								.balance(1_234),
						cryptoCreate("misc")
				).when().then(
						cryptoTransfer(tinyBarsFromTo("misc", "multiAdmin", 1))
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						cryptoTransfer(tinyBarsFromTo("multiAdmin", "misc", 1))
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						cryptoTransfer(tinyBarsFromTo("multiNoAdmin", "misc", 1))
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				);
	}

	public HapiApiSpec invalidDurationGetsMeaningfulResponse() {
		return defaultHapiSpec("InvalidDurationGetsMeaningfulResponse")
				.given().when().then(
						cryptoCreate("broken")
								.autoRenewSecs(1L)
								.hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE)
				);
	}

	private HapiApiSpec vanillaCreateSucceeds() {
		return defaultHapiSpec("VanillaCreateSucceeds")
				.given(
						cryptoCreate("testAccount").via("txn"),
						UtilVerbs.sleepFor(2000)
				).when().then(
						getTxnRecord("txn").logged()
				);
	}

	private HapiApiSpec requiresNewKeyToSign() {
		KeyShape shape = listOf(SIMPLE, listOf(2), threshOf(1, 3));
		SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON), sigs(OFF, OFF, ON)));
		SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON), sigs(OFF, OFF, ON)));

		return defaultHapiSpec("RequiresNewKeyToSign")
				.given().when().then(
						cryptoCreate("test")
								.keyShape(shape)
								.sigControl(forKey("test", invalidSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoCreate("test")
								.keyShape(shape)
								.sigControl(forKey("test", validSig))
				);
	}

	private HapiApiSpec createAnAccountEmptyThresholdKey() {
		KeyShape shape = threshOf(0, 0);
		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountEmptyThresholdKey")
				.given().when().then(
						cryptoCreate("noKeys")
								.keyShape(shape).balance(initialBalance)
								.logged()
								.hasPrecheck(KEY_REQUIRED)
				);
	}

	private HapiApiSpec createAnAccountEmptyKeyList() {
		KeyShape shape = listOf(0);
		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountEmptyKeyList")
				.given().when().then(
						cryptoCreate("noKeys")
								.keyShape(shape).balance(initialBalance)
								.logged()
								.hasPrecheck(KEY_REQUIRED)
				);
	}

	private HapiApiSpec createAnAccountEmptyNestedKey() {
		KeyShape emptyThresholdShape = threshOf(0, 0);
		KeyShape emptyListShape = listOf(0);
		KeyShape shape = threshOf(2, emptyThresholdShape, emptyListShape);
		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountEmptyThresholdKey")
				.given().when().then(
						cryptoCreate("noKeys")
								.keyShape(shape).balance(initialBalance)
								.logged()
								.hasPrecheck(KEY_REQUIRED)
				);
	}

	// One of element in key list is not valid
	private HapiApiSpec createAnAccountInvalidKeyList() {
		KeyShape emptyThresholdShape = threshOf(0, 0);
		KeyShape shape = listOf(SIMPLE, SIMPLE, emptyThresholdShape);
		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountInvalidKeyList")
				.given().when().then(
						cryptoCreate("noKeys")
								.keyShape(shape).balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING)
				);
	}

	// One of element in nested key list is not valid
	private HapiApiSpec createAnAccountInvalidNestedKeyList() {
		KeyShape invalidListShape = listOf(SIMPLE, SIMPLE, listOf(0));
		KeyShape shape = listOf(SIMPLE, SIMPLE, invalidListShape);
		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountInvalidNestedKeyList")
				.given().when().then(
						cryptoCreate("noKeys")
								.keyShape(shape).balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING)
				);
	}

	// One of element in threshold key is not valid
	private HapiApiSpec createAnAccountInvalidThresholdKey() {
		KeyShape emptyListShape = listOf(0);
		KeyShape thresholdShape = threshOf(1, SIMPLE, SIMPLE, emptyListShape);
		long initialBalance = 10_000L;

		//build a threshold key with one of key is invalid
		Key randomKey1 = Key.newBuilder().setEd25519(ByteString.copyFrom(randomUtf8Bytes(32))).build();
		Key randomKey2 = Key.newBuilder().setEd25519(ByteString.copyFrom(randomUtf8Bytes(32))).build();
		Key shortKey = Key.newBuilder().setEd25519(ByteString.copyFrom(new byte[10])).build();

		KeyList invalidKeyList = KeyList.newBuilder().addKeys(randomKey1)
				.addKeys(randomKey2).addKeys(shortKey).build();

		ThresholdKey invalidThresholdKey = ThresholdKey.newBuilder().setThreshold(2)
				.setKeys(invalidKeyList).build();

		Key regKey1 = Key.newBuilder().setThresholdKey(invalidThresholdKey).build();
		Key regKey2 = Key.newBuilder().setKeyList(invalidKeyList).build();

		return defaultHapiSpec("createAnAccountInvalidThresholdKey")
				.given().when().then(
						withOpContext((spec, opLog) -> {
							spec.registry().saveKey("regKey1", regKey1);
							spec.registry().saveKey("regKey2", regKey2);
						}),
						cryptoCreate("badThresholdKeyAccount")
								.keyShape(thresholdShape)
								.balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING),
						cryptoCreate("badThresholdKeyAccount2")
								.key("regKey1")
								.balance(initialBalance)
								.logged()
								.signedBy(GENESIS)
								.hasPrecheck(BAD_ENCODING),
						cryptoCreate("badThresholdKeyAccount3")
								.key("regKey2")
								.balance(initialBalance)
								.logged()
								.signedBy(GENESIS)
								.hasPrecheck(BAD_ENCODING)
				);
	}

	//createAnAccountInvalidNestedThresholdKey
	private HapiApiSpec createAnAccountInvalidNestedThresholdKey() {
		KeyShape goodShape = threshOf(2, 3);
		KeyShape thresholdShape0 = threshOf(0, SIMPLE, SIMPLE, SIMPLE);
		KeyShape thresholdShape4 = threshOf(4, SIMPLE, SIMPLE, SIMPLE);
		KeyShape badShape0 = threshOf(1, thresholdShape0, SIMPLE, SIMPLE);
		KeyShape badShape4 = threshOf(1, SIMPLE, thresholdShape4, SIMPLE);

		KeyShape shape0 = threshOf(3, badShape0, goodShape, goodShape, goodShape);
		KeyShape shape4 = threshOf(3, goodShape, badShape4, goodShape, goodShape);

		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountInvalidNestedKeyList")
				.given().when().then(
						cryptoCreate("noKeys")
								.keyShape(shape0).balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING),
						cryptoCreate("noKeys")
								.keyShape(shape4).balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING)
				);
	}

	private HapiApiSpec createAnAccountThresholdKeyWithInvalidThreshold() {
		KeyShape thresholdShape0 = threshOf(0, SIMPLE, SIMPLE, SIMPLE);
		KeyShape thresholdShape4 = threshOf(4, SIMPLE, SIMPLE, SIMPLE);

		long initialBalance = 10_000L;

		return defaultHapiSpec("createAnAccountThresholdKeyWithInvalidThreshold")
				.given().when().then(
						cryptoCreate("badThresholdKeyAccount1")
								.keyShape(thresholdShape0)
								.balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING),
						cryptoCreate("badThresholdKeyAccount2")
								.keyShape(thresholdShape4)
								.balance(initialBalance)
								.logged()
								.hasPrecheck(BAD_ENCODING)
				);
	}


	private HapiApiSpec createAnAccountInvalidED25519() {
		long initialBalance = 10_000L;
		Key emptyKey = Key.newBuilder().setEd25519(ByteString.EMPTY).build();
		Key shortKey = Key.newBuilder().setEd25519(ByteString.copyFrom(new byte[10])).build();
		return defaultHapiSpec("createAnAccountInvalidED25519")
				.given().when().then(
						withOpContext((spec, opLog) -> {
							spec.registry().saveKey("shortKey", shortKey);
							spec.registry().saveKey("emptyKey", emptyKey);
						}),
						cryptoCreate("shortKey")
								.key("shortKey")
								.balance(initialBalance)
								.signedBy(GENESIS)
								.logged()
								.hasPrecheck(BAD_ENCODING),
						cryptoCreate("emptyKey")
								.key("emptyKey")
								.balance(initialBalance)
								.signedBy(GENESIS)
								.logged()
								.hasPrecheck(BAD_ENCODING)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
