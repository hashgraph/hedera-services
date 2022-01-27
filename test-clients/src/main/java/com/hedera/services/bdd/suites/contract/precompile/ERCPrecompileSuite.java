package com.hedera.services.bdd.suites.contract.precompile;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERCPrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);

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
				getTokenName(),
				getTokenSymbol(),
				getTokenDecimals(),
				getTotalSupply(),
				getBalanceOfAccount(),
				transferToken(),
				transferTokenFrom()
		);
	}

	List<HapiApiSpec> ERC_721() {
		return List.of(
		);
	}

	private HapiApiSpec getTokenName() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_20_NAME")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.name(tokenName)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_NAME_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(nameTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(nameTxn).andAllChildRecords().logged()
//						childRecordsCheck(nameTxn, SUCCESS,
//								recordWith().status(SUCCESS),
//								recordWith().contractCallResult(
//										resultWith().logs(
//												inOrder(
//														logWith().utf8data(tokenName)))))
				);
	}

	private HapiApiSpec getTokenSymbol() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var tokenSymbol = "S";
		final var symbolTxn = "symbolTxn";

		return defaultHapiSpec("ERC_20_SYMBOL")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.symbol(tokenSymbol)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_SYMBOL_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(symbolTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(symbolTxn).andAllChildRecords().logged()
//						childRecordsCheck(symbolTxn, SUCCESS,
//								recordWith().status(SUCCESS),
//								recordWith().contractCallResult(
//										resultWith().logs(
//												inOrder(
//														logWith().utf8data(tokenSymbol)))))
				);
	}

	private HapiApiSpec getTokenDecimals() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var decimals = 10;
		final var decimalsTxn = "decimalsTxn";

		return defaultHapiSpec("ERC_20_DECIMALS")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.decimals(decimals)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_DECIMALS_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(decimalsTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(decimalsTxn).andAllChildRecords().logged()
//						childRecordsCheck(decimalsTxn, SUCCESS,
//								recordWith().status(SUCCESS),
//								recordWith().contractCallResult(
//										resultWith().logs(
//												inOrder(
//														logWith().longValue(decimals)))))
				);
	}

	private HapiApiSpec getTotalSupply() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var totalSupply = 50;
		final var supplyTxn = "supplyTxn";

		return defaultHapiSpec("ERC_20_TOTAL_SUPPLY")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.initialSupply(totalSupply)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_TOTAL_SUPPLY_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)))
														.payingWith(theAccount)
														.via(supplyTxn)
														.hasKnownStatus(SUCCESS)
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

	private HapiApiSpec getBalanceOfAccount() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var balanceTxn = "balanceTxn";

		return defaultHapiSpec("ERC_20_BALANCE_OF")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_BALANCE_OF_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														asAddress(spec.registry().getAccountID(theAccount)))
														.payingWith(theAccount)
														.via(balanceTxn)
														.hasKnownStatus(SUCCESS)
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

	private HapiApiSpec transferToken() {
		final var theAccount = "anybody";
		final var theRecipient = "theRecipient";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient),
						cryptoCreate(TOKEN_TREASURY).balance(10 * ONE_HUNDRED_HBARS),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_TRANSFER_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														asAddress(spec.registry().getAccountID(theRecipient)), 2)
														.payingWith(TOKEN_TREASURY)
														.via(transferTxn)
														.hasKnownStatus(SUCCESS)
										)
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
																.accountAtBytes(theRecipient, 32)
																.longValue(2))))),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(fungibleToken, 3),
						getAccountBalance(theRecipient)
								.hasTokenBalance(fungibleToken, 2)
				);
	}

	private HapiApiSpec transferTokenFrom() {
		final var payerShape = SIMPLE;
		final var recipientShape = SIMPLE;
		final var payerSigsName = "payerSigs";
		final var recipientSigsName = "recipientSigs";
		final var theAccount = "anybody";
		final var theRecipient = "theRecipient";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var transferFromTxn = "transferFromTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed(payerSigsName).shape(payerShape),
						newKeyNamed(recipientSigsName).shape(recipientShape),
						cryptoCreate(theAccount).key(payerSigsName).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient).key(recipientSigsName).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(35)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000),
						cryptoTransfer(moving(20, fungibleToken).between(TOKEN_TREASURY, theAccount))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_TRANSFER_FROM_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														asAddress(spec.registry().getAccountID(theAccount)),
														asAddress(spec.registry().getAccountID(theRecipient)), 8)
														.payingWith(theAccount).sigControl(
																forKey(theAccount, payerShape),
																forKey(theRecipient, recipientShape))
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
																.accountAtBytes(theAccount, 0)
																.accountAtBytes(theRecipient, 32)
																.longValue(8))))),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(fungibleToken, 27),
						getAccountBalance(theAccount)
								.hasTokenBalance(fungibleToken, 12),
						getAccountBalance(theRecipient)
								.hasTokenBalance(fungibleToken, 8)
				);
	}

	private HapiApiSpec allowanceReturnsFailure() {
		final var theOwner = "anybody";
		final var theSpender = "spender";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var allowanceTxn = "allowanceTxn";

		return defaultHapiSpec("ERC_20_ALLOWANCE_RETURNS_FAILURE")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theOwner).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theSpender),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theOwner, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_ALLOWANCE_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														asAddress(spec.registry().getAccountID(theOwner)),
														asAddress(spec.registry().getAccountID(theSpender)))
														.payingWith(theOwner)
														.via(allowanceTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(allowanceTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec approveReturnsFailure() {
		final var theOwner = "anybody";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var approveTxn = "approveTxn";

		return defaultHapiSpec("ERC_20_APPROVE_RETURNS_FAILURE")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theOwner).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theOwner, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_APPROVE_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														asAddress(spec.registry().getAccountID(theOwner)), 10)
														.payingWith(theOwner)
														.via(approveTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						getTxnRecord(approveTxn).andAllChildRecords().logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}