package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyLabel.complex;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoUpdateSuite.class);

	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

	public static void main(String... args) {
		new CryptoUpdateSuite().runSuiteSync();
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
	public boolean canRunConcurrent() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						updateWithUniqueSigs(),
						updateWithOverlappingSigs(),
						updateWithOneEffectiveSig(),
						canUpdateMemo(),
						updateFailsWithInsufficientSigs(),
						cannotSetThresholdNegative(),
						updateWithEmptyKeyFails(),
						updateFailsIfMissingSigs(),
						updateFailsWithContractKey(),
						updateFailsWithOverlyLongLifetime(),
						updateFailsWithInvalidMaxAutoAssociations(),
						usdFeeAsExpected(),
						sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign(),
						updateMaxAutoAssociationsWorks(),
						updateStakingFieldsWorks()
				}
		);
	}

	private HapiApiSpec updateStakingFieldsWorks() {
		return defaultHapiSpec("updateStakingFieldsWorks")
				.given(
						newKeyNamed(ADMIN_KEY),
						cryptoCreate("user")
								.key(ADMIN_KEY)
								.stakedAccountId("0.0.20")
								.declinedReward(true)
				)
				.when(
						getAccountInfo("user")
								.has(AccountInfoAsserts.accountWith()
										.stakedAccountId("0.0.20")
										.noStakingNodeId()
										.isDeclinedReward(true))
								.logged(),

						cryptoUpdate("user")
								.newStakedNodeId(0L)
								.newDeclinedReward(false)
				)
				.then(
						getAccountInfo("user")
								.has(AccountInfoAsserts.accountWith()
										.noStakedAccountId()
										.stakedNodeId(0L)
										.isDeclinedReward(false))
								.logged()
				);
	}

	private HapiApiSpec usdFeeAsExpected() {
		double autoAssocSlotPrice = 0.0018;
		double baseFee = 0.00022;
		double plusOneSlotFee = baseFee + autoAssocSlotPrice;
		double plusTenSlotsFee = baseFee + 10 * autoAssocSlotPrice;

		final var baseTxn = "baseTxn";
		final var plusOneTxn = "plusOneTxn";
		final var plusTenTxn = "plusTenTxn";

		AtomicLong expiration = new AtomicLong();
		return defaultHapiSpec("UsdFeeAsExpectedCryptoUpdate")
				.given(
						newKeyNamed("key").shape(SIMPLE),
						cryptoCreate("payer")
								.key("key")
								.balance(1_000 * ONE_HBAR),
						cryptoCreate("canonicalAccount")
								.key("key")
								.balance(100 * ONE_HBAR)
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS)
								.blankMemo()
								.payingWith("payer"),
						cryptoCreate("autoAssocTarget")
								.key("key")
								.balance(100 * ONE_HBAR)
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS)
								.blankMemo()
								.payingWith("payer"),
						getAccountInfo("canonicalAccount")
								.exposingExpiry(expiration::set)
				)
				.when(
						sourcing(() ->
								cryptoUpdate("canonicalAccount")
										.payingWith("canonicalAccount")
										.expiring(expiration.get() + THREE_MONTHS_IN_SECONDS)
										.blankMemo()
										.via(baseTxn)
						),
						cryptoUpdate("autoAssocTarget")
								.payingWith("autoAssocTarget")
								.blankMemo()
								.maxAutomaticAssociations(1)
								.via(plusOneTxn),
						cryptoUpdate("autoAssocTarget")
								.payingWith("autoAssocTarget")
								.blankMemo()
								.maxAutomaticAssociations(11)
								.via(plusTenTxn)
				)
				.then(
						validateChargedUsd(baseTxn, baseFee),
						validateChargedUsd(plusOneTxn, plusOneSlotFee),
						validateChargedUsd(plusTenTxn, plusTenSlotsFee)
				);
	}

	private HapiApiSpec updateFailsWithInvalidMaxAutoAssociations() {
		final int tokenAssociations_restrictedNetwork = 10;
		final int tokenAssociations_adventurousNetwork = 1_000;
		final int originalMax = 2;
		final int newBadMax = originalMax - 1;
		final int newGoodMax = originalMax + 1;
		final String tokenA = "tokenA";
		final String tokenB = "tokenB";
		final String firstUser = "firstUser";
		final String treasury = "treasury";
		final String tokenAcreateTxn = "tokenACreate";
		final String tokenBcreateTxn = "tokenBCreate";
		final String transferAToFU = "transferAToFU";
		final String transferBToFU = "transferBToFU";

		return defaultHapiSpec("UpdateFailsWithInvalidMaxAutoAssociations")
				.given(
						overridingTwo(
								"entities.limitTokenAssociations", "true",
								"tokens.maxPerAccount", "" + tokenAssociations_restrictedNetwork),
						cryptoCreate(treasury)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(firstUser)
								.balance(ONE_HBAR)
								.maxAutomaticTokenAssociations(originalMax),
						tokenCreate(tokenA)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenAcreateTxn),
						getTxnRecord(tokenAcreateTxn)
								.hasNewTokenAssociation(tokenA, treasury),
						tokenCreate(tokenB)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenBcreateTxn),
						getTxnRecord(tokenBcreateTxn)
								.hasNewTokenAssociation(tokenB, treasury)
				)
				.when(
						cryptoTransfer(moving(1, tokenA).between(treasury, firstUser))
								.via(transferAToFU),
						getTxnRecord(transferAToFU)
								.hasNewTokenAssociation(tokenA, firstUser),
						cryptoTransfer(moving(1, tokenB).between(treasury, firstUser))
								.via(transferBToFU),
						getTxnRecord(transferBToFU)
								.hasNewTokenAssociation(tokenB, firstUser)
				)
				.then(
						getAccountDetails(firstUser)
								.payingWith(GENESIS)
								.hasAlreadyUsedAutomaticAssociations(originalMax)
								.hasMaxAutomaticAssociations(originalMax)
								.has(accountWith().noAllowances()),
						cryptoUpdate(firstUser)
								.maxAutomaticAssociations(newBadMax)
								.hasKnownStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT),
						cryptoUpdate(firstUser)
								.maxAutomaticAssociations(newGoodMax),
						cryptoUpdate(firstUser)
								.maxAutomaticAssociations(tokenAssociations_restrictedNetwork + 1)
								.hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
						overriding( "entities.limitTokenAssociations", "false"),
						cryptoUpdate(firstUser)
								.maxAutomaticAssociations(tokenAssociations_restrictedNetwork + 1),
						overriding("tokens.maxPerAccount", "" + tokenAssociations_adventurousNetwork)
				);
	}

	private HapiApiSpec updateFailsWithOverlyLongLifetime() {
		final var smallBuffer = 12_345L;
		final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;
		return defaultHapiSpec("UpdateFailsWithOverlyLongLifetime")
				.given(
						cryptoCreate(TARGET_ACCOUNT)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.expiring(excessiveExpiry)
								.hasKnownStatus(INVALID_EXPIRATION_TIME)
				);
	}

	private HapiApiSpec sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign() {
		String sysAccount = "0.0.99";
		String randomAccount = "randomAccount";
		String firstKey = "firstKey";
		String secondKey = "secondKey";

		return defaultHapiSpec("sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign")
				.given(
						newKeyNamed(firstKey).shape(SIMPLE),
						newKeyNamed(secondKey).shape(SIMPLE)
				)
				.when(
						cryptoCreate(randomAccount)
								.key(firstKey)
				)
				.then(
						cryptoUpdate(sysAccount)
								.key(secondKey)
								.signedBy(GENESIS)
								.payingWith(GENESIS)
								.hasKnownStatus(SUCCESS)
								.logged(),
						cryptoUpdate(randomAccount)
								.key(secondKey)
								.signedBy(firstKey)
								.payingWith(GENESIS)
								.hasPrecheck(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec canUpdateMemo() {
		String firstMemo = "First";
		String secondMemo = "Second";
		return defaultHapiSpec("CanUpdateMemo")
				.given(
						cryptoCreate(TARGET_ACCOUNT)
								.balance(0L)
								.entityMemo(firstMemo)
				).when(
						cryptoUpdate(TARGET_ACCOUNT)
								.entityMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						cryptoUpdate(TARGET_ACCOUNT)
								.entityMemo(secondMemo)
				).then(
						getAccountDetails(TARGET_ACCOUNT)
								.payingWith(GENESIS)
								.has(accountWith().memo(secondMemo))
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

	private HapiApiSpec updateFailsWithContractKey() {
		return defaultHapiSpec("UpdateFailsWithContractKey")
				.given(
						cryptoCreate(TARGET_ACCOUNT)
				).when().then(
						cryptoUpdate(TARGET_ACCOUNT)
								.usingContractKey()
								.hasKnownStatus(INVALID_SIGNATURE)
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
		return defaultHapiSpec("CannotSetThresholdNegative")
				.given(
						cryptoCreate("testAccount")
				).when().then(
						cryptoUpdate("testAccount")
								.sendThreshold(-1L)
				);
	}

	private HapiApiSpec updateFailsIfMissingSigs() {
		SigControl origKeySigs = KeyShape.threshSigs(3, ON, ON, KeyShape.threshSigs(1, OFF, ON));
		SigControl updKeySigs = KeyShape.listSigs(ON, OFF, KeyShape.threshSigs(1, ON, OFF, OFF, OFF));

		return defaultHapiSpec("UpdateFailsIfMissingSigs")
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

	private HapiApiSpec updateWithEmptyKeyFails() {
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
								.hasPrecheck(INVALID_ADMIN_KEY)
				);
	}

	private HapiApiSpec updateMaxAutoAssociationsWorks() {
		final int tokenAssociations_restrictedNetwork = 10;
		final int tokenAssociations_adventurousNetwork = 1_000;
		final int originalMax = 2;
		final int newBadMax = originalMax - 1;
		final int newGoodMax = originalMax + 1;
		final String tokenA = "tokenA";
		final String tokenB = "tokenB";

		final String treasury = "treasury";
		final String tokenACreate = "tokenACreate";
		final String tokenBCreate = "tokenBCreate";
		final String transferAToC = "transferAToC";
		final String transferBToC = "transferBToC";
		final String CONTRACT = "Multipurpose";
		final String ADMIN_KEY = "adminKey";

		return defaultHapiSpec("updateMaxAutoAssociationsWorks")
				.given(
						overridingTwo(
								"entities.limitTokenAssociations", "true",
								"tokens.maxPerAccount", "" + 10),
						cryptoCreate(treasury)
								.balance(ONE_HUNDRED_HBARS),
						newKeyNamed(ADMIN_KEY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
								.maxAutomaticTokenAssociations(originalMax),
						tokenCreate(tokenA)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenACreate),
						getTxnRecord(tokenACreate)
								.hasNewTokenAssociation(tokenA, treasury),
						tokenCreate(tokenB)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(Long.MAX_VALUE)
								.treasury(treasury)
								.via(tokenBCreate),
						getTxnRecord(tokenBCreate)
								.hasNewTokenAssociation(tokenB, treasury),
						getContractInfo(CONTRACT)
								.has(ContractInfoAsserts.contractWith().maxAutoAssociations(originalMax))
								.logged()
				)
				.when(
						cryptoTransfer(moving(1, tokenA).between(treasury, CONTRACT))
								.via(transferAToC),
						getTxnRecord(transferAToC)
								.hasNewTokenAssociation(tokenA, CONTRACT),
						cryptoTransfer(moving(1, tokenB).between(treasury, CONTRACT))
								.via(transferBToC),
						getTxnRecord(transferBToC)
								.hasNewTokenAssociation(tokenB, CONTRACT)
				)
				.then(
						getContractInfo(CONTRACT)
								.payingWith(GENESIS)
								.has(contractWith()
										.hasAlreadyUsedAutomaticAssociations(originalMax)
										.maxAutoAssociations(originalMax)),
						contractUpdate(CONTRACT)
								.newMaxAutomaticAssociations(newBadMax)
								.hasKnownStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT),
						contractUpdate(CONTRACT)
								.newMaxAutomaticAssociations(newGoodMax),
						contractUpdate(CONTRACT)
								.newMaxAutomaticAssociations(tokenAssociations_restrictedNetwork + 1)
								.hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),

						overriding("entities.limitTokenAssociations", "false"),
						contractUpdate(CONTRACT)
								.newMaxAutomaticAssociations(tokenAssociations_restrictedNetwork + 1),
						overriding("tokens.maxPerAccount", "" + tokenAssociations_adventurousNetwork)
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
