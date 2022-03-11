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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
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
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERCPrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);
	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final String FUNGIBLE_TOKEN = "fungibleToken";
	private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
	private static final String MULTI_KEY = "purpose";
	private static final String ERC_20_CONTRACT_NAME = "erc20Contract";
	private static final String NESTED_ERC_20_CONTRACT_NAME = "Nestederc20Contract";
	private static final String ERC_721_CONTRACT_NAME = "erc721Contract";
	private static final String OWNER = "owner";
	private static final String ACCOUNT = "anybody";
	private static final String RECIPIENT = "recipient";
	private static final ByteString FIRST_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
	private static final ByteString SECOND_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
	private static final String TRANSFER_SIG_NAME = "transferSig";
	private static final String TRANSFER_SIGNATURE = "Transfer(address,address,uint256)";

	public static void main(String... args) {
		new ERCPrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}


	//TODO: refactor transferFrom tests and add HIP-376 tests
	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
//				ERC_20(),
				ERC_721()
		);
	}

	List<HapiApiSpec> ERC_20() {
		return List.of(
				getErc20TokenName(),
				getErc20TokenSymbol(),
				getErc20TokenDecimals(),
				getErc20TotalSupply(),
				getErc20BalanceOfAccount(),
				transferErc20Token(),
//				transferErc20TokenFrom(),
				erc20Allowance(),
				erc20Approve(),
				getErc20TokenDecimalsFromErc721TokenFails(),
				transferErc20TokenFromErc721TokenFails(),
				transferErc20TokenReceiverContract(),
				transferErc20TokenSenderAccount()
//				transferErc20TokenFromContract()
		);
	}

	List<HapiApiSpec> ERC_721() {
		return List.of(
				getErc721TokenName(),
				getErc721Symbol(),
				getErc721TokenURI(),
				getErc721OwnerOf(),
				getErc721BalanceOf(),
				getErc721TotalSupply(),
//				getErc721TransferFrom(),
				getErc721TokenURIFromErc20TokenFails(),
				getErc721OwnerOfFromErc20TokenFails(),
				erc721TokenApprove()
		);
	}

	private HapiApiSpec getErc20TokenName() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_20_NAME")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc20TokenSymbol() {
		final var tokenSymbol = "F";
		final var symbolTxn = "symbolTxn";

		return defaultHapiSpec("ERC_20_SYMBOL")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
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
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc20TokenDecimals() {
		final var decimals = 10;
		final var decimalsTxn = "decimalsTxn";

		return defaultHapiSpec("ERC_20_DECIMALS")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
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
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc20TotalSupply() {
		final var totalSupply = 50;
		final var supplyTxn = "supplyTxn";

		return defaultHapiSpec("ERC_20_TOTAL_SUPPLY")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(supplyTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(totalSupply)
														)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc20BalanceOfAccount() {
		final var balanceTxn = "balanceTxn";

		return defaultHapiSpec("ERC_20_BALANCE_OF")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(balanceTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(3)
														)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec transferErc20Token() {
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_20_CONTRACT_NAME, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT_NAME))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getContractInfo(ERC_20_CONTRACT_NAME).saveToRegistry(ERC_20_CONTRACT_NAME),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT_NAME).getContractID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var txnRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
													.logs(inOrder(logWith().withTopicsInOrder(List.of(
																	eventSignatureOf(TRANSFER_SIGNATURE),
																	parsedToByteString(sender.getContractNum()),
																	parsedToByteString(receiver.getAccountNum())
															)).longValue(2))
													)))
											.andAllChildRecords().logged();
							allRunFor(spec, txnRecord);
						}),
						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ERC_20_CONTRACT_NAME)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec transferErc20TokenReceiverContract() {
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_RECEIVER_CONTRACT")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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

						fileCreate(NESTED_ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, NESTED_ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.NESTED_ERC_20_CONTRACT)),

						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME),

						contractCreate(NESTED_ERC_20_CONTRACT_NAME)
								.bytecode(NESTED_ERC_20_CONTRACT_NAME),

						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_20_CONTRACT_NAME, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(NESTED_ERC_20_CONTRACT_NAME, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT_NAME))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(NESTED_ERC_20_CONTRACT_NAME)), 2)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getContractInfo(ERC_20_CONTRACT_NAME).saveToRegistry(ERC_20_CONTRACT_NAME),
						getContractInfo(NESTED_ERC_20_CONTRACT_NAME).saveToRegistry(NESTED_ERC_20_CONTRACT_NAME),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT_NAME).getContractID();
							final var receiver = spec.registry().getContractInfo(NESTED_ERC_20_CONTRACT_NAME).getContractID();

							var txnRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
													.logs(inOrder(logWith().withTopicsInOrder(List.of(
																	eventSignatureOf(TRANSFER_SIGNATURE),
																	parsedToByteString(sender.getContractNum()),
																	parsedToByteString(receiver.getContractNum())
															)).longValue(2))
													)))
											.andAllChildRecords().logged();
							allRunFor(spec, txnRecord);
						}),
						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ERC_20_CONTRACT_NAME)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(NESTED_ERC_20_CONTRACT_NAME)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec transferErc20TokenSenderAccount() {
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_SENDER_ACCOUNT")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_DELEGATE_TRANSFER_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getAccountInfo(ACCOUNT).savingSnapshot(ACCOUNT),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getAccountInfo(ACCOUNT).getAccountID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var txnRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
													.logs(inOrder(logWith().withTopicsInOrder(List.of(
																	eventSignatureOf(TRANSFER_SIGNATURE),
																	parsedToByteString(sender.getAccountNum()),
																	parsedToByteString(receiver.getAccountNum())
															)).longValue(2))
													)))
											.andAllChildRecords().logged();
							allRunFor(spec, txnRecord);
						}),

						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ACCOUNT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec transferErc20TokenFrom() {
		final var accountNotAssignedToTokenTxn = "accountNotAssignedToTokenTxn";
		final var transferFromAccountTxn = "transferFromAccountTxn";
		final var transferFromOtherAccountWithSignaturesTxn = "transferFromOtherAccountWithSignaturesTxn";
		final var transferWithZeroAddressesTxn = "transferWithZeroAddressesTxn";
		final var transferWithAccountWithZeroAddressTxn = "transferWithAccountWithZeroAddressTxn";
		final var transferWithRecipientWithZeroAddressTxn = "transferWithRecipientWithZeroAddressTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(35)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME,
								extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 5)
														.payingWith(GENESIS)
														.via(accountNotAssignedToTokenTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.moving(20, FUNGIBLE_TOKEN).
														between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT),
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 5)
														.payingWith(ACCOUNT)
														.via(transferFromAccountTxn)
														.hasKnownStatus(SUCCESS),
												newKeyNamed(TRANSFER_SIG_NAME).shape(
														SIMPLE.signedWith(ON)),
												cryptoUpdate(ACCOUNT).key(TRANSFER_SIG_NAME),
												cryptoUpdate(RECIPIENT).key(TRANSFER_SIG_NAME),
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 5)
														.payingWith(GENESIS).alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
														.via(transferFromOtherAccountWithSignaturesTxn)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(AccountID.parseFrom(new byte[]{})), 5)
														.payingWith(GENESIS)
														.via(transferWithZeroAddressesTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 5)
														.payingWith(GENESIS)
														.via(transferWithAccountWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(AccountID.parseFrom(new byte[]{})), 5)
														.payingWith(ACCOUNT)
														.via(transferWithRecipientWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)

						)
				).then(
						getAccountInfo(ACCOUNT).savingSnapshot(ACCOUNT),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getAccountInfo(ACCOUNT).getAccountID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var accountNotAssignedToTokenRecord =
									getTxnRecord(accountNotAssignedToTokenTxn).hasChildRecords(recordWith().status(INVALID_SIGNATURE)
									).andAllChildRecords().logged();

							var transferWithZeroAddressesRecord =
									getTxnRecord(transferWithZeroAddressesTxn).hasChildRecords(recordWith().status(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)
									).andAllChildRecords().logged();

							var transferWithAccountWithZeroAddressRecord =
									getTxnRecord(transferWithAccountWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferWithRecipientWithZeroAddressRecord =
									getTxnRecord(transferWithRecipientWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferFromAccountRecord =
									getTxnRecord(transferFromAccountTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum())
													)).longValue(5))
											))).andAllChildRecords().logged();
							var transferFromNotOwnerWithSignaturesRecord =
									getTxnRecord(transferFromOtherAccountWithSignaturesTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum())
													)).longValue(5))
											))).andAllChildRecords().logged();

							allRunFor(spec,
									accountNotAssignedToTokenRecord, transferWithZeroAddressesRecord, transferWithAccountWithZeroAddressRecord,
									transferWithRecipientWithZeroAddressRecord, transferFromAccountRecord, transferFromNotOwnerWithSignaturesRecord
							);
						}),
						childRecordsCheck(transferFromAccountTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						childRecordsCheck(transferFromOtherAccountWithSignaturesTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(FUNGIBLE_TOKEN, 15),
						getAccountBalance(ACCOUNT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10)
				);
	}

	private HapiApiSpec transferErc20TokenFromContract() {
		final var transferTxn = "transferTxn";
		final var transferFromOtherContractWithSignaturesTxn = "transferFromOtherContractWithSignaturesTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM_CONTRACT")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(35)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),

						fileCreate(NESTED_ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, NESTED_ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.NESTED_ERC_20_CONTRACT)),

						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME,
								extractByteCode(ContractResources.ERC_20_CONTRACT)),

						newKeyNamed(TRANSFER_SIG_NAME).shape(
								SIMPLE.signedWith(ON)),

						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.adminKey(TRANSFER_SIG_NAME)
								.gas(300_000),

						contractCreate(NESTED_ERC_20_CONTRACT_NAME)
								.bytecode(NESTED_ERC_20_CONTRACT_NAME)
								.adminKey(TRANSFER_SIG_NAME)


				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(ERC_20_CONTRACT_NAME, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(NESTED_ERC_20_CONTRACT_NAME, List.of(FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.moving(20, FUNGIBLE_TOKEN).
														between(TOKEN_TREASURY, ERC_20_CONTRACT_NAME)).payingWith(ACCOUNT),

												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(ERC_20_CONTRACT_NAME)),
														asAddress(spec.registry().getContractId(NESTED_ERC_20_CONTRACT_NAME)), 5)
														.via(transferTxn)
														.hasKnownStatus(SUCCESS),

												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(ERC_20_CONTRACT_NAME)),
														asAddress(spec.registry().getContractId(NESTED_ERC_20_CONTRACT_NAME)), 5)
														.payingWith(GENESIS).alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
														.via(transferFromOtherContractWithSignaturesTxn)
										)

						)
				).then(
						getContractInfo(ERC_20_CONTRACT_NAME).saveToRegistry(ERC_20_CONTRACT_NAME),
						getContractInfo(NESTED_ERC_20_CONTRACT_NAME).saveToRegistry(NESTED_ERC_20_CONTRACT_NAME),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT_NAME).getContractID();
							final var receiver = spec.registry().getContractInfo(NESTED_ERC_20_CONTRACT_NAME).getContractID();

							var transferRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getContractNum()),
															parsedToByteString(receiver.getContractNum())
													)).longValue(5))
											))).andAllChildRecords().logged();

							var transferFromOtherContractWithSignaturesTxnRecord =
									getTxnRecord(transferFromOtherContractWithSignaturesTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getContractNum()),
															parsedToByteString(receiver.getContractNum())
													)).longValue(5))
											))).andAllChildRecords().logged();

							allRunFor(spec,
									transferRecord, transferFromOtherContractWithSignaturesTxnRecord
							);
						}),

						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),

						childRecordsCheck(transferFromOtherContractWithSignaturesTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),

						getAccountBalance(ERC_20_CONTRACT_NAME)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10),
						getAccountBalance(NESTED_ERC_20_CONTRACT_NAME)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10)

				);
	}

	private HapiApiSpec erc20Allowance() {
		final var theSpender = "spender";
		final var theSpender2 = "spender2";
		final var allowanceTxn = "allowanceTxn";

		return defaultHapiSpec("ERC_20_ALLOWANCE")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER)
								.balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(theSpender),
						cryptoCreate(theSpender2),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000),
						tokenAssociate(OWNER, FUNGIBLE_TOKEN),
						cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
						cryptoApproveAllowance()
								.payingWith(DEFAULT_PAYER)
								.addTokenAllowance(OWNER, FUNGIBLE_TOKEN, theSpender, 2L)
								.via("baseApproveTxn")
								.logged()
								.signedBy(DEFAULT_PAYER, OWNER)
								.fee(ONE_HBAR),
						cryptoApproveAllowance()
								.payingWith(DEFAULT_PAYER)
								.addTokenAllowance(OWNER, FUNGIBLE_TOKEN, theSpender2, 3L)
								.via("baseApproveTxn")
								.logged()
								.signedBy(DEFAULT_PAYER, OWNER)
								.fee(ONE_HBAR)
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
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck(allowanceTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ALLOWANCE)
																.withAllowance(2)
														)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec erc20Approve() {
		final var approveTxn = "approveTxn";
		final var theSpender = "spender";

		return defaultHapiSpec("ERC_20_APPROVE")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theSpender),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME)
								.gas(300_000),
						tokenAssociate(OWNER, FUNGIBLE_TOKEN),
						tokenAssociate(theSpender, FUNGIBLE_TOKEN),
						tokenAssociate(ERC_20_CONTRACT_NAME, FUNGIBLE_TOKEN)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_APPROVE_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(theSpender)), 10)
														.payingWith(OWNER)
														.gas(500_000L)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_APPROVE_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(theSpender)), 10)
														.payingWith(OWNER)
														.gas(500_000L)
														.via(approveTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(approveTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc20TokenDecimalsFromErc721TokenFails() {
		final var invalidDecimalsTxn = "decimalsFromErc721Txn";

		return defaultHapiSpec("ERC_20_DECIMALS_FROM_ERC_721_TOKEN")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
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
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))
														.payingWith(ACCOUNT)
														.via(invalidDecimalsTxn)
														.hasKnownStatus(FAIL_INVALID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidDecimalsTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec transferErc20TokenFromErc721TokenFails() {
		final var invalidTransferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM_ERC_721_TOKEN")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						fileCreate(ERC_20_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_20_CONTRACT_NAME, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(ERC_20_CONTRACT_NAME)
								.bytecode(ERC_20_CONTRACT_NAME),
						tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1).
								between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT_NAME,
														ContractResources.ERC_20_TRANSFER_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2)
														.payingWith(ACCOUNT).alsoSigningWithFullPrefix(MULTI_KEY)
														.via(invalidTransferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(FAIL_INVALID)
										)
						)
				).then(
						getTxnRecord(invalidTransferTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721TokenName() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_721_NAME")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
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
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721Symbol() {
		final var tokenSymbol = "N";
		final var symbolTxn = "symbolTxn";

		return defaultHapiSpec("ERC_721_SYMBOL")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
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
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721TokenURI() {
		final var tokenURITxn = "tokenURITxn";

		return defaultHapiSpec("ERC_721_TOKEN_URI")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(tokenURITxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.TOKEN_URI)
																.withTokenUri("FIRST")
														)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721TotalSupply() {
		final var totalSupplyTxn = "totalSupplyTxn";

		return defaultHapiSpec("ERC_721_TOTAL_SUPPLY")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(totalSupplyTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(1)
														)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}


	private HapiApiSpec getErc721BalanceOf() {
		final var balanceOfTxn = "balanceOfTxn";

		return defaultHapiSpec("ERC_721_BALANCE_OF")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(balanceOfTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(1)
														)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721OwnerOf() {
		final var ownerOfTxn = "ownerOfTxn";

		return defaultHapiSpec("ERC_721_OWNER_OF")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
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
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												childRecordsCheck(ownerOfTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.OWNER)
																						.withOwner(asAddress(spec.registry().getAccountID(OWNER)))
																				)
																)
												)
										)
						),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721TransferFrom() {
		final var ownerNotAssignedToTokenTxn = "ownerNotAssignedToTokenTxn";
		final var transferFromOwnerTxn = "transferFromToAccountTxn";
		final var transferFromNotOwnerWithSignaturesTxn = "transferFromNotOwnerWithSignaturesTxn";
		final var transferWithZeroAddressesTxn = "transferWithZeroAddressesTxn";
		final var transferWithOwnerWithZeroAddressTxn = "transferWithOwnerWithZeroAddressTxn";
		final var transferWithRecipientWithZeroAddressTxn = "transferWithRecipientWithZeroAddressTxn";

		return defaultHapiSpec("ERC_721_TRANSFER_FROM")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(10 * ONE_MILLION_HBARS),
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(GENESIS)
														.via(ownerNotAssignedToTokenTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1, 2).
														between(TOKEN_TREASURY, OWNER)).payingWith(OWNER),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(OWNER)
														.via(transferFromOwnerTxn)
														.hasKnownStatus(SUCCESS),
												newKeyNamed(TRANSFER_SIG_NAME).shape(
														SIMPLE.signedWith(ON)),
												cryptoUpdate(OWNER).key(TRANSFER_SIG_NAME),
												cryptoUpdate(RECIPIENT).key(TRANSFER_SIG_NAME),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2)
														.payingWith(GENESIS).alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
														.via(transferFromNotOwnerWithSignaturesTxn)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(AccountID.parseFrom(new byte[]{})), 1)
														.payingWith(GENESIS)
														.via(transferWithZeroAddressesTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 1)
														.payingWith(GENESIS)
														.via(transferWithOwnerWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(AccountID.parseFrom(new byte[]{})), 1)
														.payingWith(OWNER)
														.via(transferWithRecipientWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)

						)
				).then(
						getAccountInfo(OWNER).savingSnapshot(OWNER),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getAccountInfo(OWNER).getAccountID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var ownerNotAssignedToTokenRecord =
									getTxnRecord(ownerNotAssignedToTokenTxn).hasChildRecords(recordWith().status(INVALID_SIGNATURE)
									).andAllChildRecords().logged();

							var transferWithZeroAddressesRecord =
									getTxnRecord(transferWithZeroAddressesTxn).hasChildRecords(recordWith().status(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)
									).andAllChildRecords().logged();

							var transferWithOwnerWithZeroAddressRecord =
									getTxnRecord(transferWithOwnerWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferWithRecipientWithZeroAddressRecord =
									getTxnRecord(transferWithRecipientWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferFromOwnerRecord =
									getTxnRecord(transferFromOwnerTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum()),
															parsedToByteString(1)
													)))
											))).andAllChildRecords().logged();
							var transferFromNotOwnerWithSignaturesRecord =
									getTxnRecord(transferFromNotOwnerWithSignaturesTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum()),
															parsedToByteString(2)
													)))
											))).andAllChildRecords().logged();

							allRunFor(spec,
									ownerNotAssignedToTokenRecord,
									transferWithZeroAddressesRecord,
									transferWithOwnerWithZeroAddressRecord,
									transferWithRecipientWithZeroAddressRecord,
									transferFromOwnerRecord,
									transferFromNotOwnerWithSignaturesRecord
							);
						}),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
						getAccountBalance(OWNER)
								.hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(NON_FUNGIBLE_TOKEN, 2)
				);
	}

	private HapiApiSpec getErc721TokenURIFromErc20TokenFails() {
		final var invalidTokenURITxn = "tokenURITxnFromErc20";

		return defaultHapiSpec("ERC_721_TOKEN_URI_FROM_ERC_20_TOKEN")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(10)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(ACCOUNT, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_TOKEN_URI_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)), 1)
														.payingWith(ACCOUNT)
														.via(invalidTokenURITxn)
														.hasKnownStatus(FAIL_INVALID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidTokenURITxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721OwnerOfFromErc20TokenFails() {
		final var invalidOwnerOfTxn = "ownerOfTxnFromErc20Token";

		return defaultHapiSpec("ERC_721_OWNER_OF_FROM_ERC_20_TOKEN")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(10)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_721_CONTRACT_NAME),
						updateLargeFile(OWNER, ERC_721_CONTRACT_NAME, extractByteCode(ContractResources.ERC_721_CONTRACT)),
						contractCreate(ERC_721_CONTRACT_NAME)
								.bytecode(ERC_721_CONTRACT_NAME)
								.gas(300_000),
						tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_OWNER_OF_CALL,
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)), 1)
														.payingWith(OWNER)
														.via(invalidOwnerOfTxn)
														.hasKnownStatus(FAIL_INVALID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidOwnerOfTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec erc721TokenApprove() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_721_APPROVE")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_721_CONTRACT_NAME, List.of(NON_FUNGIBLE_TOKEN))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT_NAME,
														ContractResources.ERC_721_APPROVE_CALL,
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														1)
														.payingWith(ACCOUNT)
														.via(nameTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(nameTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}