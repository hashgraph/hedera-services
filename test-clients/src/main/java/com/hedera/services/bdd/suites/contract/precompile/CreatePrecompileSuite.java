package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_1;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_4;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_TOKEN_WITH_EMPTY_TOKEN_STRUCT;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_11;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_12;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_13;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_2;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_20;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NFT_CREATE_WITHOUT_FEES;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_5;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_6;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_7;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_8;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_SCENARIO_9;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_WITH_FEES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_NON_FUNGIBLE_WITH_FEES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FUNGIBLE_TOKEN_CREATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInHbarsInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInTokenInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithoutFallbackInSchedule;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CreatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreatePrecompileSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final long AUTO_RENEW_PERIOD = 8_000_000L;
	private static final String TOKEN_SYMBOL = "tokenSymbol";
	private static final String TOKEN_NAME = "tokenName";
	private static final String MEMO = "memo";
	private static final String TOKEN_CREATE_CONTRACT_AS_KEY = "tokenCreateContractAsKey";
	private static final String ACCOUNT = "account";
	private static final String ACCOUNT_KEY = "accountKey";
	private static final String CONTRACT_NAME = "contractName";
	private static final String TOKEN_CREATE_CONTRACT = "tokenCreateContract";
	private static final String FIRST_CREATE_TXN = "firstCreateTxn";
	private static final String ACCOUNT_BALANCE = "ACCOUNT_BALANE";
	private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;

	public static void main(String... args) {
		new CreatePrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				fungibleTokenCreateHappyPathScenario1(),
				fungibleTokenCreateHappyPathWithFeesScenario2(),
				nonFungibleTokenCreateWithoutFees(),
				nonfungibleTokenCreateHappyPathWithFeesScenario4(),
				fungibleTokenCreateAndQueryAndTransferScenario5(),
				nonFungibleTokenCreateAndQueryAndTransferScenario6()
				);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(
				tokenCreateWithEmptyKeysReverts(),
				tokenCreateWithKeyWithMultipleKeyValuesReverts(),
				tokenCreateWithFixedFeeWithMultiplePaymentsReverts(),
				createTokenWithEmptyTokenStruct(),
				createTokenWithInvalidExpiry(),
				nonfungibleTokenCreateWithInvalidRoyaltyFeesScenario12(),
				nonfungibleTokenCreateWithInvalidTreasuryScenario13(),
				fungibleTokenCreateHappyPathWithInvalidFixedFeeWithERC721DenominationScenario14(),
				fungibleTokenCreateHappyPathWithInvalidFeeCollectorScenario15(),
				tokenCreateWithInsufficientHbarsSentFails(),
				delegateCallTokenCreateFails()
		);
	}

	private HapiApiSpec fungibleTokenCreateHappyPathScenario1() {
		final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";

		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String ACCOUNT2 = "account2";
		final String ed25519Key = "ed25519Key";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ed25519Key).shape(ED25519),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ed25519Key),
						cryptoCreate(ACCOUNT2)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_1,
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														spec.registry().getKey(ed25519Key).getEd25519().toByteArray(),
														spec.registry().getKey(ecdsaKey).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)), AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
												newKeyNamed(tokenCreateContractAsKeyDelegate)
														.shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))

										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith()
										.status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> {
							final var token = asTokenString(TokenID.newBuilder()
									.setTokenNum(createTokenNum.get())
									.build()
							);

							return getTokenInfo(token).logged()
									.hasTokenType(TokenType.FUNGIBLE_COMMON)
									.hasSymbol(TOKEN_SYMBOL)
									.hasName(TOKEN_NAME)
									.hasDecimals(8)
									.hasTotalSupply(200)
									.hasEntityMemo(MEMO)
									.hasTreasury(ACCOUNT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
									.hasSupplyType(TokenSupplyType.INFINITE)
									.searchKeysGlobally()
									.hasAdminKey(ed25519Key)
									.hasKycKey(ed25519Key)
									.hasFreezeKey(ecdsaKey)
									.hasWipeKey(ecdsaKey)
									.hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
									.hasFeeScheduleKey(tokenCreateContractAsKeyDelegate)
									.hasPauseKey(contractAdminKey)
									.hasPauseStatus(TokenPauseStatus.Unpaused);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec fungibleTokenCreateHappyPathWithFeesScenario2() {
		final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";

		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String feeCollector = "account2";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String existingFungibleToken = "existingFungibleToken";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						cryptoCreate(feeCollector)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey),
						tokenCreate(existingFungibleToken)
								.exposingCreatedIdTo(existingToken::set),
						tokenAssociate(feeCollector, existingFungibleToken)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_2,
														spec.registry().getKey(ecdsaKey).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(existingFungibleToken)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
												newKeyNamed(tokenCreateContractAsKeyDelegate)
														.shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))

										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith()
										.status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> {
							final var newToken = asTokenString(TokenID.newBuilder()
									.setTokenNum(createTokenNum.get())
									.build()
							);

							return getTokenInfo(newToken).logged()
									.hasTokenType(TokenType.FUNGIBLE_COMMON)
									.hasSymbol(TOKEN_SYMBOL)
									.hasName(TOKEN_NAME)
									.hasDecimals(8)
									.hasTotalSupply(200)
									.hasEntityMemo(MEMO)
									.hasTreasury(TOKEN_CREATE_CONTRACT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
									.hasSupplyType(TokenSupplyType.INFINITE)
									.searchKeysGlobally()
									.hasAdminKey(ecdsaKey)
									.hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
									.hasCustom(fixedHtsFeeInSchedule(1, existingFungibleToken, feeCollector))
									.hasCustom(fixedHbarFeeInSchedule(2, feeCollector))
									.hasCustom(fixedHtsFeeInSchedule(4, newToken, feeCollector))
									.hasCustom(fractionalFeeInSchedule(4, 5, 10, OptionalLong.of(30), true, feeCollector));
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec nonfungibleTokenCreateHappyPathWithFeesScenario4() {
		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String feeCollector = "account2";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String existingFungibleToken = "existingFungibleToken";
		final String treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						newKeyNamed(treasuryAndFeeCollectorKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						cryptoCreate(feeCollector)
								.key(treasuryAndFeeCollectorKey)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey),
						tokenCreate(existingFungibleToken)
								.exposingCreatedIdTo(existingToken::set),
						tokenAssociate(feeCollector, existingFungibleToken)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_4,
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(existingFungibleToken)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.signedBy(ecdsaKey, treasuryAndFeeCollectorKey)
														.alsoSigningWithFullPrefix(treasuryAndFeeCollectorKey)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith()
										.status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> {
							final var newToken = asTokenString(TokenID.newBuilder()
									.setTokenNum(createTokenNum.get())
									.build()
							);

							return getTokenInfo(newToken).logged()
									.hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
									.hasSymbol(TOKEN_SYMBOL)
									.hasName(TOKEN_NAME)
									.hasDecimals(0)
									.hasTotalSupply(0)
									.hasEntityMemo(MEMO)
									.hasTreasury(feeCollector)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
									.hasSupplyType(TokenSupplyType.FINITE)
									.hasMaxSupply(400)
									.searchKeysGlobally()
									.hasAdminKey(TOKEN_CREATE_CONTRACT_AS_KEY)
									.hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
									.hasCustom(royaltyFeeWithFallbackInHbarsInSchedule(4, 5, 10, feeCollector))
									.hasCustom(royaltyFeeWithFallbackInTokenInSchedule(4, 5, 10, existingFungibleToken,
											feeCollector))
									.hasCustom(royaltyFeeWithoutFallbackInSchedule(4, 5, feeCollector));
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}



	private HapiApiSpec fungibleTokenCreateHappyPathWithInvalidFixedFeeWithERC721DenominationScenario14() {
		final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";

		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String feeCollector = "account2";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String existingFungibleToken = "existingFungibleToken";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						cryptoCreate(feeCollector)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey),
						tokenCreate(existingFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.exposingCreatedIdTo(existingToken::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_2,
														spec.registry().getKey(ecdsaKey).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(existingFungibleToken)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, CONTRACT_REVERT_EXECUTED,
								TransactionRecordAsserts.recordWith()
										.status(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON)
										.contractCallResult(
												ContractFnResultAsserts.resultWith()
														.error(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON.name()))),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec fungibleTokenCreateHappyPathWithInvalidFeeCollectorScenario15() {
		final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";

		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String feeCollector = "account2";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String existingFungibleToken = "existingFungibleToken";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						cryptoCreate(feeCollector)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey),
						tokenCreate(existingFungibleToken)
								.exposingCreatedIdTo(existingToken::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_2,
														spec.registry().getKey(ecdsaKey).getECDSASecp256K1().toByteArray(),
														Utils.asSolidityAddress(0, 0, 15252L),
														asAddress(spec.registry().getTokenID(existingFungibleToken)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, CONTRACT_REVERT_EXECUTED,
								TransactionRecordAsserts.recordWith()
										.status(INVALID_CUSTOM_FEE_COLLECTOR)
										.contractCallResult(
												ContractFnResultAsserts.resultWith()
														.error(INVALID_CUSTOM_FEE_COLLECTOR.name()))),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec fungibleTokenCreateHappyPath() {
		final AtomicReference<String> contractID = new AtomicReference<>();
		final AtomicLong createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ACCOUNT_KEY),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ACCOUNT_KEY),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(ContractResources.TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(ACCOUNT_KEY)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_ABI,
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)))
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(10 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith()
										.status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> {
							final var token = asTokenString(TokenID.newBuilder()
									.setTokenNum(createTokenNum.get())
									.build()
							);
							return getTokenInfo(token).logged()
											.hasTokenType(TokenType.FUNGIBLE_COMMON)
											.hasSymbol("MTK")
											.hasName("MyToken")
											.hasDecimals(8)
											.hasTotalSupply(200)
											.hasEntityMemo("memo")
											.hasTreasury(TOKEN_CREATE_CONTRACT)
											.hasAutoRenewAccount("0.0.2")
											.hasAutoRenewPeriod(8000000L)
											.hasSupplyType(TokenSupplyType.INFINITE)
											.searchKeysGlobally()
											.hasAdminKey(ACCOUNT_KEY)
											.hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
											.hasPauseKey(TOKEN_CREATE_CONTRACT_AS_KEY)
											.hasPauseStatus(TokenPauseStatus.Unpaused);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	// TEST-003 & TEST-019
	private HapiApiSpec nonFungibleTokenCreateWithoutFees() {
		final var ed25519Key = "ed25519key";

		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ed25519Key).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ed25519Key)
								.maxAutomaticTokenAssociations(1),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT))

				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.bytecode(CONTRACT_NAME)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							var subop1 = balanceSnapshot(ACCOUNT_BALANCE,  ACCOUNT);
							var subop2 = contractCall(TOKEN_CREATE_CONTRACT, NFT_CREATE_WITHOUT_FEES,
									asAddress(spec.registry().getAccountID(ACCOUNT)),
									spec.registry().getKey(ed25519Key).getEd25519().toByteArray(),
									asAddress(spec.registry().getAccountID(ACCOUNT)),
									AUTO_RENEW_PERIOD
							).via(FIRST_CREATE_TXN)
									.gas(GAS_TO_OFFER)
									.payingWith(ACCOUNT)
									.sending(DEFAULT_AMOUNT_TO_SEND)
									.exposingResultTo(result -> {
										log.info("Explicit create result is {}", result[0]);
										final var res = (byte[])result[0];
										createTokenNum.set(new BigInteger(res).longValueExact());
									})
									.hasKnownStatus(SUCCESS);

							var subop3 = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, subop1, subop2, subop3,
									childRecordsCheck(FIRST_CREATE_TXN, SUCCESS,
											TransactionRecordAsserts.recordWith().status(SUCCESS)));

							var delta = subop3.getResponseRecord().getTransactionFee();
							var subop4 = getAccountBalance(ACCOUNT).hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE,
									-(delta+ DEFAULT_AMOUNT_TO_SEND)));
							var contractBalanceCheck =
									getContractInfo(TOKEN_CREATE_CONTRACT)
											.has(ContractInfoAsserts.contractWith()
													.balanceGreaterThan(0L)
													.balanceLessThan(DEFAULT_AMOUNT_TO_SEND));
							final var hapiGetAccountBalance = getAccountBalance(ACCOUNT).hasTokenBalance(
									asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()), 0);
							final var tokenInfo = getTokenInfo(
									asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()))
									.hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
									.hasSymbol(TOKEN_SYMBOL)
									.hasName(TOKEN_NAME)
									.hasDecimals(0)
									.hasTotalSupply(0)
									.hasEntityMemo(MEMO)
									.hasTreasury(ACCOUNT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
									.hasSupplyType(TokenSupplyType.FINITE)
									.hasFreezeDefault(TokenFreezeStatus.Frozen)
									.hasMaxSupply(10)
									.searchKeysGlobally()
									.hasAdminKey(ed25519Key)
									.hasSupplyKey(ed25519Key)
									.hasPauseKey(ed25519Key)
									.hasFreezeKey(ed25519Key)
									.hasKycKey(ed25519Key)
									.hasFeeScheduleKey(ed25519Key)
									.hasWipeKey(ed25519Key)
									.hasPauseStatus(TokenPauseStatus.Unpaused)
									.logged();
							allRunFor(spec, subop4, hapiGetAccountBalance, tokenInfo,  contractBalanceCheck);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	// TEST-020
	private HapiApiSpec delegateCallTokenCreateFails() {
		final var ed25519Key = "ed25519key";

		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ed25519Key).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ed25519Key)
								.maxAutomaticTokenAssociations(1),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_20,
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.payingWith(ACCOUNT)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
								TransactionRecordAsserts.recordWith().status(INSUFFICIENT_TX_FEE)
										.contractCallResult(ContractFnResultAsserts.resultWith().error(INSUFFICIENT_TX_FEE.name()))
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec fungibleTokenCreateAndQueryAndTransferScenario5() {
		final var ed25519Key = "ed25519key";

		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ed25519Key).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ed25519Key)
								.maxAutomaticTokenAssociations(1),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_5,
														spec.registry().getKey(ed25519Key).getEd25519().toByteArray(),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith().status(SUCCESS),
								TransactionRecordAsserts.recordWith().status(SUCCESS),
								TransactionRecordAsserts.recordWith().status(SUCCESS),
								TransactionRecordAsserts.recordWith().status(SUCCESS)
						),
						sourcing(() -> getAccountBalance(ACCOUNT).hasTokenBalance(
								asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()), 20)),
						sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT).hasTokenBalance(
								asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()), 10)),
						sourcing(() -> getTokenInfo(
								asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()))
									.hasTokenType(TokenType.FUNGIBLE_COMMON)
									.hasSymbol(TOKEN_SYMBOL)
									.hasName(TOKEN_NAME)
									.hasDecimals(8)
									.hasTotalSupply(30)
									.hasEntityMemo(MEMO)
									.hasTreasury(TOKEN_CREATE_CONTRACT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
									.hasSupplyType(TokenSupplyType.INFINITE)
									.searchKeysGlobally()
									.hasAdminKey(ed25519Key)
									.hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
									.hasPauseKey(TOKEN_CREATE_CONTRACT_AS_KEY)
									.hasPauseStatus(TokenPauseStatus.Unpaused)
									.logged()),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec nonFungibleTokenCreateAndQueryAndTransferScenario6() {

		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_6,
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith().status(SUCCESS),
								TransactionRecordAsserts.recordWith().status(SUCCESS),
								TransactionRecordAsserts.recordWith().status(SUCCESS)
						),
						sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT).hasTokenBalance(
								asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()), 0)),
						sourcing(() -> getTokenInfo(
								asTokenString(TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()))
									.hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
									.hasSymbol(TOKEN_SYMBOL)
									.hasName(TOKEN_NAME)
									.hasDecimals(0)
									.hasTotalSupply(0)
									.hasEntityMemo(MEMO)
									.hasTreasury(TOKEN_CREATE_CONTRACT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
									.hasSupplyType(TokenSupplyType.INFINITE)
									.searchKeysGlobally()
									.hasAdminKey(TOKEN_CREATE_CONTRACT_AS_KEY)
									.hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
									.hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
									.logged()),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	// TEST-007 & TEST-017
	private HapiApiSpec tokenCreateWithEmptyKeysReverts() {
		final var ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed("ecdsa").shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.bytecode(CONTRACT_NAME)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var balanceSnapshot = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
							final long sentAmount = 20 * ONE_HBAR;
							final var hapiContractCall =
									contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_7,
										asAddress(spec.registry().getAccountID(ACCOUNT)),
										AUTO_RENEW_PERIOD)
									.via(FIRST_CREATE_TXN)
									.gas(GAS_TO_OFFER)
									.sending(sentAmount)
									.payingWith(ACCOUNT)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, balanceSnapshot, hapiContractCall, txnRecord,
									getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L),
									emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
							final var delta = txnRecord.getResponseRecord().getTransactionFee();
							var changeFromSnapshot =
									getAccountBalance(ACCOUNT).hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
							allRunFor(spec, changeFromSnapshot);
						}),
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	// TEST-008
	private HapiApiSpec tokenCreateWithKeyWithMultipleKeyValuesReverts() {
		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed("ecdsa").shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_8,
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														})
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	// TEST-009
	private HapiApiSpec tokenCreateWithFixedFeeWithMultiplePaymentsReverts() {
		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed("ecdsa").shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_9,
														spec.registry().getKey("ecdsa").getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														})
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)

										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	// TEST-017
	private HapiApiSpec createTokenWithEmptyTokenStruct() {
		return defaultHapiSpec("createTokenWithEmptyTokenStruct")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.bytecode(CONTRACT_NAME)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							var accountSnapshot = balanceSnapshot(ACCOUNT_BALANCE,  ACCOUNT);
							var contractCall = contractCall(TOKEN_CREATE_CONTRACT, CREATE_TOKEN_WITH_EMPTY_TOKEN_STRUCT)
									.via(FIRST_CREATE_TXN)
									.gas(GAS_TO_OFFER)
									.payingWith(ACCOUNT)
									.sending(DEFAULT_AMOUNT_TO_SEND)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, accountSnapshot, contractCall, txnRecord, childRecordsCheck(FIRST_CREATE_TXN,
									CONTRACT_REVERT_EXECUTED,
									TransactionRecordAsserts.recordWith()
											.status(MISSING_TOKEN_SYMBOL)
											.contractCallResult(
													ContractFnResultAsserts.resultWith()
															.error(MISSING_TOKEN_SYMBOL.name()))));
							long delta = txnRecord.getResponseRecord().getTransactionFee();
							var accountBalance =
									getAccountBalance(ACCOUNT).hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE,
									-(delta)));
							allRunFor(spec, accountBalance, getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L));
						}),
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	// TEST-018
	private HapiApiSpec tokenCreateWithInsufficientHbarsSentFails() {
		final var ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed("ed25519key").shape(ED25519),
						cryptoCreate(ACCOUNT)
								.key("ed25519key")
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.bytecode(CONTRACT_NAME)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var balanceSnapshot = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
							final long sentAmount = ONE_HBAR / 100;
							final var hapiContractCall =
									contractCall(TOKEN_CREATE_CONTRACT, NFT_CREATE_WITHOUT_FEES,
											asAddress(spec.registry().getAccountID(ACCOUNT)),
											spec.registry().getKey("ed25519key").getEd25519().toByteArray(),
											asAddress(spec.registry().getAccountID(ACCOUNT)),
											AUTO_RENEW_PERIOD
									)
											.via(FIRST_CREATE_TXN)
											.gas(GAS_TO_OFFER)
											.sending(sentAmount)
											.payingWith(ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, balanceSnapshot, hapiContractCall, txnRecord,
									getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L),
									childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
									TransactionRecordAsserts.recordWith()
											.status(INSUFFICIENT_TX_FEE)
											.contractCallResult(
													ContractFnResultAsserts.resultWith()
															.error(INSUFFICIENT_TX_FEE.name())))
									);
							final var delta = txnRecord.getResponseRecord().getTransactionFee();
							var changeFromSnapshot =
									getAccountBalance(ACCOUNT).hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
							allRunFor(spec, changeFromSnapshot);
						}),
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec createTokenWithInvalidExpiry() {

		final var createTokenNum = new AtomicLong();
		String SECOND_CREATE_TXN = "SECOND_CREATE_TXN";
		String THIRD_CREATE_TXN = "THIRD_CREATE_TXN";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed("ecdsa").shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_11,
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
														)
														.via(SECOND_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														})
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)

										)
						)
				).then(
						getTxnRecord(SECOND_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(SECOND_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED,
								TransactionRecordAsserts.recordWith()
										.status(INVALID_EXPIRATION_TIME)
										.contractCallResult(
												ContractFnResultAsserts.resultWith()
														.error(INVALID_EXPIRATION_TIME.name()))),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec nonfungibleTokenCreateWithInvalidRoyaltyFeesScenario12() {
		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String feeCollector = "account2";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String existingFungibleToken = "existingFungibleToken";
		final String treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						newKeyNamed(treasuryAndFeeCollectorKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						cryptoCreate(feeCollector)
								.key(treasuryAndFeeCollectorKey)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey),
						tokenCreate(existingFungibleToken)
								.exposingCreatedIdTo(existingToken::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_12,
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(existingFungibleToken)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.signedBy(ecdsaKey, treasuryAndFeeCollectorKey)
														.alsoSigningWithFullPrefix(treasuryAndFeeCollectorKey)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														})
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, CONTRACT_REVERT_EXECUTED,
								TransactionRecordAsserts.recordWith()
										.status(CUSTOM_FEE_MUST_BE_POSITIVE)
										.contractCallResult(
												ContractFnResultAsserts.resultWith()
														.error(CUSTOM_FEE_MUST_BE_POSITIVE.name()))),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec nonfungibleTokenCreateWithInvalidTreasuryScenario13() {
		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String feeCollector = "account2";
		final String ecdsaKey = "ecdsaKey";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String existingFungibleToken = "existingFungibleToken";
		final String treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						newKeyNamed(treasuryAndFeeCollectorKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ecdsaKey),
						cryptoCreate(feeCollector)
								.key(treasuryAndFeeCollectorKey)
								.balance(ONE_HUNDRED_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(FUNGIBLE_TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(contractAdminKey),
						tokenCreate(existingFungibleToken)
								.exposingCreatedIdTo(existingToken::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_SCENARIO_13,
														Utils.asSolidityAddress(0, 0, 1543L),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(20 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.signedBy(ecdsaKey, treasuryAndFeeCollectorKey)
														.alsoSigningWithFullPrefix(treasuryAndFeeCollectorKey)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														})
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, CONTRACT_REVERT_EXECUTED,
								TransactionRecordAsserts.recordWith()
										.status(INVALID_ACCOUNT_ID)
										.contractCallResult(
												ContractFnResultAsserts.resultWith()
														.error(INVALID_ACCOUNT_ID.name()))),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec fungibleWithFeesTokenCreateHappyPath() {
		final String ACCOUNT2 = "account2";
		final String ed25519Key = "ed25519Key";
		final String ecdsaKey = "ecdsaKey";
		final AtomicLong createdTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleWithFeesCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ed25519Key).shape(ED25519),
						newKeyNamed(ecdsaKey).shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ed25519Key),
						cryptoCreate(ACCOUNT2)
								.key(ecdsaKey),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(ContractResources.TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_FUNGIBLE_WITH_FEES_ABI,
														spec.registry().getKey(ed25519Key).getEd25519().toByteArray(),
														spec.registry().getKey(ecdsaKey).getECDSASecp256K1().toByteArray())
														.via(FIRST_CREATE_TXN)
														.payingWith(ACCOUNT)
														.sending(20 * ONE_HBAR)
														.gas(GAS_TO_OFFER)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createdTokenNum.set(new BigInteger(res).longValueExact());
														})
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> {
							final var token = asTokenString(TokenID.newBuilder()
									.setTokenNum(createdTokenNum.get())
									.build()
							);
							return getTokenInfo(token).logged()
									.hasTokenType(TokenType.FUNGIBLE_COMMON)
									.hasSymbol("MTK")
									.hasName("MyToken")
									.hasDecimals(8)
									.hasTotalSupply(200)
									.hasEntityMemo("memo")
									.hasTreasury(ACCOUNT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(8000000L)
									.hasSupplyType(TokenSupplyType.INFINITE)
									.searchKeysGlobally()
									.hasAdminKey(ed25519Key)
									.hasKycKey(ecdsaKey)
									.hasKycDefault(TokenKycStatus.Revoked)
									.hasFreezeKey(ecdsaKey)
									.hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
									.hasCustom(fixedHbarFeeInSchedule(4, ACCOUNT))
									.hasCustom(fractionalFeeInSchedule(5, 6, 55, OptionalLong.of(100), true, ACCOUNT));
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec nonFungibleWithFeesTokenCreateHappyPath() {
		final AtomicLong createdTokenNum = new AtomicLong();
		return defaultHapiSpec("nonFungibleWithFeesCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						fileCreate(CONTRACT_NAME),
						updateLargeFile(GENESIS, CONTRACT_NAME, extractByteCode(ContractResources.TOKEN_CREATE_CONTRACT)),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.bytecode(CONTRACT_NAME)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, CREATE_NON_FUNGIBLE_WITH_FEES_ABI,
														asAddress(spec.registry().getAccountID(ACCOUNT)))
														.via(FIRST_CREATE_TXN)
														.payingWith(ACCOUNT)
														.sending(20 * ONE_HBAR)
														.gas(GAS_TO_OFFER)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createdTokenNum.set(new BigInteger(res).longValueExact());
														})
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						childRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.SUCCESS,
								TransactionRecordAsserts.recordWith().status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> {
							final var token = asTokenString(TokenID.newBuilder()
									.setTokenNum(createdTokenNum.get())
									.build()
							);
							return getTokenInfo(token).logged()
									.hasTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
									.hasSymbol("NFT")
									.hasName("MyNFT")
									.hasDecimals(0)
									.hasTotalSupply(0)
									.hasEntityMemo("nftMemo")
									.hasTreasury(TOKEN_CREATE_CONTRACT)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(8000000L)
									.hasSupplyType(TokenSupplyType.FINITE)
									.hasCustom(fixedHbarFeeInSchedule(4, TOKEN_CREATE_CONTRACT))
									.hasCustom(royaltyFeeWithFallbackInHbarsInSchedule(4, 5, 5, TOKEN_CREATE_CONTRACT))
									.hasMaxSupply(55);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
