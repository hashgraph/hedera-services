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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_WITH_FEES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_NON_FUNGIBLE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_NON_FUNGIBLE_WITH_FEES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TOKEN_CREATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
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
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInHbarsInSchedule;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;

public class CreatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreatePrecompileSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;

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
				fungibleTokenCreateHappyPath(),
				fungibleWithFeesTokenCreateHappyPath(),
//				nonFungibleTokenCreate(),
				nonFungibleWithFeesTokenCreateHappyPath()
		);
	}

	private HapiApiSpec fungibleTokenCreateHappyPath() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var tokenCreateContractAsKey = "tokenCreateContractAsKey";
		final var firstCreateTxn = "firstCreateTxn";

		final AtomicReference<String> contractID = new AtomicReference<>();

		final AtomicLong createTokenNum = new AtomicLong();
		final String ACCOUNT = "account";
		final String accountKey = "accountKey";
		return defaultHapiSpec("fungibleCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						newKeyNamed(accountKey),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(accountKey),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
								.adminKey(accountKey)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_FUNGIBLE_ABI,
														asAddress(spec.registry().getContractId(tokenCreateContract)))
														.via(firstCreateTxn)
														.gas(GAS_TO_OFFER)
														.sending(10 * ONE_HBAR)
														.payingWith(ACCOUNT)
														.exposingResultTo(result -> {
															log.info("Explicit create result is {}", result[0]);
															final var res = (byte[])result[0];
															createTokenNum.set(new BigInteger(res).longValueExact());
														}),
												newKeyNamed(tokenCreateContractAsKey)
														.shape(CONTRACT.signedWith(tokenCreateContract))
										)
						)
				).then(
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(tokenCreateContract).logged(),
						getContractInfo(tokenCreateContract).logged(),
						childRecordsCheck(firstCreateTxn, ResponseCodeEnum.SUCCESS,
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
											.hasTreasury(tokenCreateContract)
											.hasAutoRenewAccount("0.0.2")
											.hasAutoRenewPeriod(8000000L)
											.hasSupplyType(TokenSupplyType.INFINITE)
											.searchKeysGlobally()
											.hasAdminKey(accountKey)
											.hasSupplyKey(tokenCreateContractAsKey)
											.hasPauseKey(tokenCreateContractAsKey)
											.hasPauseStatus(TokenPauseStatus.Unpaused);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec fungibleWithFeesTokenCreateHappyPath() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";

		final String ACCOUNT = "account";
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
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_FUNGIBLE_WITH_FEES_ABI,
														spec.registry().getKey(ed25519Key).getEd25519().toByteArray(),
														spec.registry().getKey(ecdsaKey).getECDSASecp256K1().toByteArray())
														.via(firstCreateTxn)
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
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(tokenCreateContract).logged(),
						getContractInfo(tokenCreateContract).logged(),
						childRecordsCheck(firstCreateTxn, ResponseCodeEnum.SUCCESS,
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
									.hasFreezeKey(ecdsaKey)
									.hasPauseStatus(TokenPauseStatus.PauseNotApplicable)
									.hasCustom(fixedHbarFeeInSchedule(4, ACCOUNT))
									.hasCustom(fractionalFeeInSchedule(5, 6, 55, OptionalLong.of(100), true, ACCOUNT));
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec nonFungibleTokenCreate() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";

		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						cryptoCreate("treasury"),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_NON_FUNGIBLE_ABI)
														.via(firstCreateTxn)
										)
						)
				).then(
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec nonFungibleWithFeesTokenCreateHappyPath() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";
		final String ACCOUNT = "account";
		final AtomicLong createdTokenNum = new AtomicLong();
		return defaultHapiSpec("nonFungibleWithFeesCreate")
				.given(
						UtilVerbs.overriding("contracts.precompile.htsEnableTokenCreate", "true"),
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_NON_FUNGIBLE_WITH_FEES_ABI,
														asAddress(spec.registry().getAccountID(ACCOUNT)))
														.via(firstCreateTxn)
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
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(tokenCreateContract).logged(),
						getContractInfo(tokenCreateContract).logged(),
						childRecordsCheck(firstCreateTxn, ResponseCodeEnum.SUCCESS,
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
									.hasTreasury(tokenCreateContract)
									.hasAutoRenewAccount(ACCOUNT)
									.hasAutoRenewPeriod(8000000L)
									.hasSupplyType(TokenSupplyType.FINITE)
									.hasCustom(fixedHbarFeeInSchedule(4, tokenCreateContract))
									.hasCustom(royaltyFeeWithFallbackInHbarsInSchedule(4, 5, 5, tokenCreateContract))
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
