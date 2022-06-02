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
import com.hedera.services.bdd.spec.transactions.contract.HapiEthereumCall;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
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
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

//Some of the test cases cannot be converted to use eth calls,
//since they use admin keys, which are held by the txn payer.
//In the case of an eth txn, we revoke the payers keys and the txn would fail.
//The only way an eth account to create a token is the admin key to be of a contractId type.
public class CreatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreatePrecompileSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final long AUTO_RENEW_PERIOD = 8_000_000L;
	private static final String TOKEN_SYMBOL = "tokenSymbol";
	private static final String TOKEN_NAME = "tokenName";
	private static final String MEMO = "memo";
	private static final String TOKEN_CREATE_CONTRACT_AS_KEY = "tokenCreateContractAsKey";
	private static final String ACCOUNT = "account";
	private static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
	private static final String FIRST_CREATE_TXN = "firstCreateTxn";
	private static final String ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
	private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
	private static final String ED25519KEY = "ed25519key";
	private static final String ECDSA_KEY = "ecdsa";
	private final String EXISTING_TOKEN = "EXISTING_TOKEN";
	private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";

	public static void main(String... args) {
		new CreatePrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	//TODO: Fix contract name in TokenCreateContract.sol
	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(new HapiApiSpec[] {
				fungibleTokenCreateHappyPath(),
				fungibleTokenCreateWithFeesHappyPath(),
				nonFungibleTokenCreateHappyPath(),
				nonFungibleTokenCreateWithFeesHappyPath(),
				fungibleTokenCreateThenQueryAndTransfer(),
				nonFungibleTokenCreateThenQuery(),
				inheritsSenderAutoRenewAccountIfAnyForNftCreate(),
				inheritsSenderAutoRenewAccountForTokenCreate(),
				createTokenWithDefaultExpiryAndEmptyKeys()
		});
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(new HapiApiSpec[] {
				tokenCreateWithEmptyKeysReverts(),
				tokenCreateWithKeyWithMultipleKeyValuesReverts(),
				tokenCreateWithFixedFeeWithMultiplePaymentsReverts(),
				createTokenWithEmptyTokenStruct(),
				createTokenWithInvalidExpiry(),
				createTokenWithInvalidRoyaltyFee(),
				createTokenWithInvalidTreasury(),
				createTokenWithInvalidFixedFeeWithERC721Denomination(),
				createTokenWithInvalidFeeCollector(),
				createTokenWithInsufficientValueSent(),
				delegateCallTokenCreateFails()
		});
	}

	// TEST-001
	private HapiApiSpec fungibleTokenCreateHappyPath() {
		final var tokenCreateContractAsKeyDelegate = "tokenCreateContractAsKeyDelegate";
		final var createTokenNum = new AtomicLong();
		final var ACCOUNT2 = "account2";
		final var contractAdminKey = "contractAdminKey";
		final var ACCOUNT_TO_ASSOCIATE = "account3";
		final var ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
		return defaultHapiSpec("fungibleTokenCreateHappyPath")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						cryptoCreate(AUTO_RENEW_ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ED25519KEY),
						cryptoCreate(ACCOUNT2)
								.balance(ONE_HUNDRED_HBARS)
								.key(ECDSA_KEY),
						cryptoCreate(ACCOUNT_TO_ASSOCIATE)
								.key(ACCOUNT_TO_ASSOCIATE_KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
								.adminKey(contractAdminKey)
								.autoRenewAccountId(AUTO_RENEW_ACCOUNT)
								.signedBy(contractAdminKey, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
						getContractInfo(TOKEN_CREATE_CONTRACT)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
								.logged()

				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createTokenWithKeysAndExpiry",
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
														spec.registry().getKey(
																ECDSA_KEY).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD,
														asAddress(spec.registry().getAccountID(ACCOUNT_TO_ASSOCIATE)))
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
														.refusingEthConversion()
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
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
										.status(ResponseCodeEnum.SUCCESS),
								TransactionRecordAsserts.recordWith()
										.status(ResponseCodeEnum.SUCCESS),
								TransactionRecordAsserts.recordWith()
										.status(ResponseCodeEnum.SUCCESS)),
						sourcing(() -> getAccountInfo(ACCOUNT_TO_ASSOCIATE).logged().hasTokenRelationShipCount(1)),
						sourcing(() -> getTokenInfo(asTokenString(
								TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()))
								.logged()
								.hasTokenType(TokenType.FUNGIBLE_COMMON)
								.hasSymbol(TOKEN_SYMBOL)
								.hasName(TOKEN_NAME)
								.hasDecimals(8)
								.hasTotalSupply(100)
								.hasEntityMemo(MEMO)
								.hasTreasury(ACCOUNT)
								// Token doesn't inherit contract's auto-renew account if set in tokenCreate
								.hasAutoRenewAccount(ACCOUNT)
								.hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
								.hasSupplyType(TokenSupplyType.INFINITE)
								.searchKeysGlobally()
								.hasAdminKey(ED25519KEY)
								.hasKycKey(ED25519KEY)
								.hasFreezeKey(ECDSA_KEY)
								.hasWipeKey(ECDSA_KEY)
								.hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
								.hasFeeScheduleKey(tokenCreateContractAsKeyDelegate)
								.hasPauseKey(contractAdminKey)
								.hasPauseStatus(TokenPauseStatus.Unpaused)),
						cryptoDelete(ACCOUNT).hasKnownStatus(ACCOUNT_IS_TREASURY)
				);
	}

	// TEST-002
	private HapiApiSpec fungibleTokenCreateWithFeesHappyPath() {
		final var createdTokenNum = new AtomicLong();
		final var feeCollector = "feeCollector";
		return defaultHapiSpec("fungibleTokenCreateWithFeesHappyPath")
				.given(
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ECDSA_KEY),
						cryptoCreate(feeCollector)
								.balance(ONE_HUNDRED_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER),
						tokenCreate(EXISTING_TOKEN),
						tokenAssociate(feeCollector, EXISTING_TOKEN)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT,
														"createTokenWithAllCustomFeesAvailable",
														spec.registry().getKey(
																ECDSA_KEY).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(EXISTING_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.refusingEthConversion()
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
															createdTokenNum.set(new BigInteger(res).longValueExact());
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
									.setTokenNum(createdTokenNum.get())
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
									.hasAdminKey(ECDSA_KEY)
									.hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
									.hasCustom(fixedHtsFeeInSchedule(1, EXISTING_TOKEN, feeCollector))
									.hasCustom(fixedHbarFeeInSchedule(2, feeCollector))
									.hasCustom(fixedHtsFeeInSchedule(4, newToken, feeCollector))
									.hasCustom(
											fractionalFeeInSchedule(4, 5, 10, OptionalLong.of(30), true, feeCollector));
						})
				);
	}

	private HapiApiSpec inheritsSenderAutoRenewAccountIfAnyForNftCreate() {
		final var createdNftTokenNum = new AtomicLong();
		return defaultHapiSpec("inheritsSenderAutoRenewAccountIfAnyForNftCreate")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						newKeyNamed(ECDSA_KEY),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						cryptoCreate(AUTO_RENEW_ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ED25519KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.autoRenewAccountId(AUTO_RENEW_ACCOUNT)
														.gas(GAS_TO_OFFER)
										)
						),
						getContractInfo(TOKEN_CREATE_CONTRACT)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
								.logged()
				).then(
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
							final var subop2 = contractCall(TOKEN_CREATE_CONTRACT, "createNFTTokenWithKeysAndExpiry",
									asAddress(spec.registry().getAccountID(ACCOUNT)),
									spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
									new byte[] { },
									AUTO_RENEW_PERIOD
							).via(FIRST_CREATE_TXN)
									.gas(GAS_TO_OFFER)
									.payingWith(ACCOUNT)
									.sending(DEFAULT_AMOUNT_TO_SEND)
									.refusingEthConversion()
									.exposingResultTo(result -> {
										log.info("Explicit create result is {}", result[0]);
										final var res = (byte[]) result[0];
										createdNftTokenNum.set(new BigInteger(res).longValueExact());
									})
									.hasKnownStatus(SUCCESS);

							allRunFor(spec, subop1, subop2, childRecordsCheck(FIRST_CREATE_TXN, SUCCESS,
									TransactionRecordAsserts.recordWith().status(SUCCESS)));

							final var nftInfo = getTokenInfo(
									asTokenString(TokenID.newBuilder().setTokenNum(createdNftTokenNum.get()).build()))
									.hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
									.logged();

							allRunFor(spec, nftInfo);
						})
				);
	}


	private HapiApiSpec inheritsSenderAutoRenewAccountForTokenCreate() {
		final var createTokenNum = new AtomicLong();
		final var contractAdminKey = "contractAdminKey";
		final var ACCOUNT_TO_ASSOCIATE = "account3";
		final var ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
		return defaultHapiSpec("inheritsSenderAutoRenewAccountForTokenCreate")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						cryptoCreate(AUTO_RENEW_ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ED25519KEY),
						cryptoCreate(ACCOUNT_TO_ASSOCIATE)
								.key(ACCOUNT_TO_ASSOCIATE_KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
								.adminKey(contractAdminKey)
								.autoRenewAccountId(
										AUTO_RENEW_ACCOUNT) // inherits if the tokenCreateOp doesn't have
								// autoRenewAccount
								.signedBy(contractAdminKey, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
						getContractInfo(TOKEN_CREATE_CONTRACT)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
								.logged()
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createTokenWithKeysAndExpiry",
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
														spec.registry().getKey(
																ECDSA_KEY).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														new byte[] { }, // set empty autoRenewAccount
														AUTO_RENEW_PERIOD,
														asAddress(spec.registry().getAccountID(ACCOUNT_TO_ASSOCIATE)))
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
														.refusingEthConversion()
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														})
										)
						)
				).then(
						sourcing(() -> getTokenInfo(asTokenString(
								TokenID.newBuilder().setTokenNum(createTokenNum.get()).build()))
								.logged()
								.hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
								.hasPauseStatus(TokenPauseStatus.Unpaused))
				);
	}

	// TEST-003 & TEST-019
	private HapiApiSpec nonFungibleTokenCreateHappyPath() {
		final var createdTokenNum = new AtomicLong();
		return defaultHapiSpec("nonFungibleTokenCreateHappyPath")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
							final var subop2 = contractCall(
									TOKEN_CREATE_CONTRACT,
									"createNFTTokenWithKeysAndExpiry",
									asAddress(spec.registry().getAccountID(ACCOUNT)),
									spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
									asAddress(spec.registry().getAccountID(ACCOUNT)),
									AUTO_RENEW_PERIOD
							).via(FIRST_CREATE_TXN)
									.gas(GAS_TO_OFFER)
									.payingWith(ACCOUNT)
									.sending(DEFAULT_AMOUNT_TO_SEND)
									.exposingResultTo(result -> {
										log.info("Explicit create result is {}", result[0]);
										final var res = (byte[]) result[0];
										createdTokenNum.set(new BigInteger(res).longValueExact());
									})
									.refusingEthConversion()
									.hasKnownStatus(SUCCESS);
							final var subop3 = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, subop1, subop2, subop3,
									childRecordsCheck(FIRST_CREATE_TXN, SUCCESS,
											TransactionRecordAsserts.recordWith().status(SUCCESS)));

							final var delta = subop3.getResponseRecord().getTransactionFee();
							final var effectivePayer = ACCOUNT;
							final var subop4 = getAccountBalance(effectivePayer)
									.hasTinyBars(changeFromSnapshot(
											ACCOUNT_BALANCE,
											-(delta + DEFAULT_AMOUNT_TO_SEND)));
							final var contractBalanceCheck =
									getContractInfo(TOKEN_CREATE_CONTRACT)
											.has(ContractInfoAsserts.contractWith()
													.balanceGreaterThan(0L)
													.balanceLessThan(DEFAULT_AMOUNT_TO_SEND));
							final var getAccountTokenBalance = getAccountBalance(ACCOUNT).hasTokenBalance(
									asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()), 0);
							final var tokenInfo = getTokenInfo(
									asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()))
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
									.hasAdminKey(ED25519KEY)
									.hasSupplyKey(ED25519KEY)
									.hasPauseKey(ED25519KEY)
									.hasFreezeKey(ED25519KEY)
									.hasKycKey(ED25519KEY)
									.hasFeeScheduleKey(ED25519KEY)
									.hasWipeKey(ED25519KEY)
									.hasPauseStatus(TokenPauseStatus.Unpaused)
									.logged();
							allRunFor(spec, subop4, getAccountTokenBalance, tokenInfo, contractBalanceCheck);
						})
				);
	}

	// TEST-004
	private HapiApiSpec nonFungibleTokenCreateWithFeesHappyPath() {
		final var createTokenNum = new AtomicLong();
		final var feeCollector = "account2";
		final var treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
		return defaultHapiSpec("nonFungibleTokenCreateWithFeesHappyPath")
				.given(
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						newKeyNamed(treasuryAndFeeCollectorKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ECDSA_KEY),
						cryptoCreate(feeCollector)
								.key(treasuryAndFeeCollectorKey)
								.balance(ONE_HUNDRED_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER),
						tokenCreate(EXISTING_TOKEN),
						tokenAssociate(feeCollector, EXISTING_TOKEN)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT,
														"createNonFungibleTokenWithCustomFees",
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(EXISTING_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.signedBy(ECDSA_KEY, treasuryAndFeeCollectorKey)
														.alsoSigningWithFullPrefix(treasuryAndFeeCollectorKey)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
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
									.hasCustom(royaltyFeeWithFallbackInTokenInSchedule(4, 5, 10, EXISTING_TOKEN,
											feeCollector))
									.hasCustom(royaltyFeeWithoutFallbackInSchedule(4, 5, feeCollector));
						})
				);
	}

	// TEST-005
	private HapiApiSpec fungibleTokenCreateThenQueryAndTransfer() {
		final var createdTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleTokenCreateThenQueryAndTransfer")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY)
								.maxAutomaticTokenAssociations(1),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createTokenThenQueryAndTransfer",
														spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.refusingEthConversion()
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
															createdTokenNum.set(new BigInteger(res).longValueExact());
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
								asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()), 20)),
						sourcing(() -> getAccountBalance(TOKEN_CREATE_CONTRACT).hasTokenBalance(
								asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()), 10)),
						sourcing(() -> getTokenInfo(
								asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()))
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
								.hasAdminKey(ED25519KEY)
								.hasSupplyKey(TOKEN_CREATE_CONTRACT_AS_KEY)
								.hasPauseKey(TOKEN_CREATE_CONTRACT_AS_KEY)
								.hasPauseStatus(TokenPauseStatus.Unpaused)
								.logged())
				);
	}

	// TEST-006
	private HapiApiSpec nonFungibleTokenCreateThenQuery() {
		final var createdTokenNum = new AtomicLong();
		return defaultHapiSpec("nonFungibleTokenCreateThenQuery")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createNonFungibleTokenThenQuery",
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
															createdTokenNum.set(new BigInteger(res).longValueExact());
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
								asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()), 0)),
						sourcing(() -> getTokenInfo(
								asTokenString(TokenID.newBuilder().setTokenNum(createdTokenNum.get()).build()))
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
								.logged())
				);
	}

	// TEST-007 & TEST-016
	private HapiApiSpec tokenCreateWithEmptyKeysReverts() {
		return defaultHapiSpec("tokenCreateWithEmptyKeysReverts")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var balanceSnapshot =
									balanceSnapshot(ACCOUNT_BALANCE, spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT);
							final var hapiContractCall =
									contractCall(TOKEN_CREATE_CONTRACT, "createTokenWithEmptyKeysArray",
											asAddress(spec.registry().getAccountID(ACCOUNT)),
											AUTO_RENEW_PERIOD)
											.via(FIRST_CREATE_TXN)
											.gas(GAS_TO_OFFER)
											.sending(DEFAULT_AMOUNT_TO_SEND)
											.payingWith(ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, balanceSnapshot, hapiContractCall, txnRecord,
									getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L),
									emptyChildRecordsCheck(FIRST_CREATE_TXN,
											ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
							final var delta =
									spec.isUsingEthCalls()
									? GAS_TO_OFFER * HapiEthereumCall.DEFAULT_GAS_PRICE_TINYBARS :
									txnRecord.getResponseRecord().getTransactionFee();
							final var effectivePayer = spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT;
							final var changeFromSnapshot = getAccountBalance(effectivePayer)
									.hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
							allRunFor(spec, changeFromSnapshot);
						}),
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged()
				);
	}

	// TEST-008
	private HapiApiSpec tokenCreateWithKeyWithMultipleKeyValuesReverts() {
		return defaultHapiSpec("tokenCreateWithKeyWithMultipleKeyValuesReverts")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT,
														"createTokenWithKeyWithMultipleValues",
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
				);
	}

	// TEST-009
	private HapiApiSpec tokenCreateWithFixedFeeWithMultiplePaymentsReverts() {
		return defaultHapiSpec("tokenCreateWithFixedFeeWithMultiplePaymentsReverts")
				.given(
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createTokenWithInvalidFixedFee",
														spec.registry().getKey(
																ECDSA_KEY).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)

										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
						getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
						emptyChildRecordsCheck(FIRST_CREATE_TXN, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
				);
	}

	// TEST-010 & TEST-017
	private HapiApiSpec createTokenWithEmptyTokenStruct() {
		return defaultHapiSpec("createTokenWithEmptyTokenStruct")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var accountSnapshot = spec.isUsingEthCalls()
									? balanceSnapshot(ACCOUNT_BALANCE, DEFAULT_CONTRACT_SENDER)
									: balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
							final var contractCall = contractCall(TOKEN_CREATE_CONTRACT,
									"createTokenWithEmptyTokenStruct")
									.via(FIRST_CREATE_TXN)
									.gas(GAS_TO_OFFER)
									.payingWith(ACCOUNT)
									.sending(DEFAULT_AMOUNT_TO_SEND)
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							final var txnRecord = getTxnRecord(FIRST_CREATE_TXN);
							allRunFor(spec, accountSnapshot, contractCall, txnRecord,
									childRecordsCheck(FIRST_CREATE_TXN,
											CONTRACT_REVERT_EXECUTED,
											TransactionRecordAsserts.recordWith()
													.status(MISSING_TOKEN_SYMBOL)
													.contractCallResult(
															ContractFnResultAsserts.resultWith()
																	.error(MISSING_TOKEN_SYMBOL.name()))));
							final var delta = spec.isUsingEthCalls()
									? GAS_TO_OFFER * HapiEthereumCall.DEFAULT_GAS_PRICE_TINYBARS
									: txnRecord.getResponseRecord().getTransactionFee();
							final var effectivePayer = spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT;
							final var accountBalance = getAccountBalance(effectivePayer)
									.hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
							allRunFor(spec, accountBalance, getAccountBalance(TOKEN_CREATE_CONTRACT).hasTinyBars(0L));
						}),
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(TOKEN_CREATE_CONTRACT).logged()
				);
	}

	// TEST-011
	private HapiApiSpec createTokenWithInvalidExpiry() {
		return defaultHapiSpec("createTokenWithInvalidExpiry")
				.given(
						newKeyNamed("ecdsa").shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createTokenWithInvalidExpiry",
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
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
								TransactionRecordAsserts.recordWith()
										.status(INVALID_EXPIRATION_TIME)
										.contractCallResult(ContractFnResultAsserts.resultWith()
												.error(INVALID_EXPIRATION_TIME.name())))
				);
	}

	// TEST-012
	private HapiApiSpec createTokenWithInvalidRoyaltyFee() {
		final String feeCollector = "account2";
		final String contractAdminKey = "contractAdminKey";
		AtomicReference<String> existingToken = new AtomicReference<>();
		final String treasuryAndFeeCollectorKey = "treasuryAndFeeCollectorKey";
		return defaultHapiSpec("createTokenWithInvalidRoyaltyFee")
				.given(
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						newKeyNamed(contractAdminKey),
						newKeyNamed(treasuryAndFeeCollectorKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ECDSA_KEY),
						cryptoCreate(feeCollector)
								.key(treasuryAndFeeCollectorKey)
								.balance(ONE_HUNDRED_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
								.adminKey(contractAdminKey),
						tokenCreate(EXISTING_TOKEN)
								.exposingCreatedIdTo(existingToken::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT,
														"createNonFungibleTokenWithInvalidRoyaltyFee",
														asAddress(spec.registry().getContractId(TOKEN_CREATE_CONTRACT)),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(EXISTING_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.signedBy(ECDSA_KEY, treasuryAndFeeCollectorKey)
														.alsoSigningWithFullPrefix(treasuryAndFeeCollectorKey)
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
														.error(CUSTOM_FEE_MUST_BE_POSITIVE.name())))
				);
	}

	// TEST-013
	private HapiApiSpec createTokenWithInvalidTreasury() {
		return defaultHapiSpec("createTokenWithInvalidTreasury")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createNFTTokenWithKeysAndExpiry",
														Utils.asSolidityAddress(0, 0, 999_999_999L),
														spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD
												)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
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
										.status(INVALID_ACCOUNT_ID)
										.contractCallResult(
												ContractFnResultAsserts.resultWith()
														.error(INVALID_ACCOUNT_ID.name())))
				);
	}

	// TEST-014
	private HapiApiSpec createTokenWithInvalidFixedFeeWithERC721Denomination() {
		final String feeCollector = "account2";
		return defaultHapiSpec("createTokenWithInvalidFixedFeeWithERC721Denomination")
				.given(
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ECDSA_KEY),
						cryptoCreate(feeCollector)
								.balance(ONE_HUNDRED_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER),
						tokenCreate(EXISTING_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT,
														"createTokenWithAllCustomFeesAvailable",
														spec.registry().getKey(
																ECDSA_KEY).getECDSASecp256K1().toByteArray(),
														asAddress(spec.registry().getAccountID(feeCollector)),
														asAddress(spec.registry().getTokenID(EXISTING_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.refusingEthConversion()
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
														.error(CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON.name())))
				);
	}

	// TEST-015
	private HapiApiSpec createTokenWithInvalidFeeCollector() {
		return defaultHapiSpec("createTokenWithInvalidFeeCollector")
				.given(
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ECDSA_KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER),
						tokenCreate(EXISTING_TOKEN)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT,
														"createTokenWithAllCustomFeesAvailable",
														spec.registry().getKey(
																ECDSA_KEY).getECDSASecp256K1().toByteArray(),
														Utils.asSolidityAddress(0, 0, 15252L),
														asAddress(spec.registry().getTokenID(EXISTING_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														AUTO_RENEW_PERIOD)
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.refusingEthConversion()
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
														.error(INVALID_CUSTOM_FEE_COLLECTOR.name())))
				);
	}

	// TEST-018
	private HapiApiSpec createTokenWithInsufficientValueSent() {
		return defaultHapiSpec("createTokenWithInsufficientValueSent")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.key(ED25519KEY)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(TOKEN_CREATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(TOKEN_CREATE_CONTRACT)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext((spec, ignore) -> {
							final var balanceSnapshot = spec.isUsingEthCalls()
									? balanceSnapshot(ACCOUNT_BALANCE, DEFAULT_CONTRACT_SENDER)
									: balanceSnapshot(ACCOUNT_BALANCE, ACCOUNT);
							final long sentAmount = ONE_HBAR / 100;
							final var hapiContractCall =
									contractCall(TOKEN_CREATE_CONTRACT, "createNFTTokenWithKeysAndExpiry",
											asAddress(spec.registry().getAccountID(ACCOUNT)),
											spec.registry().getKey(ED25519KEY).getEd25519().toByteArray(),
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
							final var delta = spec.isUsingEthCalls()
									? GAS_TO_OFFER * HapiEthereumCall.DEFAULT_GAS_PRICE_TINYBARS
									: txnRecord.getResponseRecord().getTransactionFee();
							final var effectivePayer = spec.isUsingEthCalls() ? DEFAULT_CONTRACT_SENDER : ACCOUNT;
							var changeFromSnapshot = getAccountBalance(effectivePayer)
									.hasTinyBars(changeFromSnapshot(ACCOUNT_BALANCE, -(delta)));
							allRunFor(spec, changeFromSnapshot);
						}),
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged()
				);
	}

	// TEST-020
	private HapiApiSpec delegateCallTokenCreateFails() {
		return defaultHapiSpec("delegateCallTokenCreateFails")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "delegateCallCreate",
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
								TransactionRecordAsserts.recordWith()
										.status(INSUFFICIENT_TX_FEE)
										.contractCallResult(ContractFnResultAsserts.resultWith()
												.error(INSUFFICIENT_TX_FEE.name()))
						)
				);
	}

	private HapiApiSpec createTokenWithDefaultExpiryAndEmptyKeys() {
		final var tokenCreateContractAsKeyDelegate = "createTokenWithDefaultExpiryAndEmptyKeys";
		final var createTokenNum = new AtomicLong();
		final var ACCOUNT2 = "account2";
		final var contractAdminKey = "contractAdminKey";
		final var ACCOUNT_TO_ASSOCIATE = "account3";
		final var ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
		return defaultHapiSpec("createTokenWithDefaultExpiryAndEmptyKeys")
				.given(
						newKeyNamed(ED25519KEY).shape(ED25519),
						newKeyNamed(ECDSA_KEY).shape(SECP256K1),
						newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
						newKeyNamed(contractAdminKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.key(ED25519KEY),
						cryptoCreate(AUTO_RENEW_ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ED25519KEY),
						cryptoCreate(ACCOUNT2)
								.balance(ONE_HUNDRED_HBARS)
								.key(ECDSA_KEY),
						cryptoCreate(ACCOUNT_TO_ASSOCIATE)
								.key(ACCOUNT_TO_ASSOCIATE_KEY),
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
								.adminKey(contractAdminKey)
								.autoRenewAccountId(AUTO_RENEW_ACCOUNT)
								.signedBy(contractAdminKey, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT),
						getContractInfo(TOKEN_CREATE_CONTRACT)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(AUTO_RENEW_ACCOUNT))
								.logged()

				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createTokenWithDefaultExpiryAndEmptyKeys")
														.via(FIRST_CREATE_TXN)
														.gas(GAS_TO_OFFER)
														.sending(DEFAULT_AMOUNT_TO_SEND)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(ACCOUNT_TO_ASSOCIATE_KEY)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[]) result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(TOKEN_CREATE_CONTRACT_AS_KEY)
														.shape(CONTRACT.signedWith(TOKEN_CREATE_CONTRACT)),
												newKeyNamed(tokenCreateContractAsKeyDelegate)
														.shape(DELEGATE_CONTRACT.signedWith(TOKEN_CREATE_CONTRACT))
										)
						)
				).then(
						getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
