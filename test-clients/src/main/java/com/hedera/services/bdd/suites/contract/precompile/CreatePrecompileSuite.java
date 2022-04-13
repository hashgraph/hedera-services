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
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.*;
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
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
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
	private static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
	private static final String FIRST_CREATE_TXN = "firstCreateTxn";

	public static void main(String... args) {
		new CreatePrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
	//TODO: implement the E2E scenarios from the test plan
		return allOf(
				positiveSpecs()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				//TODO: update TokenCreateContract.sol
//				fungibleTokenCreateAndQueryAndTransferScenario5()
				fungibleTokenCreateHappyPath(),
				fungibleWithFeesTokenCreateHappyPath(),
				nonFungibleWithFeesTokenCreateHappyPath()
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
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(ACCOUNT_KEY)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createFungible",
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

	private HapiApiSpec fungibleTokenCreateAndQueryAndTransferScenario5() {
		final var ed25519Key = "ed25519key";

		final var createTokenNum = new AtomicLong();
		return defaultHapiSpec("fungibleCreateScenario5")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(ed25519Key).shape(ED25519),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(ed25519Key)
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
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createFungibleWithFees",
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
						uploadInitCode(TOKEN_CREATE_CONTRACT),
						contractCreate(TOKEN_CREATE_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(TOKEN_CREATE_CONTRACT, "createNonFungibleTokenWithCustomFees",
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
