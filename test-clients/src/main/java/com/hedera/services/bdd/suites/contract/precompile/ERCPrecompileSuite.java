package com.hedera.services.bdd.suites.contract.precompile;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.CommonUtils.hex;

public class ERCPrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);
	private static final String FUNGIBLE_TOKEN = "fungibleToken";
	private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
	private static final String MULTI_KEY = "purpose";
	private static final String ERC_20_CONTRACT_NAME = "erc20Contract";
	private static final String ERC_721_CONTRACT_NAME = "erc721Contract";
	private static final String OWNER = "owner";
	private static final String ACCOUNT = "anybody";
	private static final String RECIPIENT = "recipient";
	private static final ByteString FIRST_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));

	public static void main(String... args) {
		new ERCPrecompileSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				ERC_20(),
				ERC_721()
		);
	}

	List<HapiApiSpec> ERC_20() {
		return List.of(
				getErc20TokenName()
//				getErc20TokenSymbol()
//				getErc20TokenDecimals()
//				getErc20TotalSupply(),
//				getErc20BalanceOfAccount(),
//				transferErc20Token(),
//				transferErc20TokenFrom(),
//				erc20AllowanceReturnsFailure(),
//				erc20ApproveReturnsFailure()
		);
	}

	List<HapiApiSpec> ERC_721() {
		return List.of(
//				getErc721TokenName()
//				getErc721Symbol()
//				getErc721TokenURI()
//				getErc721OwnerOf()
//				getErc721BalanceOf(),
//				getErc721TotalSupply(),
//				getErc721TransferFrom()
		);
	}

	private HapiApiSpec getErc20TokenName() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_20_NAME")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.name(tokenName)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_NAME_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(nameTxn)
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck(nameTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.NAME)
																.withName(tokenName)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc20TokenSymbol() {
		final var tokenSymbol = "F";
		final var symbolTxn = "symbolTxn";

		return defaultHapiSpec("ERC_20_SYMBOL")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.symbol(tokenSymbol)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_SYMBOL_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(symbolTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						childRecordsCheck(symbolTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.SYMBOL)
																.withSymbol(tokenSymbol)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc20TokenDecimals() {
		final var decimals = 10;
		final var decimalsTxn = "decimalsTxn";

		return defaultHapiSpec("ERC_20_DECIMALS")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.decimals(decimals)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_DECIMALS_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(decimalsTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						childRecordsCheck(decimalsTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.DECIMALS)
																.withDecimals(decimals)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc20TotalSupply() {
		final var totalSupply = 50;
		final var supplyTxn = "supplyTxn";

		return defaultHapiSpec("ERC_20_TOTAL_SUPPLY")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(totalSupply)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TOTAL_SUPPLY_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(supplyTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						getTxnRecord(supplyTxn).andAllChildRecords().logged()
//						childRecordsCheck(supplyTxn, SUCCESS,
//								recordWith().status(SUCCESS),
//								recordWith().contractCallResult(
//										resultWith().logs(
//												inOrder(
//														logWith().longValue(totalSupply)))))
				);
	}

	private HapiApiSpec getErc20BalanceOfAccount() {
		final var balanceTxn = "balanceTxn";

		return defaultHapiSpec("ERC_20_BALANCE_OF")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_BALANCE_OF_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)))
														.payingWith(ACCOUNT)
														.via(balanceTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						getTxnRecord(balanceTxn).andAllChildRecords().logged()
//								recordWith().status(SUCCESS),
//								recordWith().contractCallResult(
//										resultWith().logs(
//												inOrder(
//														logWith().longValue(5)))))
//						childRecordsCheck(balanceTxn, SUCCESS,
				);
	}

	private HapiApiSpec transferErc20Token() {
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME),
//								.gas(300_000),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT))
				).when(withOpContext(
								(spec, opLog) -> {
									final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var sender = spec.registry().getAccountID(ACCOUNT);
									final var receiver = spec.registry().getAccountID(RECIPIENT);
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2)
														.payingWith(ACCOUNT).alsoSigningWithFullPrefix(MULTI_KEY)
														.via(transferTxn).gas(50_000_000L)
														.hasKnownStatus(SUCCESS)
										); }
						)

				).then(
						getTxnRecord(transferTxn).andAllChildRecords().logged(),
						childRecordsCheck(transferTxn, SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().contractCallResult(
										resultWith().logs(
												inOrder(
														logWith()
																.accountAtBytes(TOKEN_TREASURY, 0)
																.accountAtBytes(RECIPIENT, 32)
																.longValue(2))))),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2)
				);
	}

	private HapiApiSpec transferErc20TokenFrom() {
		final var payerKeyShape = SIMPLE;
		final var recipientKeyShape = SIMPLE;
		final var payerSigsName = "payerSigs";
		final var recipientSigsName = "recipientSigs";
		final var theRecipient = "theRecipient";
		final var transferFromTxn = "transferFromTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM")
				.given(
						newKeyNamed(MULTI_KEY),
						newKeyNamed(payerSigsName).shape(payerKeyShape),
						newKeyNamed(recipientSigsName).shape(recipientKeyShape),
						cryptoCreate(ACCOUNT).key(payerSigsName).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient).key(recipientSigsName).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(35)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(20, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(theRecipient)), 8)
														.payingWith(ACCOUNT).sigControl(
																forKey(ACCOUNT, payerKeyShape),
																forKey(theRecipient, recipientKeyShape))
														.via(transferFromTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(transferFromTxn).andAllChildRecords().logged(),
						childRecordsCheck(transferFromTxn, SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().contractCallResult(
										resultWith().logs(
												inOrder(
														logWith()
																.accountAtBytes(ACCOUNT, 0)
																.accountAtBytes(theRecipient, 32)
																.longValue(8))))),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(FUNGIBLE_TOKEN, 27),
						getAccountBalance(ACCOUNT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 12),
						getAccountBalance(theRecipient)
								.hasTokenBalance(FUNGIBLE_TOKEN, 8)
				);
	}

	private HapiApiSpec erc20AllowanceReturnsFailure() {
		final var theSpender = "spender";
		final var allowanceTxn = "allowanceTxn";

		return defaultHapiSpec("ERC_20_ALLOWANCE_RETURNS_FAILURE")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theSpender),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_ALLOWANCE_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(theSpender)))
														.payingWith(OWNER)
														.via(allowanceTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(allowanceTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec erc20ApproveReturnsFailure() {
		final var approveTxn = "approveTxn";

		return defaultHapiSpec("ERC_20_APPROVE_RETURNS_FAILURE")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_APPROVE_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)), 10)
														.payingWith(OWNER)
														.via(approveTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(approveTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc721TokenName() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_721_NAME")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.name(tokenName)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_NAME_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(nameTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						childRecordsCheck(nameTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.NAME)
																.withName(tokenName)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc721Symbol() {
		final var tokenSymbol = "N";
		final var symbolTxn = "symbolTxn";

		return defaultHapiSpec("ERC_721_SYMBOL")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.symbol(tokenSymbol)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_SYMBOL_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(symbolTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						childRecordsCheck(symbolTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.SYMBOL)
																.withSymbol(tokenSymbol)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc721TokenURI() {
		final var tokenURITxn = "tokenURITxn";

		return defaultHapiSpec("ERC_721_TOKEN_URI")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TOKEN_URI_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 1)
														.payingWith(ACCOUNT)
														.via(tokenURITxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						getTxnRecord(tokenURITxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc721TotalSupply() {
		final var totalSupplyTxn = "totalSupplyTxn";

		return defaultHapiSpec("ERC_721_TOTAL_SUPPLY")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TOTAL_SUPPLY_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(totalSupplyTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						getTxnRecord(totalSupplyTxn).andAllChildRecords().logged()
				);
	}


	private HapiApiSpec getErc721BalanceOf() {
		final var balanceOfTxn = "balanceOfTxn";

		return defaultHapiSpec("ERC_721_BALANCE_OF")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_BALANCE_OF_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)))
														.payingWith(OWNER)
														.via(balanceOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						getTxnRecord(balanceOfTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc721OwnerOf() {
		final var ownerOfTxn = "ownerOfTxn";

		return defaultHapiSpec("ERC_721_OWNER_OF")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_OWNER_OF_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 1)
														.payingWith(OWNER)
														.via(ownerOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(4000000)
										)
						)
				).then(
						getTxnRecord(ownerOfTxn).andAllChildRecords().logged()
				);
	}


	private HapiApiSpec getErc721TransferFrom() {
		final var notOwnerKeyShape = SIMPLE;
		final var notOwner = "notOwner";
		final var ownerNotAssignedToTokenTxn = "ownerNotAssignedToTokenTxn";
		final var transferFromToAccountTxn = "transferFromToAccountTxn";

		return defaultHapiSpec("ERC_721_TRANSFER_FROM")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(OWNER)
														.via(ownerNotAssignedToTokenTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(OWNER)
														.via(transferFromToAccountTxn)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(notOwner)
														.via(ownerNotAssignedToTokenTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(notOwner).sigControl(
																forKey(notOwner, notOwnerKeyShape))
														.via(ownerNotAssignedToTokenTxn)
														.hasKnownStatus(SUCCESS)
										)

						)
				).then(
						getTxnRecord(ownerNotAssignedToTokenTxn).andAllChildRecords().logged(),
						getTxnRecord(transferFromToAccountTxn).andAllChildRecords().logged()
				);
	}

	private String asSolidityAddress(final ContractID id) {
		return hex(HapiPropertySource.asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum()));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}