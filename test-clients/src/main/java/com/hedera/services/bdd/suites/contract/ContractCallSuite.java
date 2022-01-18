package com.hedera.services.bdd.suites.contract;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.APPROVE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BALANCE_OF_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DECIMALS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.IMAP_USER_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.IMAP_USER_INSERT;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.IMAP_USER_REMOVE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.OC_TOKEN_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SYMBOL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TOKEN_ERC20_CONSTRUCTOR_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TRANSFER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TRANSFER_FROM_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.WORKING_HOURS_CONS;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.WORKING_HOURS_TAKE_TICKET;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.WORKING_HOURS_USER_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.WORKING_HOURS_WORK_TICKET;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractCallSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallSuite.class);
	private static final long depositAmount = 1000;

	public static void main(String... args) {
		new ContractCallSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				resultSizeAffectsFees(),
				payableSuccess(),
				depositSuccess(),
				depositDeleteSuccess(),
				multipleDepositSuccess(),
				payTestSelfDestructCall(),
				multipleSelfDestructsAreSafe(),
				smartContractInlineAssemblyCheck(),
				ocToken(),
				contractTransferToSigReqAccountWithKeySucceeds(),
				maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
				minChargeIsTXGasUsedByContractCall(),
				HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts(),
				HSCS_EVM_006_ContractHBarTransferToAccount(),
				HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts(),
				HSCS_EVM_010_MultiSignatureAccounts(),
				HSCS_EVM_010_ReceiverMustSignContractTx(),
				insufficientGas(),
				insufficientFee(),
				nonPayable(),
				invalidContract(),
				smartContractFailFirst(),
				contractTransferToSigReqAccountWithoutKeyFails(),
				callingDestructedContractReturnsStatusDeleted(),
				gasLimitOverMaxGasLimitFailsPrecheck(),
				imapUserExercise(),
				workingHoursDemo(),
				deletedContractsCannotBeUpdated()
		);
	}

	private HapiApiSpec deletedContractsCannotBeUpdated() {
		final var adminKey = "admin";
		final var contract = "contract";

		return defaultHapiSpec("DeletedContractsCannotBeUpdated")
				.given(
						newKeyNamed(adminKey),
						fileCreate("bytecode").path(ContractResources.SELF_DESTRUCT_CALLABLE),
						contractCreate(contract)
								.bytecode("bytecode")
								.adminKey(adminKey)
								.gas(300_000)
				).when(
						contractCall(contract, ContractResources.SELF_DESTRUCT_CALL_ABI)
								.deferStatusResolution()
				).then(
						contractUpdate(contract).newMemo("Hi there!").hasKnownStatus(INVALID_CONTRACT_ID)
				);
	}

	private HapiApiSpec workingHoursDemo() {
		final var initcode = "initcode";
		final var gasToOffer = 4_000_000;
		final var workingHours = "workingHours";

		final var ticketToken = "ticketToken";
		final var adminKey = "admin";
		final var treasury = "treasury";
		final var newSupplyKey = "newSupplyKey";

		final var ticketTaking = "ticketTaking";
		final var ticketWorking = "ticketWorking";
		final var mint = "minting";
		final var burn = "burning";
		final var preMints = List.of(
				ByteString.copyFromUtf8("HELLO"),
				ByteString.copyFromUtf8("GOODBYE"));

		final AtomicLong ticketSerialNo = new AtomicLong();

		return defaultHapiSpec("WorkingHoursDemo")
				.given(
						newKeyNamed(adminKey),
						cryptoCreate(treasury),
						tokenCreate(ticketToken)
								.treasury(treasury)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.supplyType(TokenSupplyType.INFINITE)
								.adminKey(adminKey)
								.supplyKey(adminKey),
						mintToken(ticketToken, preMints).via(mint),
						burnToken(ticketToken, List.of(1L)).via(burn),
						fileCreate(initcode),
						updateLargeFile(
								GENESIS,
								initcode,
								extractByteCode(WORKING_HOURS_USER_BYTECODE_PATH))
				).when(
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							final var tokenId = registry.getTokenID(ticketToken);
							final var treasuryId = registry.getAccountID(treasury);
							final var creation = contractCreate(
									workingHours,
									WORKING_HOURS_CONS,
									tokenId.getTokenNum(), treasuryId.getAccountNum()
							)
									.bytecode(initcode)
									.gas(gasToOffer);
							allRunFor(spec, creation);
						}),
						newKeyNamed(newSupplyKey)
								.shape(KeyShape.CONTRACT.signedWith(workingHours)),
						tokenUpdate(ticketToken).supplyKey(newSupplyKey)
				).then(
						/* Take a ticket */
						contractCall(workingHours, WORKING_HOURS_TAKE_TICKET)
								.via(ticketTaking)
								.alsoSigningWithFullPrefix(treasury)
								.exposingResultTo(result -> {
									log.info("Explicit mint result is {}", result);
									ticketSerialNo.set(((BigInteger) result[0]).longValueExact());
								}),
						getAccountBalance(DEFAULT_PAYER).hasTokenBalance(ticketToken, 1L),
						/* Our ticket number is 3 (b/c of the two pre-mints), so we must call
						* work twice before the contract will actually accept our ticket. */
						sourcing(() ->
								contractCall(workingHours, WORKING_HOURS_WORK_TICKET, ticketSerialNo.get())),
						getAccountBalance(DEFAULT_PAYER).hasTokenBalance(ticketToken, 1L),
						sourcing(() ->
								contractCall(workingHours, WORKING_HOURS_WORK_TICKET, ticketSerialNo.get())
										.via(ticketWorking)),
						getAccountBalance(DEFAULT_PAYER).hasTokenBalance(ticketToken, 0L),
						getTokenInfo(ticketToken).hasTotalSupply(1L),
						/* Review the history */
						getTxnRecord(ticketTaking).andAllChildRecords().logged(),
						getTxnRecord(ticketWorking).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec imapUserExercise() {
		final var initcode = "initcode";
		final var contract = "imapUser";
		final var insert1To4 = "insert1To10";
		final var insert2To8 = "insert2To8";
		final var insert3To16 = "insert3To16";
		final var remove2 = "remove2";
		final var gasToOffer = 4_000_000;

		return defaultHapiSpec("ImapUserExercise")
				.given(
						fileCreate(initcode).path(IMAP_USER_BYTECODE_PATH),
						contractCreate(contract)
								.bytecode(initcode)
				).when().then(
						contractCall(contract, IMAP_USER_INSERT, 1, 4)
								.gas(gasToOffer)
								.via(insert1To4),
						contractCall(contract, IMAP_USER_INSERT, 2, 8)
								.gas(gasToOffer)
								.via(insert2To8),
						contractCall(contract, IMAP_USER_INSERT, 3, 16)
								.gas(gasToOffer)
								.via(insert3To16),
						contractCall(contract, IMAP_USER_REMOVE, 2)
								.gas(gasToOffer)
								.via(remove2)
				);
	}

	HapiApiSpec ocToken() {
		return defaultHapiSpec("ocToken")
				.given(
						cryptoCreate("tokenIssuer").balance(1_000_000_000_000L),
						cryptoCreate("Alice").balance(10_000_000_000L).payingWith("tokenIssuer"),
						cryptoCreate("Bob").balance(10_000_000_000L).payingWith("tokenIssuer"),
						cryptoCreate("Carol").balance(10_000_000_000L).payingWith("tokenIssuer"),
						cryptoCreate("Dave").balance(10_000_000_000L).payingWith("tokenIssuer"),

						getAccountInfo("tokenIssuer").savingSnapshot("tokenIssuerAcctInfo"),
						getAccountInfo("Alice").savingSnapshot("AliceAcctInfo"),
						getAccountInfo("Bob").savingSnapshot("BobAcctInfo"),
						getAccountInfo("Carol").savingSnapshot("CarolAcctInfo"),
						getAccountInfo("Dave").savingSnapshot("DaveAcctInfo"),

						fileCreate("bytecode")
								.path(OC_TOKEN_BYTECODE_PATH),

						contractCreate("tokenContract", TOKEN_ERC20_CONSTRUCTOR_ABI,
								1_000_000L, "OpenCrowd Token", "OCT")
								.gas(250_000L)
								.payingWith("tokenIssuer")
								.bytecode("bytecode")
								.via("tokenCreateTxn").logged()
				).when(
						assertionsHold((spec, ctxLog) -> {
							String issuerEthAddress = spec.registry().getAccountInfo("tokenIssuerAcctInfo")
									.getContractAccountID();
							String aliceEthAddress = spec.registry().getAccountInfo("AliceAcctInfo")
									.getContractAccountID();
							String bobEthAddress = spec.registry().getAccountInfo("BobAcctInfo")
									.getContractAccountID();
							String carolEthAddress = spec.registry().getAccountInfo("CarolAcctInfo")
									.getContractAccountID();
							String daveEthAddress = spec.registry().getAccountInfo("DaveAcctInfo")
									.getContractAccountID();

							var subop1 = getContractInfo("tokenContract")
									.nodePayment(10L)
									.saveToRegistry("tokenContract");

							var subop3 = contractCallLocal("tokenContract", DECIMALS_ABI)
									.saveResultTo("decimals")
									.payingWith("tokenIssuer");

							// Note: This contract call will cause a INSUFFICIENT_TX_FEE error, not sure why.
							var subop4 = contractCallLocal("tokenContract", SYMBOL_ABI)
									.saveResultTo("token_symbol")
									.payingWith("tokenIssuer")
									.hasAnswerOnlyPrecheckFrom(OK, INSUFFICIENT_TX_FEE);

							var subop5 = contractCallLocal("tokenContract", BALANCE_OF_ABI, issuerEthAddress)
									.gas(250_000L)
									.saveResultTo("issuerTokenBalance");

							allRunFor(spec, subop1, subop3, subop4, subop5);

							CallTransaction.Function funcSymbol =
									CallTransaction.Function.fromJsonInterface(SYMBOL_ABI);

							String symbol = getValueFromRegistry(spec, "token_symbol", funcSymbol);

							ctxLog.info("symbol: [{}]", symbol);
							Assertions.assertEquals(
									"", symbol,
									"TokenIssuer's symbol should be fixed value"); // should be "OCT" as expected

							CallTransaction.Function funcDecimals = CallTransaction.Function.fromJsonInterface(
									DECIMALS_ABI);

							//long decimals = getLongValueFromRegistry(spec, "decimals", function);
							BigInteger val = getValueFromRegistry(spec, "decimals", funcDecimals);
							long decimals = val.longValue();

							ctxLog.info("decimals {}", decimals);
							Assertions.assertEquals(
									3, decimals,
									"TokenIssuer's decimals should be fixed value");

							long tokenMultiplier = (long) Math.pow(10, decimals);

							CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(
									BALANCE_OF_ABI);

							long issuerBalance = ((BigInteger) getValueFromRegistry(spec, "issuerTokenBalance",
									function)).longValue();

							ctxLog.info("initial balance of Issuer {}", issuerBalance / tokenMultiplier);
							Assertions.assertEquals(
									1_000_000, issuerBalance / tokenMultiplier,
									"TokenIssuer's initial token balance should be 1_000_000");

							//  Do token transfers
							var subop6 = contractCall("tokenContract", TRANSFER_ABI,
									aliceEthAddress, 1000 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("tokenIssuer");

							var subop7 = contractCall("tokenContract", TRANSFER_ABI,
									bobEthAddress, 2000 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("tokenIssuer");

							var subop8 = contractCall("tokenContract", TRANSFER_ABI,
									carolEthAddress, 500 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Bob");

							var subop9 = contractCallLocal("tokenContract", BALANCE_OF_ABI, aliceEthAddress)
									.gas(250_000L)
									.saveResultTo("aliceTokenBalance");

							var subop10 = contractCallLocal("tokenContract", BALANCE_OF_ABI, carolEthAddress)
									.gas(250_000L)
									.saveResultTo("carolTokenBalance");

							var subop11 = contractCallLocal("tokenContract", BALANCE_OF_ABI, bobEthAddress)
									.gas(250_000L)
									.saveResultTo("bobTokenBalance");

							allRunFor(spec, subop6, subop7, subop8, subop9, subop10, subop11);

							long aliceBalance = ((BigInteger) getValueFromRegistry(spec, "aliceTokenBalance",
									function)).longValue();
							long bobBalance = ((BigInteger) getValueFromRegistry(spec, "bobTokenBalance",
									function)).longValue();
							long carolBalance = ((BigInteger) getValueFromRegistry(spec, "carolTokenBalance",
									function)).longValue();

							ctxLog.info("aliceBalance  {}", aliceBalance / tokenMultiplier);
							ctxLog.info("bobBalance  {}", bobBalance / tokenMultiplier);
							ctxLog.info("carolBalance  {}", carolBalance / tokenMultiplier);

							Assertions.assertEquals(
									1000, aliceBalance / tokenMultiplier,
									"Alice's token balance should be 1_000");

							var subop12 = contractCall("tokenContract", APPROVE_ABI,
									daveEthAddress, 200 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Alice");

							var subop13 = contractCall("tokenContract", TRANSFER_FROM_ABI,
									aliceEthAddress, bobEthAddress, 100 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Dave");

							var subop14 = contractCallLocal("tokenContract", BALANCE_OF_ABI, aliceEthAddress)
									.gas(250_000L)
									.saveResultTo("aliceTokenBalance");

							var subop15 = contractCallLocal("tokenContract", BALANCE_OF_ABI, bobEthAddress)
									.gas(250_000L)
									.saveResultTo("bobTokenBalance");

							var subop16 = contractCallLocal("tokenContract", BALANCE_OF_ABI, carolEthAddress)
									.gas(250_000L)
									.saveResultTo("carolTokenBalance");

							var subop17 = contractCallLocal("tokenContract", BALANCE_OF_ABI, daveEthAddress)
									.gas(250_000L)
									.saveResultTo("daveTokenBalance");

							var subop18 = contractCallLocal("tokenContract", BALANCE_OF_ABI, issuerEthAddress)
									.gas(250_000L)
									.saveResultTo("issuerTokenBalance");

							allRunFor(spec, subop12, subop13, subop14, subop15, subop16, subop17,
									subop18);

							long daveBalance = ((BigInteger) getValueFromRegistry(spec, "daveTokenBalance",
									function)).longValue();
							aliceBalance = ((BigInteger) getValueFromRegistry(spec, "aliceTokenBalance",
									function)).longValue();
							bobBalance = ((BigInteger) getValueFromRegistry(spec, "bobTokenBalance",
									function)).longValue();
							carolBalance = ((BigInteger) getValueFromRegistry(spec, "carolTokenBalance",
									function)).longValue();
							issuerBalance = ((BigInteger) getValueFromRegistry(spec, "issuerTokenBalance",
									function)).longValue();

							ctxLog.info("aliceBalance at end {}", aliceBalance / tokenMultiplier);
							ctxLog.info("bobBalance at end {}", bobBalance / tokenMultiplier);
							ctxLog.info("carolBalance at end {}", carolBalance / tokenMultiplier);
							ctxLog.info("daveBalance at end {}", daveBalance / tokenMultiplier);
							ctxLog.info("issuerBalance at end {}", issuerBalance / tokenMultiplier);

							Assertions.assertEquals(
									997000, issuerBalance / tokenMultiplier,
									"TokenIssuer's final balance should be 997000");

							Assertions.assertEquals(
									900, aliceBalance / tokenMultiplier,
									"Alice's final balance should be 900");
							Assertions.assertEquals(
									1600, bobBalance / tokenMultiplier,
									"Bob's final balance should be 1600");
							Assertions.assertEquals(
									500, carolBalance / tokenMultiplier,
									"Carol's final balance should be 500");
							Assertions.assertEquals(
									0, daveBalance / tokenMultiplier,
									"Dave's final balance should be 0");
						})
				).then(
						assertionsHold((spec, ctxLog) -> {
							var finalOp = getContractRecords("tokenContract")
									.saveRecordNumToRegistry("tokenContractRecordNum")
									.hasCostAnswerPrecheck(OK);

							allRunFor(spec, finalOp);

							int totalRecordNum = spec.registry().getIntValue("tokenContractRecordNum");
							ctxLog.info("Finished {}", totalRecordNum);

							Assertions.assertEquals(
									0,
									totalRecordNum,
									"Contracts should no longer receive records!");
						})
				);
	}

	private <T> T getValueFromRegistry(HapiApiSpec spec, String from, CallTransaction.Function function) {
		byte[] value = spec.registry().getBytes(from);

		T decodedReturnedValue = null;
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			decodedReturnedValue = (T) retResults[0];
		}
		return decodedReturnedValue;
	}


	HapiApiSpec smartContractInlineAssemblyCheck() {
		return defaultHapiSpec("smartContractInlineAssemblyCheck")
				.given(
						cryptoCreate("payer")
								.balance(10_000_000_000_000L),
						fileCreate("simpleStorageByteCode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH),
						fileCreate("inlineTestByteCode")
								.path(ContractResources.INLINE_TEST_BYTECODE_PATH)

				).when(
						contractCreate("simpleStorageContract")
								.payingWith("payer")
								.gas(300_000L)
								.bytecode("simpleStorageByteCode")
								.via("simpleStorageContractTxn"),
						contractCreate("inlineTestContract")
								.payingWith("payer")
								.gas(300_000L)
								.bytecode("inlineTestByteCode")
								.via("inlineTestContractTxn")

				).then(
						assertionsHold((spec, ctxLog) -> {

							var subop1 = getContractInfo("simpleStorageContract")
									.nodePayment(10L)
									.saveToRegistry("simpleStorageKey");

							var subop2 = getAccountInfo("payer")
									.savingSnapshot("payerAccountInfo");
							allRunFor(spec, subop1, subop2);

							ContractGetInfoResponse.ContractInfo simpleStorageContractInfo =
									spec.registry().getContractInfo(
											"simpleStorageKey");
							String contractAddress = simpleStorageContractInfo.getContractAccountID();

							var subop3 = contractCallLocal("inlineTestContract", ContractResources.GET_CODE_SIZE_ABI,
									contractAddress)
									.saveResultTo("simpleStorageContractCodeSizeBytes")
									.gas(300_000L);

							allRunFor(spec, subop3);

							byte[] result = spec.registry().getBytes("simpleStorageContractCodeSizeBytes");

							String funcJson = ContractResources.GET_CODE_SIZE_ABI.replaceAll("'", "\"");
							CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);

							int codeSize = 0;
							if (result != null && result.length > 0) {
								Object[] retResults = function.decodeResult(result);
								if (retResults != null && retResults.length > 0) {
									BigInteger retBi = (BigInteger) retResults[0];
									codeSize = retBi.intValue();
								}
							}

							ctxLog.info("Contract code size {}", codeSize);
							Assertions.assertNotEquals(
									0, codeSize,
									"Real smart contract code size should be greater than 0");


							CryptoGetInfoResponse.AccountInfo payerAccountInfo = spec.registry().getAccountInfo(
									"payerAccountInfo");
							String acctAddress = payerAccountInfo.getContractAccountID();

							var subop4 = contractCallLocal("inlineTestContract", ContractResources.GET_CODE_SIZE_ABI,
									acctAddress)
									.saveResultTo("fakeCodeSizeBytes")
									.gas(300_000L);

							allRunFor(spec, subop4);
							result = spec.registry().getBytes("fakeCodeSizeBytes");

							codeSize = 0;
							if (result != null && result.length > 0) {
								Object[] retResults = function.decodeResult(result);
								if (retResults != null && retResults.length > 0) {
									BigInteger retBi = (BigInteger) retResults[0];
									codeSize = retBi.intValue();
								}
							}

							ctxLog.info("Fake contract code size {}", codeSize);
							Assertions.assertEquals(
									0, codeSize,
									"Fake contract code size should be 0");
						})
				);
	}

	private HapiApiSpec multipleSelfDestructsAreSafe() {
		return defaultHapiSpec("MultipleSelfDestructsAreSafe")
				.given(
						fileCreate("bytecode").path(ContractResources.FUSE_BYTECODE_PATH),
						contractCreate("fuse").bytecode("bytecode").gas(300_000)
				).when(
						contractCall("fuse", ContractResources.LIGHT_ABI).via("lightTxn")
				).then(
						getTxnRecord("lightTxn").logged()
				);
	}

	HapiApiSpec depositSuccess() {
		return defaultHapiSpec("DepositSuccess")
				.given(
						fileCreate("payableBytecode").path(ContractResources.PAYABLE_CONTRACT_BYTECODE_PATH),
						contractCreate("payableContract").bytecode("payableBytecode").adminKey(THRESHOLD)
				).when(
						contractCall("payableContract", ContractResources.DEPOSIT_ABI, depositAmount)
								.via("payTxn").sending(depositAmount)
				).then(
						getTxnRecord("payTxn")
								.hasPriority(recordWith().contractCallResult(
										resultWith().logs(inOrder()))));
	}

	HapiApiSpec multipleDepositSuccess() {
		return defaultHapiSpec("MultipleDepositSuccess")
				.given(
						fileCreate("payableBytecode").path(ContractResources.PAYABLE_CONTRACT_BYTECODE_PATH),
						contractCreate("payableContract").bytecode("payableBytecode").adminKey(THRESHOLD)
				)
				.when()
				.then(
						withOpContext((spec, opLog) -> {
							for (int i = 0; i < 10; i++) {
								var subOp1 = balanceSnapshot("payerBefore", "payableContract");
								var subOp2 = contractCall("payableContract", ContractResources.DEPOSIT_ABI,
										depositAmount)
										.via("payTxn").sending(depositAmount);
								var subOp3 = getAccountBalance("payableContract")
										.hasTinyBars(changeFromSnapshot("payerBefore", +depositAmount));
								allRunFor(spec, subOp1, subOp2, subOp3);
							}
						})
				);
	}

	HapiApiSpec depositDeleteSuccess() {
		long initBalance = 7890;
		return defaultHapiSpec("DepositDeleteSuccess")
				.given(
						cryptoCreate("beneficiary").balance(initBalance),
						fileCreate("payableBytecode").path(ContractResources.PAYABLE_CONTRACT_BYTECODE_PATH),
						contractCreate("payableContract").bytecode("payableBytecode").adminKey(THRESHOLD)
				).when(
						contractCall("payableContract", ContractResources.DEPOSIT_ABI, depositAmount)
								.via("payTxn").sending(depositAmount)

				).then(
						contractDelete("payableContract").transferAccount("beneficiary"),
						getAccountBalance("beneficiary")
								.hasTinyBars(initBalance + depositAmount)
				);
	}

	HapiApiSpec payableSuccess() {
		return defaultHapiSpec("PayableSuccess")
				.given(
						UtilVerbs.overriding("contracts.maxGas", "1000000"),
						fileCreate("payableBytecode").path(ContractResources.PAYABLE_CONTRACT_BYTECODE_PATH),
						contractCreate("payableContract").bytecode("payableBytecode").adminKey(THRESHOLD).gas(1_000_000)
				).when(
						contractCall("payableContract").via("payTxn").sending(depositAmount)
				).then(
						getTxnRecord("payTxn")
								.hasPriority(recordWith().contractCallResult(
										resultWith().logs(
												inOrder(
														logWith().longAtBytes(depositAmount, 24))))),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	HapiApiSpec callingDestructedContractReturnsStatusDeleted() {
		return defaultHapiSpec("CallingDestructedContractReturnsStatusDeleted")
				.given(
						UtilVerbs.overriding("contracts.maxGas", "1000000"),
						fileCreate("simpleUpdateBytecode").path(ContractResources.SIMPLE_UPDATE)
				).when(
						contractCreate("simpleUpdateContract").bytecode("simpleUpdateBytecode").gas(300_000L),
						contractCall("simpleUpdateContract",
								ContractResources.SIMPLE_UPDATE_ABI, 5, 42).gas(300_000L),
						contractCall("simpleUpdateContract",
								ContractResources.SIMPLE_SELFDESTRUCT_UPDATE_ABI,
								"0x0000000000000000000000000000000000000002")
								.gas(1_000_000L)
				).then(
						contractCall("simpleUpdateContract",
								ContractResources.SIMPLE_UPDATE_ABI, 15, 434).gas(350_000L)
								.hasKnownStatus(CONTRACT_DELETED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	HapiApiSpec insufficientGas() {
		return defaultHapiSpec("InsufficientGas")
				.given(
						fileCreate("simpleStorageBytecode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH),
						contractCreate("simpleStorage").bytecode("simpleStorageBytecode").adminKey(THRESHOLD),
						getContractInfo("simpleStorage").saveToRegistry("simpleStorageInfo")
				).when().then(
						contractCall("simpleStorage", ContractResources.CREATE_CHILD_ABI).via("simpleStorageTxn")
								.gas(0L).hasKnownStatus(INSUFFICIENT_GAS),
						getTxnRecord("simpleStorageTxn").logged()
				);
	}

	HapiApiSpec insufficientFee() {
		return defaultHapiSpec("InsufficientFee")
				.given(
						cryptoCreate("accountToPay"),
						fileCreate("parentDelegateBytecode")
								.path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode")
				).when().then(
						contractCall("parentDelegate", ContractResources.CREATE_CHILD_ABI).fee(0L)
								.payingWith("accountToPay")
								.hasPrecheck(INSUFFICIENT_TX_FEE));
	}

	HapiApiSpec nonPayable() {
		return defaultHapiSpec("NonPayable")
				.given(
						fileCreate("parentDelegateBytecode")
								.path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode")
				).when(
						contractCall("parentDelegate", ContractResources.CREATE_CHILD_ABI).via("callTxn").sending(
								depositAmount)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				).then(
						getTxnRecord("callTxn").hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder())))
				);
	}

	HapiApiSpec invalidContract() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();

		return defaultHapiSpec("InvalidContract")
				.given().when().then(
						contractCall(invalidContract, ContractResources.CREATE_CHILD_ABI)
								.hasKnownStatus(INVALID_CONTRACT_ID));
	}

	private HapiApiSpec resultSizeAffectsFees() {
		final long TRANSFER_AMOUNT = 1_000L;
		BiConsumer<TransactionRecord, Logger> RESULT_SIZE_FORMATTER = (record, txnLog) -> {
			ContractFunctionResult result = record.getContractCallResult();
			txnLog.info("Contract call result FeeBuilder size = "
					+ FeeBuilder.getContractFunctionSize(result)
					+ ", fee = " + record.getTransactionFee()
					+ ", result is [self-reported size = " + result.getContractCallResult().size()
					+ ", '" + result.getContractCallResult() + "']");
			txnLog.info("  Literally :: " + result);
		};

		return defaultHapiSpec("ResultSizeAffectsFees")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "false"),
						TxnVerbs.fileCreate("bytecode").path(ContractResources.VERBOSE_DEPOSIT_BYTECODE_PATH),
						TxnVerbs.contractCreate("testContract").bytecode("bytecode")
				).when(
						TxnVerbs.contractCall(
								"testContract", ContractResources.VERBOSE_DEPOSIT_ABI,
								TRANSFER_AMOUNT, 0, "So we out-danced thought...")
								.via("noLogsCallTxn").sending(TRANSFER_AMOUNT),
						TxnVerbs.contractCall(
								"testContract", ContractResources.VERBOSE_DEPOSIT_ABI,
								TRANSFER_AMOUNT, 5, "So we out-danced thought...")
								.via("loggedCallTxn").sending(TRANSFER_AMOUNT)

				).then(
						assertionsHold((spec, assertLog) -> {
							HapiGetTxnRecord noLogsLookup =
									QueryVerbs.getTxnRecord("noLogsCallTxn").loggedWith(RESULT_SIZE_FORMATTER);
							HapiGetTxnRecord logsLookup =
									QueryVerbs.getTxnRecord("loggedCallTxn").loggedWith(RESULT_SIZE_FORMATTER);
							allRunFor(spec, noLogsLookup, logsLookup);
							TransactionRecord unloggedRecord =
									noLogsLookup.getResponse().getTransactionGetRecord().getTransactionRecord();
							TransactionRecord loggedRecord =
									logsLookup.getResponse().getTransactionGetRecord().getTransactionRecord();
							assertLog.info("Fee for logged record   = " + loggedRecord.getTransactionFee());
							assertLog.info("Fee for unlogged record = " + unloggedRecord.getTransactionFee());
							Assertions.assertNotEquals(
									unloggedRecord.getTransactionFee(),
									loggedRecord.getTransactionFee(),
									"Result size should change the txn fee!");
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	HapiApiSpec smartContractFailFirst() {
		return defaultHapiSpec("smartContractFailFirst")
				.given(
						cryptoCreate("payer").balance(1_000_000_000_000L).logged(),
						fileCreate("bytecode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH)
				).when(
						withOpContext((spec, ignore) -> {
							var subop1 = balanceSnapshot("balanceBefore0", "payer");

							var subop2 =
									contractCreate("failInsufficientGas")
											.balance(0)
											.payingWith("payer")
											.gas(1)
											.bytecode("bytecode")
											.hasKnownStatus(INSUFFICIENT_GAS)
											.via("failInsufficientGas");

							var subop3 = getTxnRecord("failInsufficientGas");
							allRunFor(spec, subop1, subop2, subop3);
							long delta = subop3.getResponseRecord().getTransactionFee();

							var subop4 = getAccountBalance("payer").hasTinyBars(
									changeFromSnapshot("balanceBefore0", -delta));
							allRunFor(spec, subop4);

						}),

						withOpContext((spec, ignore) -> {

							var subop1 = balanceSnapshot("balanceBefore1", "payer");

							var subop2 = contractCreate("failInvalidInitialBalance")
									.balance(100_000_000_000L)
									.payingWith("payer")
									.gas(250_000L)
									.bytecode("bytecode")
									.via("failInvalidInitialBalance")
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED);

							var subop3 = getTxnRecord("failInvalidInitialBalance");
							allRunFor(spec, subop1, subop2, subop3);
							long delta = subop3.getResponseRecord().getTransactionFee();

							var subop4 = getAccountBalance("payer").hasTinyBars(
									changeFromSnapshot("balanceBefore1", -delta));
							allRunFor(spec, subop4);

						}),

						withOpContext((spec, ignore) -> {

							var subop1 = balanceSnapshot("balanceBefore2", "payer");

							var subop2 = contractCreate("successWithZeroInitialBalance")
									.balance(0L)
									.payingWith("payer")
									.gas(250_000L)
									.bytecode("bytecode")
									.hasKnownStatus(SUCCESS)
									.via("successWithZeroInitialBalance");

							var subop3 = getTxnRecord("successWithZeroInitialBalance");
							allRunFor(spec, subop1, subop2, subop3);
							long delta = subop3.getResponseRecord().getTransactionFee();

							var subop4 = getAccountBalance("payer").hasTinyBars(
									changeFromSnapshot("balanceBefore2", -delta));
							allRunFor(spec, subop4);

						}),

						withOpContext((spec, ignore) -> {

							var subop1 = balanceSnapshot("balanceBefore3", "payer");

							var subop2 = contractCall("successWithZeroInitialBalance",
									ContractResources.SIMPLE_STORAGE_SETTER_ABI, 999_999L)
									.payingWith("payer")
									.gas(300_000L)
									.hasKnownStatus(SUCCESS)
									.via("setValue");

							var subop3 = getTxnRecord("setValue");
							allRunFor(spec, subop1, subop2, subop3);
							long delta = subop3.getResponseRecord().getTransactionFee();

							var subop4 = getAccountBalance("payer").hasTinyBars(
									changeFromSnapshot("balanceBefore3", -delta));
							allRunFor(spec, subop4);

						}),

						withOpContext((spec, ignore) -> {

							var subop1 = balanceSnapshot("balanceBefore4", "payer");

							var subop2 = contractCall("successWithZeroInitialBalance",
									ContractResources.SIMPLE_STORAGE_GETTER_ABI)
									.payingWith("payer")
									.gas(300_000L)
									.hasKnownStatus(SUCCESS)
									.via("getValue");

							var subop3 = getTxnRecord("getValue");
							allRunFor(spec, subop1, subop2, subop3);
							long delta = subop3.getResponseRecord().getTransactionFee();

							var subop4 = getAccountBalance("payer").hasTinyBars(
									changeFromSnapshot("balanceBefore4", -delta));
							allRunFor(spec, subop4);

						})
				).then(
						getTxnRecord("failInsufficientGas"),
						getTxnRecord("successWithZeroInitialBalance"),
						getTxnRecord("failInvalidInitialBalance")
				);
	}

	HapiApiSpec payTestSelfDestructCall() {
		return defaultHapiSpec("payTestSelfDestructCall")
				.given(
						cryptoCreate("payer").balance(1_000_000_000_000L).logged(),
						cryptoCreate("receiver").balance(1_000L),
						fileCreate("bytecode")
								.path(ContractResources.PAY_TEST_SELF_DESTRUCT_BYTECODE_PATH),
						contractCreate("payTestSelfDestruct")
								.bytecode("bytecode")
				).when(
						withOpContext((spec, opLog) -> {
							var subop1 = contractCall(
									"payTestSelfDestruct", ContractResources.DEPOSIT_ABI, 1_000L)
									.payingWith("payer")
									.gas(300_000L)
									.via("deposit")
									.sending(1_000L);

							var subop2 = contractCall(
									"payTestSelfDestruct", ContractResources.GET_BALANCE_ABI)
									.payingWith("payer")
									.gas(300_000L)
									.via("getBalance");

							AccountID contractAccountId = asId("payTestSelfDestruct", spec);
							var subop3 = contractCall(
									"payTestSelfDestruct", ContractResources.KILL_ME_ABI,
									contractAccountId.getAccountNum())
									.payingWith("payer")
									.gas(300_000L)
									.hasKnownStatus(OBTAINER_SAME_CONTRACT_ID);

							var subop4 = contractCall(
									"payTestSelfDestruct", ContractResources.KILL_ME_ABI, 999_999L)
									.payingWith("payer")
									.gas(300_000L)
									.hasKnownStatus(INVALID_SOLIDITY_ADDRESS);

							AccountID receiverAccountId = asId("receiver", spec);
							var subop5 = contractCall(
									"payTestSelfDestruct", ContractResources.KILL_ME_ABI,
									receiverAccountId.getAccountNum())
									.payingWith("payer")
									.gas(300_000L)
									.via("selfDestruct")
									.hasKnownStatus(SUCCESS);

							allRunFor(spec, subop1, subop2, subop3, subop4, subop5);
						})
				).then(
						getTxnRecord("deposit"),
						getTxnRecord("getBalance")
								.hasPriority(recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GET_BALANCE_ABI,
												isLiteralResult(new Object[] { BigInteger.valueOf(1_000L) })))),
						getAccountBalance("receiver")
								.hasTinyBars(2_000L)
				);
	}

	private HapiApiSpec contractTransferToSigReqAccountWithKeySucceeds() {
		return defaultHapiSpec("ContractTransferToSigReqAccountWithKeySucceeds")
				.given(
						cryptoCreate("contractCaller").balance(1_000_000_000_000L),
						cryptoCreate("receivableSigReqAccount")
								.balance(1_000_000_000_000L).receiverSigRequired(true),
						getAccountInfo("contractCaller").savingSnapshot("contractCallerInfo"),
						getAccountInfo("receivableSigReqAccount").savingSnapshot("receivableSigReqAccountInfo"),
						fileCreate("transferringContractBytecode").path(ContractResources.TRANSFERRING_CONTRACT)
				).when(
						contractCreate("transferringContract").bytecode("transferringContractBytecode")
								.gas(300_000L).balance(5000L)
				).then(
						withOpContext((spec, opLog) -> {
							String accountAddress = spec.registry()
									.getAccountInfo("receivableSigReqAccountInfo").getContractAccountID();
							Key receivableAccountKey = spec.registry()
									.getAccountInfo("receivableSigReqAccountInfo").getKey();
							Key contractCallerKey = spec.registry()
									.getAccountInfo("contractCallerInfo").getKey();
							spec.registry().saveKey("receivableKey", receivableAccountKey);
							spec.registry().saveKey("contractCallerKey", contractCallerKey);
							/* if any of the keys are missing, INVALID_SIGNATURE is returned */
							var call = contractCall(
									"transferringContract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									accountAddress,
									1
							)
									.payingWith("contractCaller")
									.gas(300_000)
									.alsoSigningWithFullPrefix("receivableKey");
							/* calling with the receivableSigReqAccount should pass without adding keys */
							var callWithReceivable = contractCall("transferringContract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									accountAddress, 1).payingWith("receivableSigReqAccount")
									.gas(300_000).hasKnownStatus(SUCCESS);
							allRunFor(spec, call, callWithReceivable);
						})
				);
	}

	private HapiApiSpec contractTransferToSigReqAccountWithoutKeyFails() {
		return defaultHapiSpec("ContractTransferToSigReqAccountWithoutKeyFails")
				.given(
						cryptoCreate("receivableSigReqAccount")
								.balance(1_000_000_000_000L).receiverSigRequired(true),
						getAccountInfo("receivableSigReqAccount").savingSnapshot("receivableSigReqAccountInfo"),
						fileCreate("transferringContractBytecode").path(ContractResources.TRANSFERRING_CONTRACT)
				).when(
						contractCreate("transferringContract").bytecode("transferringContractBytecode")
								.gas(300_000L).balance(5000L)
				).then(
						withOpContext((spec, opLog) -> {
							String accountAddress = spec.registry()
									.getAccountInfo("receivableSigReqAccountInfo").getContractAccountID();
							var call = contractCall("transferringContract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									accountAddress, 1).gas(300_000).hasKnownStatus(INVALID_SIGNATURE);
							allRunFor(spec, call);
						})
				);
	}

	private HapiApiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
		return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "5"),
						fileCreate("simpleUpdateBytecode").path(ContractResources.SIMPLE_UPDATE)
				).when(
						contractCreate("simpleUpdateContract").bytecode("simpleUpdateBytecode").gas(300_000L),
						contractCall("simpleUpdateContract",
								ContractResources.SIMPLE_UPDATE_ABI, 5, 42).gas(300_000L).via("callTX")
				).then(
						withOpContext((spec, ignore) -> {
							final var subop01 = getTxnRecord("callTX").saveTxnRecordToRegistry("callTXRec");
							allRunFor(spec, subop01);

							final var gasUsed = spec.registry().getTransactionRecord("callTXRec")
									.getContractCallResult().getGasUsed();
							Assertions.assertEquals(285000, gasUsed);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec minChargeIsTXGasUsedByContractCall() {
		return defaultHapiSpec("MinChargeIsTXGasUsedByContractCall")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						fileCreate("simpleUpdateBytecode").path(ContractResources.SIMPLE_UPDATE)
				).when(
						contractCreate("simpleUpdateContract").bytecode("simpleUpdateBytecode").gas(300_000L),
						contractCall("simpleUpdateContract",
								ContractResources.SIMPLE_UPDATE_ABI, 5, 42).gas(300_000L).via("callTX")
				).then(
						withOpContext((spec, ignore) -> {
							final var subop01 = getTxnRecord("callTX").saveTxnRecordToRegistry("callTXRec");
							allRunFor(spec, subop01);

							final var gasUsed = spec.registry().getTransactionRecord("callTXRec")
									.getContractCallResult().getGasUsed();
							Assertions.assertTrue(gasUsed > 0L);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
		return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
				.given(
						fileCreate("simpleUpdateBytecode").path(ContractResources.SIMPLE_UPDATE),
						contractCreate("simpleUpdateContract").bytecode("simpleUpdateBytecode").gas(300_000L),
						UtilVerbs.overriding("contracts.maxGas", "100")
				).when().then(
						contractCall("simpleUpdateContract",
								ContractResources.SIMPLE_UPDATE_ABI, 5, 42).gas(101L).hasPrecheck(
								MAX_GAS_LIMIT_EXCEEDED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec HSCS_EVM_006_ContractHBarTransferToAccount() {
		final var ACCOUNT = "account";
		final var CONTRACT_FROM = "contract1";
		return defaultHapiSpec("HSCS_EVM_006_ContractHBarTransferToAccount")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver").balance(10_000L),

						fileCreate("contract1Bytecode").path(ContractResources.TRANSFERRING_CONTRACT).payingWith(
								ACCOUNT),
						contractCreate(CONTRACT_FROM).bytecode("contract1Bytecode").balance(10_000L).payingWith(ACCOUNT),

						getContractInfo(CONTRACT_FROM).saveToRegistry("contract_from"),
						getAccountInfo(ACCOUNT).savingSnapshot("accountInfo"),
						getAccountInfo("receiver").savingSnapshot("receiverInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiverAddr = spec.registry().getAccountInfo("receiverInfo").getContractAccountID();
							var transferCall = contractCall(
									CONTRACT_FROM,
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									receiverAddr, 10)
									.payingWith(ACCOUNT).logged();
							allRunFor(spec, transferCall);
						})
				)
				.then(
						getAccountBalance("receiver").hasTinyBars(10_000 + 10)
				);
	}

	private HapiApiSpec HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts() {
		final var ACCOUNT = "account";
		final var TOP_LEVEL_CONTRACT = "tlc";
		final var SUB_LEVEL_CONTRACT = "slc";
		final var INITIAL_CONTRACT_BALANCE = 100;

		return defaultHapiSpec("HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						fileCreate(TOP_LEVEL_CONTRACT + "bytecode").path(
								ContractResources.TOP_LEVEL_TRANSFERRING_CONTRACT),
						fileCreate(SUB_LEVEL_CONTRACT + "bytecode").path(
								ContractResources.SUB_LEVEL_TRANSFERRING_CONTRACT)
				)
				.when(
						contractCreate(TOP_LEVEL_CONTRACT).bytecode(TOP_LEVEL_CONTRACT + "bytecode").payingWith(
								ACCOUNT).balance(INITIAL_CONTRACT_BALANCE),
						contractCreate(SUB_LEVEL_CONTRACT).bytecode(SUB_LEVEL_CONTRACT + "bytecode").payingWith(
								ACCOUNT).balance(INITIAL_CONTRACT_BALANCE)
				)
				.then(
						contractCall(TOP_LEVEL_CONTRACT).sending(10).payingWith(ACCOUNT),
						getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE + 10),

						contractCall(TOP_LEVEL_CONTRACT,
								ContractResources.TOP_LEVEL_TRANSFERRING_CONTRACT_TRANSFER_CALL_PAYABLE_ABI)
								.sending(10)
								.payingWith(ACCOUNT),
						getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE + 20),

						contractCall(TOP_LEVEL_CONTRACT,
								ContractResources.TOP_LEVEL_TRANSFERRING_CONTRACT_NON_PAYABLE_ABI)
								.sending(10)
								.payingWith(ACCOUNT)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
						getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE + 20),

						getContractInfo(TOP_LEVEL_CONTRACT).saveToRegistry("tcinfo"),
						getContractInfo(SUB_LEVEL_CONTRACT).saveToRegistry("scinfo"),

						/* sub-level non-payable contract call */
						assertionsHold((spec, log) -> {
							final var subLevelSolidityAddr = spec.registry().getContractInfo(
									"scinfo").getContractAccountID();
							final var cc = contractCall(
									SUB_LEVEL_CONTRACT,
									ContractResources.SUB_LEVEL_NON_PAYABLE_ABI,
									subLevelSolidityAddr, 20L)
									.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);
							allRunFor(spec, cc);
						}),
						getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(20 + INITIAL_CONTRACT_BALANCE),
						getAccountBalance(SUB_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE),

						/* sub-level payable contract call */
						assertionsHold((spec, log) -> {
							final var subLevelSolidityAddr = spec.registry().getContractInfo(
									"scinfo").getContractAccountID();
							final var cc = contractCall(
									TOP_LEVEL_CONTRACT,
									ContractResources.SUB_LEVEL_PAYABLE_ABI,
									subLevelSolidityAddr, 20);
							allRunFor(spec, cc);
						}),
						getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE),
						getAccountBalance(SUB_LEVEL_CONTRACT).hasTinyBars(20 + INITIAL_CONTRACT_BALANCE)

				);
	}

	private HapiApiSpec HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts() {
		final var ACCOUNT = "account";
		final var CONTRACT_FROM = "contract1";
		final var CONTRACT_TO = "contract2";
		return defaultHapiSpec("HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),

						fileCreate("contract1Bytecode").path(ContractResources.TRANSFERRING_CONTRACT).payingWith(
								ACCOUNT),
						contractCreate(CONTRACT_FROM).bytecode("contract1Bytecode").balance(10_000L).payingWith(ACCOUNT),

						contractCreate(CONTRACT_TO).bytecode("contract1Bytecode").balance(10_000L).payingWith(ACCOUNT),

						getContractInfo(CONTRACT_FROM).saveToRegistry("contract_from"),
						getContractInfo(CONTRACT_TO).saveToRegistry("contract_to"),
						getAccountInfo(ACCOUNT).savingSnapshot("accountInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var cto = spec.registry().getContractInfo("contract_to").getContractAccountID();

							var transferCall = contractCall(
									CONTRACT_FROM,
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									cto, 10)
									.payingWith(ACCOUNT).logged();
							allRunFor(spec, transferCall);
						})
				)
				.then(
						getAccountBalance(CONTRACT_FROM).hasTinyBars(10_000 - 10),
						getAccountBalance(CONTRACT_TO).hasTinyBars(10_000 + 10)
				);
	}

	private HapiApiSpec HSCS_EVM_010_ReceiverMustSignContractTx() {
		final var ACCOUNT = "acc";
		final var RECEIVER_KEY = "receiverKey";
		return defaultHapiSpec("HSCS_EVM_010_ReceiverMustSignContractTx")
				.given(
						newKeyNamed(RECEIVER_KEY),
						cryptoCreate(ACCOUNT)
								.balance(5 * ONE_HUNDRED_HBARS)
								.receiverSigRequired(true)
								.key(RECEIVER_KEY)
				)
				.when(
						getAccountInfo(ACCOUNT).savingSnapshot("accInfo"),
						fileCreate("bytecode")
								.path(ContractResources.TRANSFERRING_CONTRACT),
						contractCreate("contract")
								.bytecode("bytecode")
								.payingWith(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
				)
				.then(
						withOpContext((spec, log) -> {
							var acc = spec.registry().getAccountInfo("accInfo").getContractAccountID();
							var withoutReceiverSignature = contractCall(
									"contract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									acc, ONE_HUNDRED_HBARS / 2)
									.hasKnownStatus(INVALID_SIGNATURE);
							allRunFor(spec, withoutReceiverSignature);

							var withSignature = contractCall(
									"contract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									acc, ONE_HUNDRED_HBARS / 2)
									.payingWith(ACCOUNT)
									.signedBy(RECEIVER_KEY)
									.hasKnownStatus(SUCCESS);
							allRunFor(spec, withSignature);
						})
				);
	}

	private HapiApiSpec HSCS_EVM_010_MultiSignatureAccounts() {
		final var ACCOUNT = "acc";
		final var PAYER_KEY = "pkey";
		final var OTHER_KEY = "okey";
		final var KEY_LIST = "klist";
		return defaultHapiSpec("HSCS_EVM_010_MultiSignatureAccounts")
				.given(
						newKeyNamed(PAYER_KEY),
						newKeyNamed(OTHER_KEY),
						newKeyListNamed(KEY_LIST, List.of(PAYER_KEY, OTHER_KEY)),
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.key(KEY_LIST)
								.keyType(THRESHOLD)
				)
				.when(
						fileCreate("bytecode")
								.path(ContractResources.TRANSFERRING_CONTRACT),
						getAccountInfo(ACCOUNT).savingSnapshot("accInfo"),

						contractCreate("contract").bytecode("bytecode")
								.payingWith(ACCOUNT)
								.signedBy(PAYER_KEY)
								.adminKey(KEY_LIST).hasPrecheck(INVALID_SIGNATURE),

						contractCreate("contract").bytecode("bytecode")
								.payingWith(ACCOUNT)
								.signedBy(PAYER_KEY, OTHER_KEY)
								.balance(10)
								.adminKey(KEY_LIST)
				)
				.then(
						withOpContext((spec, log) -> {
							var acc = spec.registry().getAccountInfo("accInfo").getContractAccountID();
							var assertionWithOnlyOneKey = contractCall(
									"contract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									acc, 10)
									.payingWith(ACCOUNT)
									.signedBy(PAYER_KEY)
									.hasPrecheck(INVALID_SIGNATURE);
							allRunFor(spec, assertionWithOnlyOneKey);

							var assertionWithBothKeys = contractCall(
									"contract",
									ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
									acc, 10)
									.payingWith(ACCOUNT)
									.signedBy(PAYER_KEY, OTHER_KEY)
									.hasKnownStatus(SUCCESS);
							allRunFor(spec, assertionWithBothKeys);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
