package com.hedera.services.bdd.suites.contract.precompile;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERCDirectTokenCallSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCDirectTokenCallSuite.class);
	private static final String FUNGIBLE_TOKEN = "fungibleToken";
	private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
	private static final String MULTI_KEY = "purpose";
	private static final String ACCOUNT = "anybody";
	private static final String RECIPIENT = "recipient";
	private static final ByteString FIRST_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));

	public static void main(String... args) {
		new ERCDirectTokenCallSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				directCallsWorkForERC20(),
				directCallsWorkForERC721(),
				erc20DirectTokenCallsWithEOA(),
				erc721DirectTokenCallsWithEOA(),
				negativeTransferForDirectCallsERC20(),
				negativeTransferForDirectCallsERC721(),
		});
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
		final var approveTxn = "approveTxn";

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
											getABIFor(Utils.FunctionType.FUNCTION, "name", "ERC20ABI")
									).via(nameTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "symbol", "ERC20ABI")
									).via(symbolTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "decimals", "ERC20ABI")
									).via(decimalsTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", "ERC20ABI")
									).via(totalSupplyTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "balanceOf", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT))
									).via(balanceOfTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transfer", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											tokenTransferAmount
									).via(transferTxn).payingWith(ACCOUNT),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "approve", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											1
									).gas(10000000).via(approveTxn).payingWith(ACCOUNT),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "allowance", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT)),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT))
									).via("allowanceTxn").payingWith(ACCOUNT).logged()
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.NAME)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.SYMBOL)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.DECIMALS)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																						.withTotalSupply(
																								tokenTotalSupply)
																				)
																)
												),
												childRecordsCheck(balanceOfTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(
																								HTSPrecompileResult.FunctionType.BALANCE)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																						.withErcFungibleTransferStatus(
																								true)
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
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY,
								ACCOUNT))
				).when(withOpContext(
						(spec, ignore) -> {
							var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
							allRunFor(spec,
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "name", "ERC721ABI")
									).via(nameTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "symbol", "ERC721ABI")
									).via(symbolTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "tokenURI", "ERC721ABI"),
											1
									).via(tokenURITxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", "ERC721ABI")
									).via(totalSupplyTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "balanceOf", "ERC721ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT))
									).via(balanceOfTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "ownerOf", "ERC721ABI"),
											1
									).via(ownerOfTxn),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transferFrom", "ERC721ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT)),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											1
									).via("transferFrom").payingWith(ACCOUNT)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.NAME)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.SYMBOL)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.TOKEN_URI)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																						.withTotalSupply(
																								tokenTotalSupply)
																				)
																)
												),
												childRecordsCheck(balanceOfTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(
																								HTSPrecompileResult.FunctionType.BALANCE)
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
																						.forFunction(
																								HTSPrecompileResult.FunctionType.OWNER)
																						.withOwner(asAddress(
																								spec.registry().getAccountID(
																										ACCOUNT)))
																				)
																)
												)
										))
				);
	}

	private HapiApiSpec negativeTransferForDirectCallsERC20() {
		final AtomicReference<String> tokenNum = new AtomicReference<>();

		final var tokenName = "DirectToken";
		final var tokenSymbol = "DTK";
		final var tokenDecimals = 10;
		final var tokenTotalSupply = 5;
		final var tokenTransferAmount = 3;
		final var NEGATIVE_ACCOUNT = "NEGATIVE_ACCOUNT";
		final var EMPTY_BALANCE_ACCOUNT = "EMPTY_BALANCE_ACCOUNT";

		return defaultHapiSpec("negativeTransferForDirectCallsERC20")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(EMPTY_BALANCE_ACCOUNT),
						cryptoCreate(NEGATIVE_ACCOUNT),
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
						tokenAssociate(EMPTY_BALANCE_ACCOUNT, FUNGIBLE_TOKEN),
						cryptoTransfer(moving(tokenTransferAmount, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT))
				).when(withOpContext(
						(spec, ignore) -> {
							var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
							allRunFor(spec,
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transfer", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											0
									).via("TRANSFER_ZERO_AMOUNT").payingWith(ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transfer", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											100
									).via("TRANSFER_WITH_INSUFFICIENT_BALANCE").payingWith(ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transferFrom", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(NEGATIVE_ACCOUNT)),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											tokenTransferAmount
									).via("TOKEN_NOT_ASSOCIATED_TO_ACCOUNT").payingWith(ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transfer", "ERC20ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											100
									).via("TRANSFER_SENDER_WITH_INSUFFICIENT_BALANCE").payingWith(EMPTY_BALANCE_ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
							);
						})
				).then(getAccountBalance(ACCOUNT).payingWith(ACCOUNT).hasTokenBalance(FUNGIBLE_TOKEN, tokenTransferAmount),
						childRecordsCheck("TRANSFER_ZERO_AMOUNT", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_ACCOUNT_AMOUNTS)),
						childRecordsCheck("TRANSFER_WITH_INSUFFICIENT_BALANCE", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INSUFFICIENT_TOKEN_BALANCE)),
						childRecordsCheck("TOKEN_NOT_ASSOCIATED_TO_ACCOUNT", CONTRACT_REVERT_EXECUTED,
								recordWith().status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)),
						childRecordsCheck("TRANSFER_SENDER_WITH_INSUFFICIENT_BALANCE", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INSUFFICIENT_TOKEN_BALANCE))
				);
	}

	private HapiApiSpec negativeTransferForDirectCallsERC721() {
		final AtomicReference<String> tokenNum = new AtomicReference<>();
		final AtomicReference<String> zCivilianMirrorAddr = new AtomicReference<>();
		final var tokenName = "DirectToken";
		final var tokenSymbol = "DTK";

		return defaultHapiSpec("negativeTransferForDirectCallsERC721")
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
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY,
								ACCOUNT))
				).when(withOpContext(
						(spec, ignore) -> {
							var tokenAddress = asHexedSolidityAddress(asToken(tokenNum.get()));
							zCivilianMirrorAddr.set(asHexedSolidityAddress(
									AccountID.newBuilder().setAccountNum(666_666_666L).build()));
							allRunFor(spec,
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transferFrom", "ERC721ABI"),
											zCivilianMirrorAddr.get(),
											asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)),
											1
									).via("MISSING_FROM").payingWith(ACCOUNT).hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transferFrom", "ERC721ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT)),
											zCivilianMirrorAddr.get(),
											1
									).via("MISSING_TO").payingWith(ACCOUNT).hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCallWithFunctionAbi(
											tokenAddress,
											getABIFor(Utils.FunctionType.FUNCTION, "transferFrom", "ERC721ABI"),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT)),
											asHexedSolidityAddress(spec.registry().getAccountID(ACCOUNT)),
											0
									).via("SERIAL_NOT_OWNED_BY_FROM").payingWith(ACCOUNT)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
							);
						})
				).then(
						childRecordsCheck("MISSING_FROM", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_ACCOUNT_ID)),
						childRecordsCheck("MISSING_TO", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_ACCOUNT_ID)),
						childRecordsCheck("SERIAL_NOT_OWNED_BY_FROM", CONTRACT_REVERT_EXECUTED,
								recordWith().status(INVALID_TOKEN_NFT_SERIAL_NUMBER)));
	}

	private HapiApiSpec erc20DirectTokenCallsWithEOA() {
		final var creation = "autoCreation";
		final var spender = "spender";
		final var eoaSender = "eoaSender";
		final var ecdsaKey = "ecdsaKey";
		final var SYMBOL = "ħT";
		final var approveTxn = "approveTxn";
		final var nameTxn = "nameTxn";
		final var symbolTxn = "symbolTxn";
		final var decimalsTxn = "decimalsTxn";
		final var totalSupplyTxn = "totalSupplyTxn";
		final var balanceOfTxn = "balanceOfTxn";
		final var transferTxn = "transferTxn";
		final AtomicReference<String> spenderMirrorAddr = new AtomicReference<>();

		return defaultHapiSpec("erc20DirectTokenCallsWithEOA")
				.given(
						cryptoCreate(spender).exposingCreatedIdTo(id ->
								spenderMirrorAddr.set(asHexedSolidityAddress(id))),
						newKeyNamed(ecdsaKey).shape(SECP256K1_ON),
						cryptoTransfer(
								tinyBarsFromToWithAlias(DEFAULT_PAYER, ecdsaKey, ONE_HUNDRED_HBARS)
						).via(creation),
						getReceipt(creation).savingAutoCreation(eoaSender, ecdsaKey),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
						tokenCreate(FUNGIBLE_TOKEN)
								.treasury(eoaSender)
								.initialSupply(1000)
								.decimals(13)
								.symbol(SYMBOL)
								.asCallableContract(),
						tokenAssociate(RECIPIENT, FUNGIBLE_TOKEN)
				).when(withOpContext(
						(spec, ignore) -> allRunFor(spec,
								contractCallWithFunctionAbi(FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "name", "ERC20ABI"))
										.payingWith(eoaSender)
										.via(nameTxn),
								contractCallWithFunctionAbi(FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "symbol", "ERC20ABI"))
										.payingWith(eoaSender)
										.via(symbolTxn),
								contractCallWithFunctionAbi(FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "decimals", "ERC20ABI"))
										.payingWith(eoaSender)
										.via(decimalsTxn),
								contractCallWithFunctionAbi(FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", "ERC20ABI"))
										.payingWith(eoaSender)
										.via(totalSupplyTxn),
								contractCallWithFunctionAbi(FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "balanceOf", "ERC20ABI"),
										spenderMirrorAddr.get())
										.payingWith(eoaSender)
										.via(balanceOfTxn),
								contractCallWithFunctionAbi(
										FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "approve", "ERC20ABI"),
										spenderMirrorAddr.get(), 100)
										.gas(1_000_000)
										.payingWith(eoaSender)
										.via(approveTxn),
								contractCallWithFunctionAbi(
										FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "transfer", "ERC20ABI"),
										asHexedSolidityAddress(spec.registry().getAccountID(RECIPIENT)), 3)
										.gas(1_000_000)
										.payingWith(eoaSender)
										.via(transferTxn)
						))
				).then(withOpContext(
						(spec, ignore) ->
								allRunFor(spec,
										getTxnRecord(nameTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(symbolTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(decimalsTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(totalSupplyTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(balanceOfTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(approveTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(transferTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS))
								)));
	}

	private HapiApiSpec erc721DirectTokenCallsWithEOA() {
		final var creation = "autoCreation";
		final var spender = "spender";
		final var eoaSender = "eoaSender";
		final var ecdsaKey = "ecdsaKey";
		final var nameTxn = "nameTxn";
		final var symbolTxn = "symbolTxn";
		final var totalSupplyTxn = "totalSupplyTxn";
		final var balanceOfTxn = "balanceOfTxn";

		final AtomicReference<String> spenderMirrorAddr = new AtomicReference<>();

		return defaultHapiSpec("erc721DirectTokenCallsWithEOA")
				.given(
						cryptoCreate(spender).exposingCreatedIdTo(id ->
								spenderMirrorAddr.set(asHexedSolidityAddress(id))),
						newKeyNamed(ecdsaKey).shape(SECP256K1_ON),
						cryptoTransfer(
								tinyBarsFromToWithAlias(DEFAULT_PAYER, ecdsaKey, ONE_HUNDRED_HBARS)
						).via(creation),
						getReceipt(creation).savingAutoCreation(eoaSender, ecdsaKey),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.treasury(eoaSender)
								.initialSupply(0)
								.asCallableContract(),
						tokenAssociate(RECIPIENT, NON_FUNGIBLE_TOKEN)
				).when(withOpContext(
						(spec, ignore) -> allRunFor(spec,
								contractCallWithFunctionAbi(NON_FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "name", "ERC721ABI"))
										.payingWith(eoaSender)
										.via(nameTxn),
								contractCallWithFunctionAbi(NON_FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "symbol", "ERC721ABI"))
										.payingWith(eoaSender)
										.via(symbolTxn),
								contractCallWithFunctionAbi(NON_FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", "ERC721ABI"))
										.payingWith(eoaSender)
										.via(totalSupplyTxn),
								contractCallWithFunctionAbi(NON_FUNGIBLE_TOKEN,
										getABIFor(Utils.FunctionType.FUNCTION, "balanceOf", "ERC721ABI"),
										spenderMirrorAddr.get())
										.payingWith(eoaSender)
										.via(balanceOfTxn)
						))
				).then(withOpContext(
						(spec, ignore) ->
								allRunFor(spec,
										getTxnRecord(nameTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(symbolTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(totalSupplyTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS)),
										getTxnRecord(balanceOfTxn)
												.andAllChildRecords()
												.logged()
												.hasChildRecords(recordWith().status(SUCCESS))
								)));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
