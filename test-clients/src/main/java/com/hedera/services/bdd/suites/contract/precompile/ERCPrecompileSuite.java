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
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERCPrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);
	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final String FUNGIBLE_TOKEN = "fungibleToken";
	private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
	private static final String MULTI_KEY = "purpose";
	private static final String OWNER = "owner";
	private static final String ACCOUNT = "anybody";
	private static final String RECIPIENT = "recipient";
	private static final ByteString FIRST_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
	private static final String TRANSFER_SIGNATURE = "Transfer(address,address,uint256)";
	private static final String ERC_20_CONTRACT = "ERC20Contract";
	private static final String ERC_721_CONTRACT = "ERC721Contract";

	public static void main(String... args) {
		new ERCPrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				ERC_20(),
				ERC_721()
		);
	}

	List<HapiApiSpec> ERC_20() {
		return List.of(new HapiApiSpec[]{
				getErc20TokenName(),
				getErc20TokenSymbol(),
				getErc20TokenDecimals(),
				getErc20TotalSupply(),
				getErc20BalanceOfAccount(),
				transferErc20Token(),
				erc20AllowanceReturnsFails(),
				erc20ApproveReturnsFails(),
				getErc20TokenDecimalsFromErc721TokenFails(),
				transferErc20TokenFromErc721TokenFails(),
				transferErc20TokenReceiverContract(),
				transferErc20TokenSenderAccount(),
				transferErc20TokenAliasedSender(),
				directCallsWorkForERC20()
		});
	}

	List<HapiApiSpec> ERC_721() {
		return List.of(new HapiApiSpec[]{
				getErc721TokenName(),
				getErc721Symbol(),
				getErc721TokenURI(),
				getErc721OwnerOf(),
				getErc721BalanceOf(),
				getErc721TotalSupply(),
				getErc721TokenURIFromErc20TokenFails(),
				getErc721OwnerOfFromErc20TokenFails(),
				directCallsWorkForERC721()
		});
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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "name", asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))
												)
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
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) -> {
									tokenAddr.set(asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)));
									allRunFor(
											spec,
											contractCall(ERC_20_CONTRACT, "symbol", tokenAddr.get())
													.payingWith(ACCOUNT)
													.via(symbolTxn)
													.hasKnownStatus(SUCCESS)
													.gas(GAS_TO_OFFER)
									);
								}
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
						sourcing(() -> contractCallLocal(ERC_20_CONTRACT, "symbol", tokenAddr.get()))
				);
	}

	private HapiApiSpec getErc20TokenDecimals() {
		final var decimals = 10;
		final var decimalsTxn = "decimalsTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) -> {
									tokenAddr.set(asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)));
									allRunFor(
											spec,
											contractCall(ERC_20_CONTRACT, "decimals", tokenAddr.get())
													.payingWith(ACCOUNT)
													.via(decimalsTxn)
													.hasKnownStatus(SUCCESS)
													.gas(GAS_TO_OFFER)
									);
								}
						)
				).then(
						childRecordsCheck(decimalsTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.DECIMALS)
																.withDecimals(decimals)))),
						sourcing(() -> contractCallLocal(ERC_20_CONTRACT, "decimals", tokenAddr.get()))
				);
	}

	private HapiApiSpec getErc20TotalSupply() {
		final var totalSupply = 50;
		final var supplyTxn = "supplyTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) -> {
									tokenAddr.set(asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)));
									allRunFor(
											spec,
											contractCall(ERC_20_CONTRACT, "totalSupply", tokenAddr.get())
													.payingWith(ACCOUNT)
													.via(supplyTxn)
													.hasKnownStatus(SUCCESS)
													.gas(GAS_TO_OFFER)
									);
								}
						)
				).then(
						childRecordsCheck(supplyTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(totalSupply)
														)
										)
						),
						sourcing(() -> contractCallLocal(ERC_20_CONTRACT, "decimals", tokenAddr.get()))
				);
	}

	private HapiApiSpec getErc20BalanceOfAccount() {
		final var balanceTxn = "balanceTxn";
		final var zeroBalanceTxn = "zBalanceTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();
		final AtomicReference<byte[]> accountAddr = new AtomicReference<>();

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
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) -> {
									tokenAddr.set(asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)));
									accountAddr.set(asAddress(spec.registry().getAccountID(ACCOUNT)));
									allRunFor(
											spec,
											contractCall(ERC_20_CONTRACT, "balanceOf",
													tokenAddr.get(), accountAddr.get())
													.payingWith(ACCOUNT)
													.via(balanceTxn)
													.hasKnownStatus(SUCCESS)
													.gas(GAS_TO_OFFER)
									);
								}
						)
				).then(
						/* expect 0 returned from balanceOf() if the account and token are not associated -*/
						childRecordsCheck(zeroBalanceTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(0)
														)
										)
						),
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
						sourcing(() -> contractCallLocal(
								ERC_20_CONTRACT, "balanceOf",
								tokenAddr.get(), accountAddr.get()))
				);
	}

	private HapiApiSpec transferErc20Token() {
		final var transferTxn = "transferTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();
		final AtomicReference<byte[]> accountAddr = new AtomicReference<>();

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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT))
				).when(withOpContext(
								(spec, opLog) -> {
									tokenAddr.set(asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)));
									accountAddr.set(asAddress(spec.registry().getAccountID(RECIPIENT)));
									allRunFor(
											spec,
											contractCall(ERC_20_CONTRACT, "transfer",
													tokenAddr.get(), accountAddr.get(), 2)
													.via(transferTxn).gas(GAS_TO_OFFER)
													.hasKnownStatus(SUCCESS)
									);
								}
						)
				).then(
						getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT).getContractID();
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
																.forFunction(
																		HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ERC_20_CONTRACT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						sourcing(() -> contractCallLocal(
								ERC_20_CONTRACT, "transfer",
										tokenAddr.get(), accountAddr.get(), 1
								).hasAnswerOnlyPrecheck(NOT_SUPPORTED)
						)
				);
	}

	private HapiApiSpec transferErc20TokenReceiverContract() {
		final var transferTxn = "transferTxn";
		final var nestedContract = "NestedERC20Contract";

		return defaultHapiSpec("ERC_20_TRANSFER_RECEIVER_CONTRACT")
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
						uploadInitCode(ERC_20_CONTRACT, nestedContract),
						contractCreate(ERC_20_CONTRACT),
						contractCreate(nestedContract),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(nestedContract, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "transfer",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(nestedContract)), 2
												)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
						getContractInfo(nestedContract).saveToRegistry(nestedContract),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT).getContractID();
							final var receiver = spec.registry().getContractInfo(nestedContract).getContractID();

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
																.forFunction(
																		HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ERC_20_CONTRACT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(nestedContract)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec transferErc20TokenSenderAccount() {
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_SENDER_ACCOUNT")
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
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "delegateTransfer",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2
												)
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
																.forFunction(
																		HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ACCOUNT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2)
				);
	}

	private HapiApiSpec transferErc20TokenAliasedSender() {
		final var aliasedTransferTxn = "aliasedTransferTxn";
		final var addLiquidityTxn = "addLiquidityTxn";
		final var create2Txn = "create2Txn";

		final var ACCOUNT_A = "AccountA";
		final var ACCOUNT_B = "AccountB";
		final var TOKEN_A = "TokenA";

		final var ALIASED_TRANSFER = "AliasedTransfer";
		final byte[][] ALIASED_ADDRESS = new byte[1][1];

		final AtomicReference<String> childMirror = new AtomicReference<>();
		final AtomicReference<String> childEip1014 = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_TRANSFER_ALIASED_SENDER")
				.given(
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "false"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER),
						cryptoCreate(ACCOUNT),
						cryptoCreate(ACCOUNT_A).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
						cryptoCreate(ACCOUNT_B).balance(ONE_MILLION_HBARS),
						tokenCreate("TokenA")
								.adminKey(MULTI_KEY)
								.initialSupply(10000)
								.treasury(ACCOUNT_A),
						tokenAssociate(ACCOUNT_B, TOKEN_A),
						uploadInitCode(ALIASED_TRANSFER),
						contractCreate(ALIASED_TRANSFER)
								.gas(300_000),
						withOpContext(
								(spec, opLog) -> allRunFor(
										spec,
										contractCall(ALIASED_TRANSFER,
												"deployWithCREATE2",
												asAddress(spec.registry().getTokenID(TOKEN_A)))
												.exposingResultTo(result -> {
													final var res = (byte[]) result[0];
													ALIASED_ADDRESS[0] = res;
												})
												.payingWith(ACCOUNT)
												.alsoSigningWithFullPrefix(MULTI_KEY)
												.via(create2Txn).gas(GAS_TO_OFFER)
												.hasKnownStatus(SUCCESS)
								)
						)
				).when(
						captureChildCreate2MetaFor(
								2, 0,
								"setup", create2Txn, childMirror, childEip1014),
						withOpContext(
								(spec, opLog) -> allRunFor(
										spec,
										contractCall(ALIASED_TRANSFER,
												"giveTokensToOperator",
												asAddress(spec.registry().getTokenID(TOKEN_A)),
												asAddress(spec.registry().getAccountID(ACCOUNT_A)),
												1500)
												.payingWith(ACCOUNT)
												.alsoSigningWithFullPrefix(MULTI_KEY)
												.via(addLiquidityTxn).gas(GAS_TO_OFFER)
												.hasKnownStatus(SUCCESS)
								)
						),
						withOpContext(
								(spec, opLog) -> allRunFor(
										spec,
										contractCall(ALIASED_TRANSFER,
												"transfer",
												asAddress(spec.registry().getAccountID(ACCOUNT_B)),
												1000)
												.payingWith(ACCOUNT)
												.alsoSigningWithFullPrefix(MULTI_KEY)
												.via(aliasedTransferTxn).gas(GAS_TO_OFFER)
												.hasKnownStatus(SUCCESS)
								))
				).then(
						sourcing(
								() -> getContractInfo(asContractString(
										contractIdFromHexedMirrorAddress(childMirror.get())))
										.hasToken(ExpectedTokenRel.relationshipWith(TOKEN_A).balance(500))
										.logged()
						),
						getAccountBalance(ACCOUNT_B).hasTokenBalance(TOKEN_A, 1000),
						getAccountBalance(ACCOUNT_A).hasTokenBalance(TOKEN_A, 8500)
				);
	}

	private HapiApiSpec erc20AllowanceReturnsFails() {
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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "allowance",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(theSpender))
												)
														.payingWith(OWNER)
														.via(allowanceTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(allowanceTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec erc20ApproveReturnsFails() {
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
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "approve",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)), 10
												)
														.payingWith(OWNER)
														.via(approveTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(approveTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc20TokenDecimalsFromErc721TokenFails() {
		final var invalidDecimalsTxn = "decimalsFromErc721Txn";

		return defaultHapiSpec("ERC_20_DECIMALS_FROM_ERC_721_TOKEN")
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "decimals", asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(invalidDecimalsTxn)
														.hasKnownStatus(INVALID_TOKEN_ID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidDecimalsTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec transferErc20TokenFromErc721TokenFails() {
		final var invalidTransferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM_ERC_721_TOKEN")
				.given(
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
						tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).
								between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "transfer",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2
												)
														.payingWith(ACCOUNT).alsoSigningWithFullPrefix(MULTI_KEY)
														.via(invalidTransferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(INVALID_TOKEN_ID)
										)
						)
				).then(
						getTxnRecord(invalidTransferTxn).andAllChildRecords().logged()
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "name",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "symbol",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
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
						)
				);
	}

	private HapiApiSpec getErc721TokenURI() {
		final var tokenURITxn = "tokenURITxn";
		final var nonExistingTokenURITxn = "nonExistingTokenURITxn";
		final var ERC721MetadataNonExistingToken = "ERC721Metadata: URI query for nonexistent token";

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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "tokenURI",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 1
												)
														.payingWith(ACCOUNT)
														.via(tokenURITxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER),
												contractCall(ERC_721_CONTRACT,
														"tokenURI",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 2)
														.payingWith(ACCOUNT)
														.via(nonExistingTokenURITxn)
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
						childRecordsCheck(nonExistingTokenURITxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.TOKEN_URI)
																.withTokenUri(ERC721MetadataNonExistingToken)
														)
										)
						)
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "totalSupply",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
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
																.forFunction(
																		HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(1)
														)
										)
						)
				);
	}


	private HapiApiSpec getErc721BalanceOf() {
		final var balanceOfTxn = "balanceOfTxn";
		final var zeroBalanceOfTxn = "zbalanceOfTxn";

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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "balanceOf",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER))
												)
														.payingWith(OWNER)
														.via(zeroBalanceOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER),
												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
												cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1)
														.between(TOKEN_TREASURY, OWNER)),
												contractCall(ERC_721_CONTRACT,
														"balanceOf",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)))
														.payingWith(OWNER)
														.via(balanceOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						/* expect 0 returned from balanceOf() if the account and token are not associated -*/
						childRecordsCheck(zeroBalanceOfTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(0)
														)
										)
						),
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
						)
				);
	}

	private HapiApiSpec getErc721OwnerOf() {
		final var ownerOfTxn = "ownerOfTxn";
		final AtomicReference<byte[]> ownerAddr = new AtomicReference<>();
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

		HapiApiSpec then = defaultHapiSpec("ERC_721_OWNER_OF")
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
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) -> {
									tokenAddr.set(asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)));
									allRunFor(
											spec,
											contractCall(ERC_721_CONTRACT, "ownerOf",
													tokenAddr.get(), 1)
													.payingWith(OWNER)
													.via(ownerOfTxn)
													.hasKnownStatus(SUCCESS)
													.gas(GAS_TO_OFFER)
									);
								}
						)
				).then(
						withOpContext(
								(spec, opLog) -> {
									ownerAddr.set(asAddress(spec.registry().getAccountID(OWNER)));
									allRunFor(
											spec,
											childRecordsCheck(ownerOfTxn, SUCCESS,
													recordWith()
															.status(SUCCESS)
															.contractCallResult(
																	resultWith()
																			.contractCallResult(htsPrecompileResult()
																					.forFunction(
																							HTSPrecompileResult.FunctionType.OWNER)
																					.withOwner(ownerAddr.get())
																			)
															)
											)
									);
								}
						),
						sourcing(() ->
								contractCallLocal(
										ERC_721_CONTRACT, "ownerOf", tokenAddr.get(), 1
								)
										.payingWith(OWNER)
										.gas(GAS_TO_OFFER))
				);
		return then;
	}

	private HapiApiSpec getErc721TokenURIFromErc20TokenFails() {
		final var invalidTokenURITxn = "tokenURITxnFromErc20";

		return defaultHapiSpec("ERC_721_TOKEN_URI_FROM_ERC_20_TOKEN")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(10)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "tokenURI",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)), 1
												)
														.payingWith(ACCOUNT)
														.via(invalidTokenURITxn)
														.hasKnownStatus(INVALID_TOKEN_ID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidTokenURITxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc721OwnerOfFromErc20TokenFails() {
		final var invalidOwnerOfTxn = "ownerOfTxnFromErc20Token";

		return defaultHapiSpec("ERC_721_OWNER_OF_FROM_ERC_20_TOKEN")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(10)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "ownerOf",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)), 1
												)
														.payingWith(OWNER)
														.via(invalidOwnerOfTxn)
														.hasKnownStatus(INVALID_TOKEN_ID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidOwnerOfTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec directCallsWorkForERC20() {

		final AtomicReference<String> tokenNum = new AtomicReference<>();

		final var tokenName = "TokenA";
		final var tokenSymbol = "FDFGF";
		final var tokenDecimals = 10;
		final var tokenTotalSupply = 5;
		final var tokenTransferAmount = 3;

		final var symbolTxn = "symbolTxn";
		final var nameTxn = "nameTxn";
		final var decimalsTxn = "decimalsTxn";
		final var totalSupplyTxn = "totalSupplyTxn";
		final var balanceOfTxn = "balanceOfTxn";
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("DirectCallsWorkForERC20")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(tokenTotalSupply)
								.name(tokenName)
								.symbol(tokenSymbol)
								.decimals(tokenDecimals)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(tokenNum::set),
						tokenAssociate(ACCOUNT, FUNGIBLE_TOKEN),
						tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN),
						cryptoTransfer(moving(tokenTransferAmount, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT))
				).when(withOpContext(
						(spec, ignore) -> {
							var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
							allRunFor(spec,
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "name", "ERC20ABI")
									).via(nameTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "symbol", "ERC20ABI")
									).via(symbolTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "decimals", "ERC20ABI")
									).via(decimalsTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "totalSupply", "ERC20ABI")
									).via(totalSupplyTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "balanceOf", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT))
									).via(balanceOfTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "transfer", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											tokenTransferAmount
									).via(transferTxn).payingWith(ACCOUNT)
							);
						})
				).then(
						withOpContext(
								(spec, ignore) ->
										allRunFor(spec,
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
												childRecordsCheck(decimalsTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.DECIMALS)
																						.withDecimals(tokenDecimals)
																				)
																)
												),
												childRecordsCheck(totalSupplyTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																						.withTotalSupply(tokenTotalSupply)
																				)
																)
												),
												childRecordsCheck(balanceOfTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																						.withBalance(tokenTransferAmount)
																				)
																)
												),
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
												)))
				);
	}

	private HapiApiSpec directCallsWorkForERC721() {

		final AtomicReference<String> tokenNum = new AtomicReference<>();

		final var tokenName = "TokenA";
		final var tokenSymbol = "FDFDFD";
		final var tokenTotalSupply = 1;

		final var symbolTxn = "symbolTxn";
		final var nameTxn = "nameTxn";
		final var tokenURITxn = "tokenURITxn";
		final var totalSupplyTxn = "totalSupplyTxn";
		final var balanceOfTxn = "balanceOfTxn";
		final var ownerOfTxn = "ownerOfTxn";

		return defaultHapiSpec("DirectCallsWorkForERC721")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.name(tokenName)
								.symbol(tokenSymbol)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(tokenNum::set),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(ACCOUNT, NON_FUNGIBLE_TOKEN),
						tokenAssociate(RECIPIENT, NON_FUNGIBLE_TOKEN),
						cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY,
								ACCOUNT))
				).when(withOpContext(
						(spec, ignore) -> {
							var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
							allRunFor(spec,
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "name", "ERC721ABI")
									).via(nameTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "symbol", "ERC721ABI")
									).via(symbolTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "tokenURI", "ERC721ABI"),
											1
									).via(tokenURITxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "totalSupply", "ERC721ABI")
									).via(totalSupplyTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "balanceOf", "ERC721ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT))
									).via(balanceOfTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(FunctionType.FUNCTION, "ownerOf", "ERC721ABI"),
											1
									).via(ownerOfTxn)
							);
						})
				).then(
						withOpContext(
								(spec, ignore) ->
										allRunFor(spec,
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
												childRecordsCheck(totalSupplyTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																						.withTotalSupply(tokenTotalSupply)
																				)
																)
												),
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
												childRecordsCheck(ownerOfTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.OWNER)
																						.withOwner(asAddress(spec.registry().getAccountID(ACCOUNT)))
																				)
																)
												)))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}