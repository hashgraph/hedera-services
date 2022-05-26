package com.hedera.services.bdd.suites.contract.hapi;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeBuilder;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.core.CallTransaction;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.literalInitcodeFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getABIForContract;
import static com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.utils.contracts.SimpleBytesResult.bigIntResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.swirlds.common.utility.CommonUtils.unhex;

public class ContractCallSuite extends HapiApiSuite {
	private static final String defaultMaxAutoRenewPeriod =
			HapiSpecSetup.getDefaultNodeProps().get("ledger.autoRenewPeriod.maxDuration");

	private static final Logger log = LogManager.getLogger(ContractCallSuite.class);
	private static final long depositAmount = 1000;
	private static final long GAS_TO_OFFER = 2_000_000L;

	private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
	public static final String SIMPLE_UPDATE_CONTRACT = "SimpleUpdate";
	public static final String TRANSFERRING_CONTRACT = "Transferring";
	public static final String SIMPLE_STORAGE_CONTRACT = "SimpleStorage";

	public static void main(String... args) {
		new ContractCallSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
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
				deletedContractsCannotBeUpdated(),
				sendHbarsToAddressesMultipleTimes(),
				sendHbarsToDifferentAddresses(),
				sendHbarsFromDifferentAddressessToAddress(),
				sendHbarsFromAndToDifferentAddressess(),
				transferNegativeAmountOfHbars(),
				transferToCaller(),
				transferZeroHbarsToCaller(),
				transferZeroHbars(),
				sendHbarsToOuterContractFromDifferentAddresses(),
				sendHbarsToCallerFromDifferentAddresses(),
				bitcarbonTestStillPasses(),
				contractCreationStoragePriceMatchesFinalExpiry(),
				whitelistingAliasedContract(),
				cannotUseMirrorAddressOfAliasedContractInPrecompileMethod(),
				exchangeRatePrecompileWorks(),
				canMintAndTransferInSameContractOperation(),
				workingHoursDemo(),
		});
	}

	private HapiApiSpec whitelistingAliasedContract() {
		final var creationTxn = "creationTxn";
		final var mirrorWhitelistCheckTxn = "mirrorWhitelistCheckTxn";
		final var evmWhitelistCheckTxn = "evmWhitelistCheckTxn";

		final var WHITELISTER = "Whitelister";
		final var CREATOR = "Creator";

		final AtomicReference<String> childMirror = new AtomicReference<>();
		final AtomicReference<String> childEip1014 = new AtomicReference<>();

		return defaultHapiSpec("whitelistingAliasedContract")
				.given(
						sourcing(() -> createLargeFile(
								DEFAULT_PAYER, WHITELISTER, literalInitcodeFor("Whitelister"))),
						sourcing(() -> createLargeFile(
								DEFAULT_PAYER, CREATOR, literalInitcodeFor("Creator"))),
						withOpContext((spec, op) -> allRunFor(spec,
								contractCreate(WHITELISTER)
										.payingWith(DEFAULT_PAYER)
										.gas(GAS_TO_OFFER),
								contractCreate(CREATOR)
										.payingWith(DEFAULT_PAYER)
										.gas(GAS_TO_OFFER)
										.via(creationTxn)
						))
				).when(
						captureChildCreate2MetaFor(
								1, 0,
								"setup", creationTxn, childMirror, childEip1014),
						withOpContext((spec, op) -> allRunFor(spec,
								contractCall(WHITELISTER, "addToWhitelist", childEip1014.get())
										.payingWith(DEFAULT_PAYER),
								contractCallWithFunctionAbi(
										asContractString(
												contractIdFromHexedMirrorAddress(childMirror.get())),
										getABIFor(FUNCTION, "isWhitelisted", WHITELISTER),
										getNestedContractAddress(WHITELISTER, spec))
										.payingWith(DEFAULT_PAYER)
										.via(mirrorWhitelistCheckTxn),
								contractCall(CREATOR, "isWhitelisted",
										getNestedContractAddress(WHITELISTER, spec))
										.payingWith(DEFAULT_PAYER)
										.via(evmWhitelistCheckTxn)
						))
				).then(
						getTxnRecord(mirrorWhitelistCheckTxn).hasPriority(recordWith().contractCallResult(
								resultWith().contractCallResult(bigIntResult(1)))).logged(),
						getTxnRecord(evmWhitelistCheckTxn).hasPriority(recordWith().contractCallResult(
								resultWith().contractCallResult(bigIntResult(1)))).logged()
				);
	}

	private HapiApiSpec cannotUseMirrorAddressOfAliasedContractInPrecompileMethod() {
		final var creationTxn = "creationTxn";
		final var ASSOCIATOR = "Associator";

		final AtomicReference<String> childMirror = new AtomicReference<>();
		final AtomicReference<String> childEip1014 = new AtomicReference<>();
		final AtomicReference<TokenID> tokenID = new AtomicReference<>();

		return defaultHapiSpec("cannotUseMirrorAddressOfAliasedContractInPrecompileMethod")
				.given(
						cryptoCreate("Treasury"),
						sourcing(() -> createLargeFile(
								DEFAULT_PAYER, ASSOCIATOR, literalInitcodeFor("Associator"))),
						withOpContext((spec, op) -> allRunFor(spec,
								contractCreate(ASSOCIATOR)
										.payingWith(DEFAULT_PAYER)
										.bytecode(ASSOCIATOR)
										.gas(GAS_TO_OFFER)
										.via(creationTxn)
						))
				).when(
						withOpContext((spec, op) -> {
							allRunFor(spec,
									captureChildCreate2MetaFor(
											1, 0,
											"setup", creationTxn, childMirror, childEip1014),
									tokenCreate("TokenA")
											.initialSupply(100)
											.treasury("Treasury")
											.exposingCreatedIdTo(id -> {
												tokenID.set(asToken(id));
											})
							);
							final var create2address = childEip1014.get();
							final var mirrorAddress = childMirror.get();
							allRunFor(spec,
									contractCall(ASSOCIATOR, "associate", mirrorAddress,
											asAddress(tokenID.get()))
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
											.gas(GAS_TO_OFFER)
											.via("NOPE"),
									childRecordsCheck(
											"NOPE",
											CONTRACT_REVERT_EXECUTED,
											recordWith().status(INVALID_ACCOUNT_ID)),
									contractCall(ASSOCIATOR, "associate", create2address,
											asAddress(tokenID.get()))
											.gas(GAS_TO_OFFER)
							);
						})
				).then();
	}

	private HapiApiSpec contractCreationStoragePriceMatchesFinalExpiry() {
		final var toyMaker = "ToyMaker";
		final var createIndirectly = "CreateIndirectly";
		final var normalPayer = "normalPayer";
		final var longLivedPayer = "longLivedPayer";
		final var longLifetime = 100 * 7776000L;
		final AtomicLong normalPayerGasUsed = new AtomicLong();
		final AtomicLong longLivedPayerGasUsed = new AtomicLong();
		final AtomicReference<String> toyMakerMirror = new AtomicReference<>();

		return defaultHapiSpec("ContractCreationStoragePriceMatchesFinalExpiry")
				.given(
						overriding("ledger.autoRenewPeriod.maxDuration", "" + longLifetime),
						cryptoCreate(normalPayer),
						cryptoCreate(longLivedPayer).autoRenewSecs(longLifetime),
						uploadInitCode(toyMaker, createIndirectly),
						contractCreate(toyMaker)
								.exposingNumTo(num -> toyMakerMirror.set(
										asHexedSolidityAddress(0, 0, num))),
						sourcing(() -> contractCreate(createIndirectly)
								.autoRenewSecs(longLifetime)
								.payingWith(GENESIS)
						)
				).when(
						contractCall(toyMaker, "make")
								.payingWith(normalPayer)
								.exposingGasTo((status, gasUsed) -> normalPayerGasUsed.set(gasUsed)),
						contractCall(toyMaker, "make")
								.payingWith(longLivedPayer)
								.exposingGasTo((status, gasUsed) -> longLivedPayerGasUsed.set(gasUsed)),
						assertionsHold((spec, opLog) -> Assertions.assertEquals(
								normalPayerGasUsed.get(),
								longLivedPayerGasUsed.get(),
								"Payer expiry should not affect create storage cost")),
						// Verify that we are still charged a "typical" amount despite the payer and
						// the original sender contract having extremely long expiry dates
						sourcing(() -> contractCall(createIndirectly, "makeOpaquely", toyMakerMirror.get())
								.payingWith(longLivedPayer))
				).then(
						overriding("ledger.autoRenewPeriod.maxDuration", "" + defaultMaxAutoRenewPeriod)
				);
	}

	private HapiApiSpec bitcarbonTestStillPasses() {
		final var addressBook = "AddressBook";
		final var jurisdictions = "Jurisdictions";
		final var minters = "Minters";
		final var addJurisTxn = "addJurisTxn";
		final var historicalAddress = "1234567890123456789012345678901234567890";
		final AtomicReference<byte[]> nyJurisCode = new AtomicReference<>();
		final AtomicReference<byte[]> defaultPayerMirror = new AtomicReference<>();
		final AtomicReference<String> addressBookMirror = new AtomicReference<>();
		final AtomicReference<String> jurisdictionMirror = new AtomicReference<>();


		return defaultHapiSpec("BitcarbonTestStillPasses")
				.given(
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER),
						withOpContext((spec, opLog) -> defaultPayerMirror.set((unhex(spec.registry().getAccountInfo(DEFAULT_CONTRACT_SENDER).getContractAccountID())))),
						uploadInitCode(addressBook, jurisdictions),
						contractCreate(addressBook)
								.exposingNumTo(num -> addressBookMirror.set(
										asHexedSolidityAddress(0, 0, num)))
								.payingWith(DEFAULT_CONTRACT_SENDER),
						contractCreate(jurisdictions)
								.exposingNumTo(num -> jurisdictionMirror.set(
										asHexedSolidityAddress(0, 0, num)))
								.withExplicitParams(() -> explicitJurisdictionConsParams)
								.payingWith(DEFAULT_CONTRACT_SENDER),
						sourcing(() -> createLargeFile(DEFAULT_CONTRACT_SENDER, minters,
								bookInterpolated(
										literalInitcodeFor(minters).toByteArray(),
										addressBookMirror.get()))),
						contractCreate(minters)
								.withExplicitParams(() -> String.format(
										explicitMinterConsParamsTpl, jurisdictionMirror.get()))
								.payingWith(DEFAULT_CONTRACT_SENDER)
				).when(
						contractCall(minters)
								.withExplicitParams(() -> String.format(explicitMinterConfigParamsTpl, jurisdictionMirror.get())),
						contractCall(jurisdictions)
								.withExplicitParams(() -> explicitJurisdictionsAddParams)
								.via(addJurisTxn)
								.gas(1_000_000),
						getTxnRecord(addJurisTxn).exposingFilteredCallResultVia(
								getABIForContract(jurisdictions),
								event -> event.name.equals("JurisdictionAdded"),
								data -> nyJurisCode.set((byte[]) data.get(0))),
						sourcing(() -> logIt("NY juris code is " + CommonUtils.hex(nyJurisCode.get())))
				).then(
						sourcing(() -> contractCallLocal(jurisdictions, "isValid", nyJurisCode.get())
								.has(resultWith()
										.resultThruAbi(getABIFor(FUNCTION, "isValid", jurisdictions),
												isLiteralResult(new Object[]{Boolean.TRUE})))
						),
						contractCallLocal(minters, "seven")
								.has(resultWith()
										.resultThruAbi(getABIFor(FUNCTION, "seven", minters),
												isLiteralResult(new Object[]{BigInteger.valueOf(7L)}))),
						sourcing(() -> contractCallLocal(minters, "owner")
								.has(resultWith()
										.resultThruAbi(
												getABIFor(FUNCTION, "owner", minters),
												isLiteralResult(new Object[]{defaultPayerMirror.get()
												})))
						),
						sourcing(() -> contractCallLocal(jurisdictions, "owner")
								.has(resultWith()
										.resultThruAbi(
												getABIFor(FUNCTION, "owner", minters),
												isLiteralResult(new Object[]{defaultPayerMirror.get()
												})))
						),
						sourcing(() -> contractCall(
								minters, "add", historicalAddress, "Peter", nyJurisCode.get())
								.gas(1_000_000))
				);
	}

	private HapiApiSpec deletedContractsCannotBeUpdated() {
		final var contract = "SelfDestructCallable";

		return defaultHapiSpec("DeletedContractsCannotBeUpdated")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.gas(300_000)
				).when(
						contractCall(contract, "destroy")
								.deferStatusResolution()
				).then(
						contractUpdate(contract)
								.newMemo("Hi there!")
								.hasKnownStatus(INVALID_CONTRACT_ID)
				);
	}

	private HapiApiSpec workingHoursDemo() {
		final var gasToOffer = 4_000_000;
		final var contract = "WorkingHours";
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
						// we need a new user, expiry to 1 Jan 2100 costs 11M gas for token associate
						tokenCreate(ticketToken)
								.treasury(treasury)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.supplyType(TokenSupplyType.INFINITE)
								.adminKey(adminKey)
								.supplyKey(adminKey),
						mintToken(ticketToken, preMints).via(mint),
						burnToken(ticketToken, List.of(1L)).via(burn),
						uploadInitCode(contract)
				).when(
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							final var tokenId = registry.getTokenID(ticketToken);
							final var treasuryId = registry.getAccountID(treasury);
							final var creation = contractCreate(
									contract,
									tokenId.getTokenNum(), treasuryId.getAccountNum()
							)
									.gas(gasToOffer);
							allRunFor(spec, creation);
						}),
						newKeyNamed(newSupplyKey)
								.shape(KeyShape.CONTRACT.signedWith(contract)),
						tokenUpdate(ticketToken).supplyKey(newSupplyKey)
				).then(
						/* Take a ticket */
						contractCall(contract, "takeTicket")
								.alsoSigningWithFullPrefix(DEFAULT_CONTRACT_SENDER, treasury)
								.gas(4_000_000)
								.via(ticketTaking)
								.exposingResultTo(result -> {
									log.info("Explicit mint result is {}", result);
									ticketSerialNo.set(((BigInteger) result[0]).longValueExact());
								}),
						getTxnRecord(ticketTaking),
						getAccountBalance(DEFAULT_CONTRACT_SENDER).hasTokenBalance(ticketToken, 1L),
						/* Our ticket number is 3 (b/c of the two pre-mints), so we must call
						 * work twice before the contract will actually accept our ticket. */
						sourcing(() ->
								contractCall(contract, "workTicket", ticketSerialNo.get())
										.gas(2_000_000)
										.alsoSigningWithFullPrefix(DEFAULT_CONTRACT_SENDER)),
						getAccountBalance(DEFAULT_CONTRACT_SENDER).hasTokenBalance(ticketToken, 1L),
						sourcing(() ->
								contractCall(contract, "workTicket", ticketSerialNo.get())
										.gas(2_000_000)
										.alsoSigningWithFullPrefix(DEFAULT_CONTRACT_SENDER)
										.via(ticketWorking)),
						getAccountBalance(DEFAULT_CONTRACT_SENDER).hasTokenBalance(ticketToken, 0L),
						getTokenInfo(ticketToken).hasTotalSupply(1L),
						/* Review the history */
						getTxnRecord(ticketTaking).andAllChildRecords().logged(),
						getTxnRecord(ticketWorking).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec canMintAndTransferInSameContractOperation() {
		final AtomicReference<String> tokenMirrorAddr = new AtomicReference<>();
		final AtomicReference<String> aCivilianMirrorAddr = new AtomicReference<>();
		final var nfToken = "nfToken";
		final var multiKey = "multiKey";
		final var aCivilian = "aCivilian";
		final var treasuryContract = "SomeERC721Scenarios";
		final var mintAndTransferTxn = "mintAndTransferTxn";
		final var mintAndTransferAndBurnTxn = "mintAndTransferAndBurnTxn";

		return defaultHapiSpec("CanMintAndTransferInSameContractOperation")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(aCivilian).exposingCreatedIdTo(id ->
								aCivilianMirrorAddr.set(asHexedSolidityAddress(id))),
						uploadInitCode(treasuryContract),
						contractCreate(treasuryContract)
								.adminKey(multiKey),
						tokenCreate(nfToken)
								.supplyKey(multiKey)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(treasuryContract)
								.initialSupply(0)
								.exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(
										asHexedSolidityAddress(
												HapiPropertySource.asToken(idLit)))),
						mintToken(nfToken, List.of(
								// 1
								ByteString.copyFromUtf8("A penny for"),
								// 2
								ByteString.copyFromUtf8("the Old Guy")
						)),
						tokenAssociate(aCivilian, nfToken),
						cryptoTransfer(movingUnique(nfToken, 2L).between(treasuryContract, aCivilian))
				).when(
						sourcing(() -> contractCall(
								treasuryContract, "nonSequiturMintAndTransfer",
								tokenMirrorAddr.get(), aCivilianMirrorAddr.get()
						)
								.via(mintAndTransferTxn)
								.gas(4_000_000)
								.alsoSigningWithFullPrefix(multiKey))
				).then(
						getTokenInfo(nfToken).hasTotalSupply(4L),
						getTokenNftInfo(nfToken, 3L)
								.hasSerialNum(3L)
								.hasAccountID(aCivilian)
								.hasMetadata(ByteString.copyFrom(new byte[] { (byte) 0xee })),
						getTokenNftInfo(nfToken, 4L)
								.hasSerialNum(4L)
								.hasAccountID(aCivilian)
								.hasMetadata(ByteString.copyFrom(new byte[] { (byte) 0xff })),
						sourcing(() -> contractCall(
								treasuryContract, "nonSequiturMintAndTransferAndBurn",
								tokenMirrorAddr.get(), aCivilianMirrorAddr.get()
						)
								.via(mintAndTransferAndBurnTxn)
								.gas(4_000_000)
								.alsoSigningWithFullPrefix(multiKey, aCivilian))
				);
	}

	private HapiApiSpec exchangeRatePrecompileWorks() {
		final var valueToTinycentCall = "recoverUsd";
		final var rateAware = "ExchangeRatePrecompile";
		// Must send $6.66 USD to access the gated method
		final var minPriceToAccessGatedMethod = 666L;
		final var minValueToAccessGatedMethodAtCurrentRate = new AtomicLong();

		return defaultHapiSpec("ExchangeRatePrecompileWorks")
				.given(
						uploadInitCode(rateAware),
						contractCreate(rateAware, minPriceToAccessGatedMethod),
						withOpContext((spec, opLog) -> {
							final var rates = spec.ratesProvider().rates();
							minValueToAccessGatedMethodAtCurrentRate.set(
									minPriceToAccessGatedMethod * TINY_PARTS_PER_WHOLE
											* rates.getHbarEquiv() / rates.getCentEquiv());
							log.info("Requires {} tinybar of value to access the method",
									minValueToAccessGatedMethodAtCurrentRate::get);
						})
				).when(
						sourcing(() -> contractCall(rateAware, "gatedAccess")
								.sending(minValueToAccessGatedMethodAtCurrentRate.get() - 1)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
						sourcing(() -> contractCall(rateAware, "gatedAccess")
								.sending(minValueToAccessGatedMethodAtCurrentRate.get()))
				).then(
						sourcing(() -> contractCall(rateAware, "approxUsdValue")
								.sending(minValueToAccessGatedMethodAtCurrentRate.get())
								.via(valueToTinycentCall)),
						getTxnRecord(valueToTinycentCall).hasPriority(recordWith()
								.contractCallResult(resultWith()
										.resultViaFunctionName(
												"approxUsdValue",
												rateAware,
												isLiteralResult(new Object[] {
														BigInteger.valueOf(
																minPriceToAccessGatedMethod * TINY_PARTS_PER_WHOLE)
												})))),
						sourcing(() -> contractCall(rateAware, "invalidCall")
								.sending(minValueToAccessGatedMethodAtCurrentRate.get())
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED))
				);
	}

	private HapiApiSpec imapUserExercise() {
		final var contract = "User";
		final var insert1To4 = "insert1To10";
		final var insert2To8 = "insert2To8";
		final var insert3To16 = "insert3To16";
		final var remove2 = "remove2";
		final var gasToOffer = 400_000;

		return defaultHapiSpec("ImapUserExercise")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
				).when().then(
						contractCall(contract, "insert", 1, 4)
								.gas(gasToOffer)
								.via(insert1To4),
						contractCall(contract, "insert", 2, 8)
								.gas(gasToOffer)
								.via(insert2To8),
						contractCall(contract, "insert", 3, 16)
								.gas(gasToOffer)
								.via(insert3To16),
						contractCall(contract, "remove", 2)
								.gas(gasToOffer)
								.via(remove2)
				);
	}

	// For this test we use refusingEthConversion() for the Eth Call isomer,
	// since we should modify the expected balances and change the test itself in order to pass with Eth Calls
	HapiApiSpec ocToken() {
		final var contract = "OcToken";

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

						uploadInitCode(contract),
						contractCreate(contract, 1_000_000L, "OpenCrowd Token", "OCT")
								.gas(250_000L)
								.payingWith("tokenIssuer")
								.via("tokenCreateTxn").logged()
				).when(
						assertionsHold((spec, ctxLog) -> {
							final var issuerEthAddress = spec.registry().getAccountInfo("tokenIssuerAcctInfo")
									.getContractAccountID();
							final var aliceEthAddress = spec.registry().getAccountInfo("AliceAcctInfo")
									.getContractAccountID();
							final var bobEthAddress = spec.registry().getAccountInfo("BobAcctInfo")
									.getContractAccountID();
							final var carolEthAddress = spec.registry().getAccountInfo("CarolAcctInfo")
									.getContractAccountID();
							final var daveEthAddress = spec.registry().getAccountInfo("DaveAcctInfo")
									.getContractAccountID();

							final var subop1 = getContractInfo(contract)
									.nodePayment(10L)
									.saveToRegistry("tokenContract");

							final var subop3 = contractCallLocal(contract, "decimals")
									.saveResultTo("decimals")
									.payingWith("tokenIssuer");

							// Note: This contract call will cause a INSUFFICIENT_TX_FEE error, not sure why.
							final var subop4 = contractCallLocal(contract, "symbol")
									.saveResultTo("token_symbol")
									.payingWith("tokenIssuer")
									.hasAnswerOnlyPrecheckFrom(OK, INSUFFICIENT_TX_FEE);

							final var subop5 = contractCallLocal(contract, "balanceOf", issuerEthAddress)
									.gas(250_000L)
									.saveResultTo("issuerTokenBalance");

							allRunFor(spec, subop1, subop3, subop4, subop5);

							final var funcSymbol =
									CallTransaction.Function.fromJsonInterface(getABIFor(FUNCTION, "symbol", contract));

							final var symbol = getValueFromRegistry(spec, "token_symbol", funcSymbol);

							ctxLog.info("symbol: [{}]", symbol);

							Assertions.assertEquals(
									"", symbol,
									"TokenIssuer's symbol should be fixed value"); // should be "OCT" as expected

							final var funcDecimals = CallTransaction.Function.fromJsonInterface(
									getABIFor(FUNCTION, "decimals", contract));

							//long decimals = getLongValueFromRegistry(spec, "decimals", function);
							final BigInteger val = getValueFromRegistry(spec, "decimals", funcDecimals);
							final var decimals = val.longValue();

							ctxLog.info("decimals {}", decimals);
							Assertions.assertEquals(
									3, decimals,
									"TokenIssuer's decimals should be fixed value");

							final long tokenMultiplier = (long) Math.pow(10, decimals);

							final var function = CallTransaction.Function.fromJsonInterface(
									getABIFor(FUNCTION, "balanceOf", contract));

							long issuerBalance = ((BigInteger) getValueFromRegistry(spec, "issuerTokenBalance",
									function)).longValue();

							ctxLog.info("initial balance of Issuer {}", issuerBalance / tokenMultiplier);
							Assertions.assertEquals(
									1_000_000, issuerBalance / tokenMultiplier,
									"TokenIssuer's initial token balance should be 1_000_000");

							//  Do token transfers
							final var subop6 = contractCall(contract, "transfer",
									aliceEthAddress, 1000 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("tokenIssuer")
									.refusingEthConversion();

							final var subop7 = contractCall(contract, "transfer",
									bobEthAddress, 2000 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("tokenIssuer")
									.refusingEthConversion();

							final var subop8 = contractCall(contract, "transfer",
									carolEthAddress, 500 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Bob")
									.refusingEthConversion();

							final var subop9 = contractCallLocal(contract, "balanceOf", aliceEthAddress)
									.gas(250_000L)
									.saveResultTo("aliceTokenBalance");

							final var subop10 = contractCallLocal(contract, "balanceOf", carolEthAddress)
									.gas(250_000L)
									.saveResultTo("carolTokenBalance");

							final var subop11 = contractCallLocal(contract, "balanceOf", bobEthAddress)
									.gas(250_000L)
									.saveResultTo("bobTokenBalance");

							allRunFor(spec, subop6, subop7, subop8, subop9, subop10, subop11);

							var aliceBalance = ((BigInteger) getValueFromRegistry(spec, "aliceTokenBalance",
									function)).longValue();
							var bobBalance = ((BigInteger) getValueFromRegistry(spec, "bobTokenBalance",
									function)).longValue();
							var carolBalance = ((BigInteger) getValueFromRegistry(spec, "carolTokenBalance",
									function)).longValue();

							ctxLog.info("aliceBalance  {}", aliceBalance / tokenMultiplier);
							ctxLog.info("bobBalance  {}", bobBalance / tokenMultiplier);
							ctxLog.info("carolBalance  {}", carolBalance / tokenMultiplier);

							Assertions.assertEquals(
									1000, aliceBalance / tokenMultiplier,
									"Alice's token balance should be 1_000");

							final var subop12 = contractCall(contract, "approve",
									daveEthAddress, 200 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Alice")
									.refusingEthConversion();

							final var subop13 = contractCall(contract, "transferFrom",
									aliceEthAddress, bobEthAddress, 100 * tokenMultiplier)
									.gas(250_000L)
									.payingWith("Dave")
									.refusingEthConversion();

							final var subop14 = contractCallLocal(contract, "balanceOf", aliceEthAddress)
									.gas(250_000L)
									.saveResultTo("aliceTokenBalance");

							final var subop15 = contractCallLocal(contract, "balanceOf", bobEthAddress)
									.gas(250_000L)
									.saveResultTo("bobTokenBalance");

							final var subop16 = contractCallLocal(contract, "balanceOf", carolEthAddress)
									.gas(250_000L)
									.saveResultTo("carolTokenBalance");

							final var subop17 = contractCallLocal(contract, "balanceOf", daveEthAddress)
									.gas(250_000L)
									.saveResultTo("daveTokenBalance");

							final var subop18 = contractCallLocal(contract, "balanceOf", issuerEthAddress)
									.gas(250_000L)
									.saveResultTo("issuerTokenBalance");

							allRunFor(spec, subop12, subop13, subop14, subop15, subop16, subop17,
									subop18);

							final var daveBalance = ((BigInteger) getValueFromRegistry(spec, "daveTokenBalance",
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
						getContractRecords(contract).hasCostAnswerPrecheck(NOT_SUPPORTED),
						getContractRecords(contract).nodePayment(100L).hasAnswerOnlyPrecheck(NOT_SUPPORTED)
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
		final var inlineTestContract = "InlineTest";

		return defaultHapiSpec("smartContractInlineAssemblyCheck")
				.given(
						cryptoCreate("payer")
								.balance(10_000_000_000_000L),
						uploadInitCode(SIMPLE_STORAGE_CONTRACT, inlineTestContract)
				).when(
						contractCreate(SIMPLE_STORAGE_CONTRACT),
						contractCreate(inlineTestContract)
				).then(
						assertionsHold((spec, ctxLog) -> {

							final var subop1 = getContractInfo(SIMPLE_STORAGE_CONTRACT)
									.nodePayment(10L)
									.saveToRegistry("simpleStorageKey");

							final var subop2 = getAccountInfo("payer")
									.savingSnapshot("payerAccountInfo");
							allRunFor(spec, subop1, subop2);

							final var simpleStorageContractInfo =
									spec.registry().getContractInfo("simpleStorageKey");
							final var contractAddress = simpleStorageContractInfo.getContractAccountID();

							final var subop3 = contractCallLocal(inlineTestContract, "getCodeSize", contractAddress
							)
									.saveResultTo("simpleStorageContractCodeSizeBytes")
									.gas(300_000L);

							allRunFor(spec, subop3);

							var result = spec.registry().getBytes("simpleStorageContractCodeSizeBytes");

							final var funcJson = getABIFor(FUNCTION, "getCodeSize", inlineTestContract).replaceAll("'", "\"");
							final var function = CallTransaction.Function.fromJsonInterface(funcJson);

							var codeSize = 0;
							if (result != null && result.length > 0) {
								final var retResults = function.decodeResult(result);
								if (retResults != null && retResults.length > 0) {
									final var retBi = (BigInteger) retResults[0];
									codeSize = retBi.intValue();
								}
							}

							ctxLog.info("Contract code size {}", codeSize);
							Assertions.assertNotEquals(
									0, codeSize,
									"Real smart contract code size should be greater than 0");


							final var payerAccountInfo = spec.registry().getAccountInfo(
									"payerAccountInfo");
							final var acctAddress = payerAccountInfo.getContractAccountID();

							final var subop4 = contractCallLocal(inlineTestContract, "getCodeSize", acctAddress
							)
									.saveResultTo("fakeCodeSizeBytes")
									.gas(300_000L);

							allRunFor(spec, subop4);
							result = spec.registry().getBytes("fakeCodeSizeBytes");

							codeSize = 0;
							if (result != null && result.length > 0) {
								final var retResults = function.decodeResult(result);
								if (retResults != null && retResults.length > 0) {
									final var retBi = (BigInteger) retResults[0];
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
		final var contract = "Fuse";
		return defaultHapiSpec("MultipleSelfDestructsAreSafe")
				.given(
						uploadInitCode(contract),
						contractCreate(contract).gas(300_000)
				).when(
						contractCall(contract, "light").via("lightTxn")
								.scrambleTxnBody(
										tx -> {
											System.out.println(" tx - " + Bytes.wrap(tx.toByteArray()));
											return tx;
										})
				).then(
						getTxnRecord("lightTxn").logged().exposingTo(
								tr -> System.out.println(Bytes.of(tr.toByteArray())))
				);
	}

	HapiApiSpec depositSuccess() {
		return defaultHapiSpec("DepositSuccess")
				.given(
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						contractCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount
						)
								.via("payTxn")
								.sending(depositAmount)
				).then(
						getTxnRecord("payTxn")
								.hasPriority(recordWith().contractCallResult(
										resultWith().logs(inOrder())))
				);
	}

	HapiApiSpec multipleDepositSuccess() {
		return defaultHapiSpec("MultipleDepositSuccess")
				.given(
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				)
				.when()
				.then(
						withOpContext((spec, opLog) -> {
							for (int i = 0; i < 10; i++) {
								final var subOp1 = balanceSnapshot("payerBefore", PAY_RECEIVABLE_CONTRACT);
								final var subOp2 = contractCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount
								)
										.via("payTxn")
										.sending(depositAmount);
								final var subOp3 = getAccountBalance(PAY_RECEIVABLE_CONTRACT)
										.hasTinyBars(changeFromSnapshot("payerBefore", +depositAmount));
								allRunFor(spec, subOp1, subOp2, subOp3);
							}
						})
				);
	}

	HapiApiSpec depositDeleteSuccess() {
		final var initBalance = 7890L;
		return defaultHapiSpec("DepositDeleteSuccess")
				.given(
						cryptoCreate("beneficiary").balance(initBalance),
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						contractCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount
						)
								.via("payTxn")
								.sending(depositAmount)

				).then(
						contractDelete(PAY_RECEIVABLE_CONTRACT).transferAccount("beneficiary"),
						getAccountBalance("beneficiary")
								.hasTinyBars(initBalance + depositAmount)
				);
	}

	HapiApiSpec payableSuccess() {
		return defaultHapiSpec("PayableSuccess")
				.given(
						UtilVerbs.overriding("contracts.maxGas", "1000000"),
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD).gas(1_000_000)
				).when(
						contractCall(PAY_RECEIVABLE_CONTRACT).via("payTxn").sending(depositAmount)
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
						uploadInitCode(SIMPLE_UPDATE_CONTRACT)
				).when(
						contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
						contractCall(SIMPLE_UPDATE_CONTRACT,
								"set", 5, 42).gas(300_000L),
						contractCall(SIMPLE_UPDATE_CONTRACT,
								"del",
								"0x0000000000000000000000000000000000000002")
								.gas(1_000_000L)
				).then(
						contractCall(SIMPLE_UPDATE_CONTRACT,
								"set", 15, 434).gas(350_000L)
								.hasKnownStatus(CONTRACT_DELETED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	HapiApiSpec insufficientGas() {
		return defaultHapiSpec("InsufficientGas")
				.given(
						uploadInitCode(SIMPLE_STORAGE_CONTRACT),
						contractCreate(SIMPLE_STORAGE_CONTRACT).adminKey(THRESHOLD),
						getContractInfo(SIMPLE_STORAGE_CONTRACT).saveToRegistry("simpleStorageInfo")
				).when().then(
						contractCall(SIMPLE_STORAGE_CONTRACT, "get"
						)
								.via("simpleStorageTxn")
								.gas(0L)
								.hasKnownStatus(INSUFFICIENT_GAS),
						getTxnRecord("simpleStorageTxn").logged()
				);
	}

	HapiApiSpec insufficientFee() {
		final var contract = "CreateTrivial";

		return defaultHapiSpec("InsufficientFee")
				.given(
						cryptoCreate("accountToPay"),
						uploadInitCode(contract),
						contractCreate(contract)
				).when().then(
						contractCall(contract, "create")
								.fee(0L)
								.payingWith("accountToPay")
								.hasPrecheck(INSUFFICIENT_TX_FEE));
	}

	HapiApiSpec nonPayable() {
		final var contract = "CreateTrivial";

		return defaultHapiSpec("NonPayable")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						contractCall(contract, "create")
								.via("callTxn")
								.sending(depositAmount)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				).then(
						getTxnRecord("callTxn").hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder())))
				);
	}

	HapiApiSpec invalidContract() {
		final var invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();
		final var function = getABIFor(FUNCTION, "getIndirect", "CreateTrivial");

		return defaultHapiSpec("InvalidContract")
				.given(
						withOpContext((spec, ctxLog) -> {
							spec.registry().saveContractId("invalid", asContract("1.1.1"));
						})
				).when().then(
						contractCallWithFunctionAbi("invalid", function)
								.hasKnownStatus(INVALID_CONTRACT_ID));
	}

	private HapiApiSpec resultSizeAffectsFees() {
		final var contract = "VerboseDeposit";
		final var TRANSFER_AMOUNT = 1_000L;
		BiConsumer<TransactionRecord, Logger> RESULT_SIZE_FORMATTER = (record, txnLog) -> {
			final var result = record.getContractCallResult();
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
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						contractCall(contract, "deposit", TRANSFER_AMOUNT, 0, "So we out-danced thought..."
						)
								.via("noLogsCallTxn")
								.sending(TRANSFER_AMOUNT),
						contractCall(contract, "deposit", TRANSFER_AMOUNT, 5, "So we out-danced thought..."
						)
								.via("loggedCallTxn")
								.sending(TRANSFER_AMOUNT)

				).then(
						assertionsHold((spec, assertLog) -> {
							HapiGetTxnRecord noLogsLookup =
									QueryVerbs.getTxnRecord("noLogsCallTxn").loggedWith(RESULT_SIZE_FORMATTER);
							HapiGetTxnRecord logsLookup =
									QueryVerbs.getTxnRecord("loggedCallTxn").loggedWith(RESULT_SIZE_FORMATTER);
							allRunFor(spec, noLogsLookup, logsLookup);
							final var unloggedRecord =
									noLogsLookup.getResponse().getTransactionGetRecord().getTransactionRecord();
							final var loggedRecord =
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
		final var civilian = "civilian";
		return defaultHapiSpec("smartContractFailFirst")
				.given(
						uploadInitCode(SIMPLE_STORAGE_CONTRACT),
						cryptoCreate(civilian).balance(ONE_MILLION_HBARS).payingWith(GENESIS)
				).when(
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot("balanceBefore0", civilian);
							final var subop2 =
									contractCreate(SIMPLE_STORAGE_CONTRACT)
											.balance(0)
											.payingWith(civilian)
											.gas(1)
											.hasKnownStatus(INSUFFICIENT_GAS)
											.via("failInsufficientGas");
							final var subop3 = getTxnRecord("failInsufficientGas");
							allRunFor(spec, subop1, subop2, subop3);
							final var delta = subop3.getResponseRecord().getTransactionFee();
							final var subop4 = getAccountBalance(civilian).hasTinyBars(
									changeFromSnapshot("balanceBefore0", -delta));
							allRunFor(spec, subop4);
						}),
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot("balanceBefore1", civilian);
							final var subop2 = contractCreate(SIMPLE_STORAGE_CONTRACT)
									.balance(100_000_000_000L)
									.payingWith(civilian)
									.gas(250_000L)
									.via("failInvalidInitialBalance")
									.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							final var subop3 = getTxnRecord("failInvalidInitialBalance");
							allRunFor(spec, subop1, subop2, subop3);
							final var delta = subop3.getResponseRecord().getTransactionFee();
							final var subop4 = getAccountBalance(civilian).hasTinyBars(
									changeFromSnapshot("balanceBefore1", -delta));
							allRunFor(spec, subop4);
						}),
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot("balanceBefore2", civilian);
							final var subop2 = contractCreate(SIMPLE_STORAGE_CONTRACT)
									.balance(0L)
									.payingWith(civilian)
									.gas(250_000L)
									.hasKnownStatus(SUCCESS)
									.via("successWithZeroInitialBalance");
							final var subop3 = getTxnRecord("successWithZeroInitialBalance");
							allRunFor(spec, subop1, subop2, subop3);
							final var delta = subop3.getResponseRecord().getTransactionFee();
							final var subop4 = getAccountBalance(civilian).hasTinyBars(
									changeFromSnapshot("balanceBefore2", -delta));
							allRunFor(spec, subop4);
						}),
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot("balanceBefore3", civilian);
							final var subop2 = contractCall(SIMPLE_STORAGE_CONTRACT, "set", 999_999L)
									.payingWith(civilian)
									.gas(300_000L)
									.hasKnownStatus(SUCCESS)
									//ContractCall and EthereumTransaction gas fees differ
									.refusingEthConversion()
									.via("setValue");
							final var subop3 = getTxnRecord("setValue");
							allRunFor(spec, subop1, subop2, subop3);
							final var delta = subop3.getResponseRecord().getTransactionFee();
							final var subop4 = getAccountBalance(civilian).hasTinyBars(
									changeFromSnapshot("balanceBefore3", -delta));
							allRunFor(spec, subop4);
						}),
						withOpContext((spec, ignore) -> {
							final var subop1 = balanceSnapshot("balanceBefore4", civilian);
							final var subop2 = contractCall(SIMPLE_STORAGE_CONTRACT, "get")
									.payingWith(civilian)
									.gas(300_000L)
									.hasKnownStatus(SUCCESS)
									//ContractCall and EthereumTransaction gas fees differ
									.refusingEthConversion()
									.via("getValue");
							final var subop3 = getTxnRecord("getValue");
							allRunFor(spec, subop1, subop2, subop3);
							final var delta = subop3.getResponseRecord().getTransactionFee();

							final var subop4 = getAccountBalance(civilian).hasTinyBars(
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
		final var contract = "PayTestSelfDestruct";

		return defaultHapiSpec("payTestSelfDestructCall")
				.given(
						cryptoCreate("payer").balance(1_000_000_000_000L).logged(),
						cryptoCreate("receiver").balance(1_000L),
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var subop1 = contractCall(contract, "deposit", 1_000L
									)
											.payingWith("payer")
											.gas(300_000L)
											.via("deposit")
											.sending(1_000L);

									final var subop2 = contractCall(contract, "getBalance"
									)
											.payingWith("payer")
											.gas(300_000L)
											.via("getBalance");

									final var contractAccountId = asId(contract, spec);
									final var subop3 = contractCall(contract, "killMe",
											contractAccountId.getAccountNum()
									)
											.payingWith("payer")
											.gas(300_000L)
											.hasKnownStatus(OBTAINER_SAME_CONTRACT_ID);

									final var subop4 = contractCall(contract, "killMe", 999_999L
									)
											.payingWith("payer")
											.gas(300_000L)
											.hasKnownStatus(INVALID_SOLIDITY_ADDRESS);

									final var receiverAccountId = asId("receiver", spec);
									final var subop5 = contractCall(contract, "killMe",
											receiverAccountId.getAccountNum()
									)
											.payingWith("payer")
											.gas(300_000L)
											.via("selfDestruct")
											.hasKnownStatus(SUCCESS);

									allRunFor(spec, subop1, subop2, subop3, subop4, subop5);
								}
						)
				).then(
						getTxnRecord("deposit"),
						getTxnRecord("getBalance")
								.hasPriority(
										recordWith()
												.contractCallResult(
														resultWith()
																.resultViaFunctionName("getBalance", contract,
																		isLiteralResult(
																				new Object[] { BigInteger.valueOf(
																						1_000L) })
																)
												)
								),
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
						uploadInitCode(TRANSFERRING_CONTRACT)
				).when(
						contractCreate(TRANSFERRING_CONTRACT).gas(300_000L).balance(5000L)
				).then(
						withOpContext((spec, opLog) -> {
							final var accountAddress = spec.registry()
									.getAccountInfo("receivableSigReqAccountInfo").getContractAccountID();
							final var receivableAccountKey = spec.registry()
									.getAccountInfo("receivableSigReqAccountInfo").getKey();
							final var contractCallerKey = spec.registry()
									.getAccountInfo("contractCallerInfo").getKey();
							spec.registry().saveKey("receivableKey", receivableAccountKey);
							spec.registry().saveKey("contractCallerKey", contractCallerKey);
							/* if any of the keys are missing, INVALID_SIGNATURE is returned */
							final var call = contractCall(TRANSFERRING_CONTRACT, "transferToAddress",
									accountAddress, 1
							)
									.payingWith("contractCaller")
									.gas(300_000)
									.alsoSigningWithFullPrefix("receivableKey");
							/* calling with the receivableSigReqAccount should pass without adding keys */
							final var callWithReceivable = contractCall(TRANSFERRING_CONTRACT, "transferToAddress",
									accountAddress, 1
							)
									.payingWith("receivableSigReqAccount")
									.gas(300_000)
									.hasKnownStatus(SUCCESS);
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
						uploadInitCode(TRANSFERRING_CONTRACT)
				).when(
						contractCreate(TRANSFERRING_CONTRACT).gas(300_000L).balance(5000L)
				).then(
						withOpContext((spec, opLog) -> {
							final var accountAddress = spec.registry()
									.getAccountInfo("receivableSigReqAccountInfo").getContractAccountID();
							final var call = contractCall(TRANSFERRING_CONTRACT,
									"transferToAddress",
									accountAddress, 1).gas(300_000).hasKnownStatus(INVALID_SIGNATURE);
							allRunFor(spec, call);
						})
				);
	}

	private HapiApiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
		return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "5"),
						uploadInitCode(SIMPLE_UPDATE_CONTRACT)
				).when(
						contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
						contractCall(SIMPLE_UPDATE_CONTRACT, "set", 5, 42
						)
								.gas(300_000L)
								.via("callTX")
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
						uploadInitCode(SIMPLE_UPDATE_CONTRACT)
				).when(
						contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
						contractCall(SIMPLE_UPDATE_CONTRACT, "set", 5, 42
						)
								.gas(300_000L)
								.via("callTX")
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
						uploadInitCode(SIMPLE_UPDATE_CONTRACT),
						contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
						UtilVerbs.overriding("contracts.maxGas", "100")
				).when().then(
						contractCall(SIMPLE_UPDATE_CONTRACT, "set", 5, 42).gas(101L
								)
								.hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec HSCS_EVM_006_ContractHBarTransferToAccount() {
		final var ACCOUNT = "account";

		return defaultHapiSpec("HSCS_EVM_006_ContractHBarTransferToAccount")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver").balance(10_000L),
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
						getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry("contract_from"),
						getAccountInfo(ACCOUNT).savingSnapshot("accountInfo"),
						getAccountInfo("receiver").savingSnapshot("receiverInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							final var receiverAddr = spec.registry().getAccountInfo(
									"receiverInfo").getContractAccountID();
							final var transferCall = contractCall(TRANSFERRING_CONTRACT, "transferToAddress",
									receiverAddr, 10
							)
									.payingWith(ACCOUNT)
									.logged();
							allRunFor(spec, transferCall);
						})
				)
				.then(
						getAccountBalance("receiver").hasTinyBars(10_000 + 10)
				);
	}

	private HapiApiSpec HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts() {
		final var ACCOUNT = "account";
		final var topLevelContract = "TopLevelTransferring";
		final var subLevelContract = "SubLevelTransferring";
		final var INITIAL_CONTRACT_BALANCE = 100;

		return defaultHapiSpec("HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						uploadInitCode(topLevelContract, subLevelContract)
				)
				.when(
						contractCreate(topLevelContract).payingWith(ACCOUNT).balance(INITIAL_CONTRACT_BALANCE),
						contractCreate(subLevelContract).payingWith(ACCOUNT).balance(INITIAL_CONTRACT_BALANCE)
				)
				.then(
						contractCall(topLevelContract).sending(10).payingWith(ACCOUNT),
						getAccountBalance(topLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE + 10),

						contractCall(topLevelContract, "topLevelTransferCall"
						)
								.sending(10)
								.payingWith(ACCOUNT),
						getAccountBalance(topLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE + 20),

						contractCall(topLevelContract, "topLevelNonPayableCall"
						)
								.sending(10)
								.payingWith(ACCOUNT)
								.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
						getAccountBalance(topLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE + 20),

						getContractInfo(topLevelContract).saveToRegistry("tcinfo"),
						getContractInfo(subLevelContract).saveToRegistry("scinfo"),

						/* sub-level non-payable contract call */
						assertionsHold((spec, log) -> {
							final var subLevelSolidityAddr = spec.registry().getContractInfo(
									"scinfo").getContractAccountID();
							final var cc = contractCall(subLevelContract, "subLevelNonPayableCall",
									subLevelSolidityAddr, 20L
							)
									.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);
							allRunFor(spec, cc);
						}),
						getAccountBalance(topLevelContract).hasTinyBars(20 + INITIAL_CONTRACT_BALANCE),
						getAccountBalance(subLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE),

						/* sub-level payable contract call */
						assertionsHold((spec, log) -> {
							final var subLevelSolidityAddr = spec.registry().getContractInfo(
									"scinfo").getContractAccountID();
							final var cc = contractCall(
									topLevelContract,
									"subLevelPayableCall",
									subLevelSolidityAddr, 20);
							allRunFor(spec, cc);
						}),
						getAccountBalance(topLevelContract).hasTinyBars(INITIAL_CONTRACT_BALANCE),
						getAccountBalance(subLevelContract).hasTinyBars(20 + INITIAL_CONTRACT_BALANCE)

				);
	}

	private HapiApiSpec HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts() {
		final var to = "To";
		final var ACCOUNT = "account";

		return defaultHapiSpec("HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
						contractCustomCreate(TRANSFERRING_CONTRACT, to).balance(10_000L).payingWith(ACCOUNT),
						getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry("contract_from"),
						getContractInfo(TRANSFERRING_CONTRACT + to).saveToRegistry("contract_to"),
						getAccountInfo(ACCOUNT).savingSnapshot("accountInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var cto =
									spec.registry().getContractInfo(TRANSFERRING_CONTRACT + to).getContractAccountID();
							var transferCall = contractCall(TRANSFERRING_CONTRACT, "transferToAddress",
									cto, 10
							)
									.payingWith(ACCOUNT)
									.logged();
							allRunFor(spec, transferCall);
						})
				)
				.then(
						getAccountBalance(TRANSFERRING_CONTRACT).hasTinyBars(10_000 - 10),
						getAccountBalance(TRANSFERRING_CONTRACT + to).hasTinyBars(10_000 + 10)
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
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).payingWith(ACCOUNT).balance(ONE_HUNDRED_HBARS)
				)
				.then(
						withOpContext(
								(spec, log) -> {
									final var acc = spec.registry().getAccountInfo("accInfo").getContractAccountID();
									final var withoutReceiverSignature = contractCall(TRANSFERRING_CONTRACT,
											"transferToAddress", acc, ONE_HUNDRED_HBARS / 2
									)
											.hasKnownStatus(INVALID_SIGNATURE);
									allRunFor(spec, withoutReceiverSignature);

									final var withSignature = contractCall(TRANSFERRING_CONTRACT,
											"transferToAddress", acc, ONE_HUNDRED_HBARS / 2
									)
											.payingWith(ACCOUNT)
											.signedBy(RECEIVER_KEY)
											.hasKnownStatus(SUCCESS);
									allRunFor(spec, withSignature);
								}
						)
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
						uploadInitCode(TRANSFERRING_CONTRACT),
						getAccountInfo(ACCOUNT).savingSnapshot("accInfo"),

						contractCreate(TRANSFERRING_CONTRACT)
								.payingWith(ACCOUNT)
								.signedBy(PAYER_KEY)
								.adminKey(KEY_LIST).hasPrecheck(INVALID_SIGNATURE),

						contractCreate(TRANSFERRING_CONTRACT)
								.payingWith(ACCOUNT)
								.signedBy(PAYER_KEY, OTHER_KEY)
								.balance(10)
								.adminKey(KEY_LIST)
				)
				.then(
						withOpContext(
								(spec, log) -> {
									final var acc = spec.registry().getAccountInfo("accInfo").getContractAccountID();
									final var assertionWithOnlyOneKey = contractCall(TRANSFERRING_CONTRACT,
											"transferToAddress", acc, 10
									)
											.payingWith(ACCOUNT)
											.signedBy(PAYER_KEY)
											.hasPrecheck(INVALID_SIGNATURE)
											.refusingEthConversion();
									allRunFor(spec, assertionWithOnlyOneKey);

									final var assertionWithBothKeys = contractCall(TRANSFERRING_CONTRACT,
											"transferToAddress", acc, 10
									)
											.payingWith(ACCOUNT)
											.signedBy(PAYER_KEY, OTHER_KEY)
											.hasKnownStatus(SUCCESS)
											.refusingEthConversion();
									allRunFor(spec, assertionWithBothKeys);
								}
						)
				);
	}

	private HapiApiSpec sendHbarsToAddressesMultipleTimes() {
		final var ACCOUNT = "account";
		return defaultHapiSpec("sendHbarsToAddressesMultipleTimes")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver").balance(10_000L),
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),
						getAccountInfo("receiver").savingSnapshot("receiverInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiverAddr = spec.registry().getAccountInfo("receiverInfo").getContractAccountID();
							var transferCall = contractCall(
									TRANSFERRING_CONTRACT,
									"transferToAddressMultipleTimes",
									receiverAddr, 64)
									.payingWith(ACCOUNT).logged();
							allRunFor(spec, transferCall);
						})
				)
				.then(
						getAccountBalance("receiver").hasTinyBars(10_000L + 127L),
						sourcing(() -> getContractInfo(TRANSFERRING_CONTRACT)
								.has(contractWith().balance(10_000L - 127L)))
				);
	}

	private HapiApiSpec sendHbarsToDifferentAddresses() {
		final var ACCOUNT = "account";
		return defaultHapiSpec("sendHbarsToDifferentAddresses")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver1").balance(10_000L),
						cryptoCreate("receiver2").balance(10_000L),
						cryptoCreate("receiver3").balance(10_000L),
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),

						getAccountInfo("receiver1").savingSnapshot("receiver1Info"),
						getAccountInfo("receiver2").savingSnapshot("receiver2Info"),
						getAccountInfo("receiver3").savingSnapshot("receiver3Info")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiver1Addr = spec.registry().getAccountInfo("receiver1Info").getContractAccountID();
							var receiver2Addr = spec.registry().getAccountInfo("receiver2Info").getContractAccountID();
							var receiver3Addr = spec.registry().getAccountInfo("receiver3Info").getContractAccountID();

							var transferCall = contractCall(TRANSFERRING_CONTRACT, "transferToDifferentAddresses",
									receiver1Addr, receiver2Addr, receiver3Addr, 20
							)
									.payingWith(ACCOUNT).logged();
							allRunFor(spec, transferCall);
						})
				)
				.then(
						getAccountBalance("receiver1").hasTinyBars(10_000L + 20L),
						getAccountBalance("receiver2").hasTinyBars(10_000L + 10L),
						getAccountBalance("receiver3").hasTinyBars(10_000L + 5L),
						sourcing(() -> getContractInfo(TRANSFERRING_CONTRACT)
								.has(contractWith().balance(10_000L - 35L)))
				);
	}

	private HapiApiSpec sendHbarsFromDifferentAddressessToAddress() {
		final var ACCOUNT = "account";
		final var NESTED_TRANSFERRING_CONTRACT = "NestedTransferringContract";
		final var NESTED_CONTRACT = "NestedTransferContract";

		return defaultHapiSpec("sendHbarsFromDifferentAddressessToAddress")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver").balance(10_000L),
						uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_CONTRACT),
						contractCustomCreate(NESTED_CONTRACT, "1").balance(10_000L).payingWith(ACCOUNT),
						contractCustomCreate(NESTED_CONTRACT, "2").balance(10_000L).payingWith(ACCOUNT),
						getAccountInfo("receiver").savingSnapshot("receiverInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiverAddr = spec.registry().getAccountInfo("receiverInfo").getContractAccountID();

							allRunFor(spec,
									contractCreate(NESTED_TRANSFERRING_CONTRACT,
											getNestedContractAddress(NESTED_CONTRACT + "1", spec),
											getNestedContractAddress(NESTED_CONTRACT + "2", spec)).balance(
											10_000L).payingWith(ACCOUNT),

									contractCall(NESTED_TRANSFERRING_CONTRACT,
											"transferFromDifferentAddressesToAddress",
											receiverAddr, 40L)
											.payingWith(ACCOUNT).logged());
						})
				)
				.then(
						getAccountBalance("receiver").hasTinyBars(10_000L + 80L),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "1")
								.has(contractWith().balance(10_000L - 20L))),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "2")
								.has(contractWith().balance(10_000L - 20L)))
				);
	}

	private HapiApiSpec sendHbarsToOuterContractFromDifferentAddresses() {
		final var ACCOUNT = "account";
		final var NESTED_TRANSFERRING_CONTRACT = "NestedTransferringContract";
		final var NESTED_CONTRACT = "NestedTransferContract";
		return defaultHapiSpec("sendHbarsToOuterContractFromDifferentAddresses")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_CONTRACT),
						contractCustomCreate(NESTED_CONTRACT, "1").balance(10_000L).payingWith(ACCOUNT),
						contractCustomCreate(NESTED_CONTRACT, "2").balance(10_000L).payingWith(ACCOUNT)
				)
				.when(
						withOpContext((spec, log) -> {
							allRunFor(spec,
									contractCreate(NESTED_TRANSFERRING_CONTRACT,
											getNestedContractAddress(NESTED_CONTRACT + "1", spec),
											getNestedContractAddress(NESTED_CONTRACT + "2", spec)).balance(
											10_000L).payingWith(ACCOUNT),

									contractCall(
											NESTED_TRANSFERRING_CONTRACT,
											"transferToContractFromDifferentAddresses", 50L)
											.payingWith(ACCOUNT).logged());
						})
				)
				.then(
						sourcing(() -> getContractInfo(NESTED_TRANSFERRING_CONTRACT)
								.has(contractWith().balance(10_000L + 100L))),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "1")
								.has(contractWith().balance(10_000L - 50L))),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "2")
								.has(contractWith().balance(10_000L - 50L)))
				);
	}

	private HapiApiSpec sendHbarsToCallerFromDifferentAddresses() {
		final var NESTED_TRANSFERRING_CONTRACT = "NestedTransferringContract";
		final var NESTED_CONTRACT = "NestedTransferContract";
		final var transferTxn = "transferTxn";
		return defaultHapiSpec("sendHbarsToCallerFromDifferentAddresses")
				.given(
						withOpContext((spec, log) -> {
							if(!spec.isUsingEthCalls()) {
								spec.registry().saveAccountId(DEFAULT_CONTRACT_RECEIVER, spec.setup().strongControlAccount());
							}
							final var keyCreation = newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE);
							final var transfer1 = cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
									.via("autoAccount");

							final var nestedTransferringUpload = uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_CONTRACT);
							final var createFirstNestedContract = contractCustomCreate(NESTED_CONTRACT, "1").balance(10_000L);
							final var createSecondNestedContract = contractCustomCreate(NESTED_CONTRACT, "2").balance(10_000L);
							final var transfer2 = cryptoTransfer(TokenMovement.movingHbar(10_000_000L).between(GENESIS, DEFAULT_CONTRACT_RECEIVER));
							final var saveSnapshot = getAccountInfo(DEFAULT_CONTRACT_RECEIVER).savingSnapshot("accountInfo").payingWith(GENESIS);
							allRunFor(spec, keyCreation, transfer1, nestedTransferringUpload, createFirstNestedContract, createSecondNestedContract, transfer2, saveSnapshot);
						})
				)
				.when(
						withOpContext((spec, log) -> {
							allRunFor(spec,
									contractCreate(NESTED_TRANSFERRING_CONTRACT,
											getNestedContractAddress(NESTED_CONTRACT + "1", spec),
											getNestedContractAddress(NESTED_CONTRACT + "2", spec)).balance(
											10_000L).payingWith(GENESIS),
									contractCall(
											NESTED_TRANSFERRING_CONTRACT,
											"transferToCallerFromDifferentAddresses", 100L)
											.payingWith(DEFAULT_CONTRACT_RECEIVER)
											.signingWith(SECP_256K1_RECEIVER_SOURCE_KEY)
											.via(transferTxn).logged(),

									getTxnRecord(transferTxn).saveTxnRecordToRegistry("txn").payingWith(GENESIS),
									getAccountInfo(DEFAULT_CONTRACT_RECEIVER).savingSnapshot("accountInfoAfterCall").payingWith(GENESIS));
						})
				)
				.then(
						assertionsHold((spec, opLog) -> {
							final var fee = spec.registry().getTransactionRecord("txn").getTransactionFee();
							final var accountBalanceBeforeCall =
									spec.registry().getAccountInfo("accountInfo").getBalance();
							final var accountBalanceAfterCall =
									spec.registry().getAccountInfo("accountInfoAfterCall").getBalance();

							Assertions.assertEquals(accountBalanceAfterCall,
									accountBalanceBeforeCall - fee + 200L);

						}),
						sourcing(() -> getContractInfo(NESTED_TRANSFERRING_CONTRACT)
								.has(contractWith().balance(10_000L - 200L))),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "1")
								.has(contractWith().balance(10_000L))),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "2")
								.has(contractWith().balance(10_000L)))
				);
	}

	private HapiApiSpec sendHbarsFromAndToDifferentAddressess() {
		final var ACCOUNT = "account";
		final var NESTED_TRANSFERRING_CONTRACT = "NestedTransferringContract";
		final var NESTED_CONTRACT = "NestedTransferContract";
		return defaultHapiSpec("sendHbarsFromAndToDifferentAddressess")
				.given(
						cryptoCreate(ACCOUNT).balance(200 * ONE_HUNDRED_HBARS),
						cryptoCreate("receiver1").balance(10_000L),
						cryptoCreate("receiver2").balance(10_000L),
						cryptoCreate("receiver3").balance(10_000L),
						uploadInitCode(NESTED_TRANSFERRING_CONTRACT, NESTED_CONTRACT),
						contractCustomCreate(NESTED_CONTRACT, "1").balance(10_000L).payingWith(ACCOUNT),
						contractCustomCreate(NESTED_CONTRACT, "2").balance(10_000L).payingWith(ACCOUNT),

						getAccountInfo("receiver1").savingSnapshot("receiver1Info"),
						getAccountInfo("receiver2").savingSnapshot("receiver2Info"),
						getAccountInfo("receiver3").savingSnapshot("receiver3Info")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiver1Addr = spec.registry().getAccountInfo("receiver1Info").getContractAccountID();
							var receiver2Addr = spec.registry().getAccountInfo("receiver2Info").getContractAccountID();
							var receiver3Addr = spec.registry().getAccountInfo("receiver3Info").getContractAccountID();

							allRunFor(spec,
									contractCreate(NESTED_TRANSFERRING_CONTRACT,
											getNestedContractAddress(NESTED_CONTRACT + "1", spec),
											getNestedContractAddress(NESTED_CONTRACT + "2", spec)).balance(
											10_000L).payingWith(ACCOUNT),

									contractCall(
											NESTED_TRANSFERRING_CONTRACT,
											"transferFromAndToDifferentAddresses",
											receiver1Addr, receiver2Addr, receiver3Addr, 40)
											.payingWith(ACCOUNT).gas(1_000_000L).logged());
						})
				)
				.then(
						getAccountBalance("receiver1").hasTinyBars(10_000 + 80),
						getAccountBalance("receiver2").hasTinyBars(10_000 + 80),
						getAccountBalance("receiver3").hasTinyBars(10_000 + 80),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "1")
								.has(contractWith().balance(10_000 - 60))),
						sourcing(() -> getContractInfo(NESTED_CONTRACT + "2")
								.has(contractWith().balance(10_000 - 60)))
				);
	}

	private HapiApiSpec transferNegativeAmountOfHbars() {
		final var ACCOUNT = "account";
		return defaultHapiSpec("transferNegativeAmountOfHbarsFails")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver").balance(10_000L),
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L).payingWith(ACCOUNT),

						getAccountInfo("receiver").savingSnapshot("receiverInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiverAddr = spec.registry().getAccountInfo("receiverInfo").getContractAccountID();
							var transferCall = contractCall(
									TRANSFERRING_CONTRACT,
									"transferToAddressNegativeAmount",
									receiverAddr, 10)
									.payingWith(ACCOUNT).hasKnownStatus(CONTRACT_REVERT_EXECUTED);
							var transferCallZeroHbars = contractCall(
									TRANSFERRING_CONTRACT,
									"transferToAddressNegativeAmount",
									receiverAddr, 0)
									.payingWith(ACCOUNT).hasKnownStatus(SUCCESS);

							allRunFor(spec, transferCall, transferCallZeroHbars);
						})
				)
				.then(
						getAccountBalance("receiver").hasTinyBars(10_000L),
						sourcing(() -> getContractInfo(TRANSFERRING_CONTRACT)
								.has(contractWith().balance(10_000L)))
				);
	}

	private HapiApiSpec transferToCaller() {
		final var transferTxn = "transferTxn";
		return defaultHapiSpec("transferToCaller")
				.given(
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot("accountInfo").payingWith(GENESIS)
				)
				.when(
						withOpContext((spec, log) -> {
							var transferCall = contractCall(
									TRANSFERRING_CONTRACT,
									"transferToCaller", 10)
									.payingWith(DEFAULT_CONTRACT_SENDER)
									.via(transferTxn).logged();

							var saveTxnRecord =
									getTxnRecord(transferTxn).saveTxnRecordToRegistry("txn").payingWith(GENESIS);
							var saveAccountInfoAfterCall = getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(
									"accountInfoAfterCall").payingWith(GENESIS);
							var saveContractInfo = getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(
									"contract_from");

							allRunFor(spec, transferCall, saveTxnRecord, saveAccountInfoAfterCall, saveContractInfo);
						})
				)
				.then(
						assertionsHold((spec, opLog) -> {
							final var fee = spec.registry().getTransactionRecord("txn").getTransactionFee();
							final var accountBalanceBeforeCall =
									spec.registry().getAccountInfo("accountInfo").getBalance();
							final var accountBalanceAfterCall =
									spec.registry().getAccountInfo("accountInfoAfterCall").getBalance();

							Assertions.assertEquals(accountBalanceAfterCall,
									accountBalanceBeforeCall - fee + 10L);

						}),
						sourcing(() -> getContractInfo(TRANSFERRING_CONTRACT)
								.has(contractWith().balance(10_000L - 10L)))
				);
	}

	private HapiApiSpec transferZeroHbarsToCaller() {
		final var transferTxn = "transferTxn";
		return defaultHapiSpec("transferZeroHbarsToCaller")
				.given(
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot("accountInfo").payingWith(GENESIS)
				)
				.when(
						withOpContext((spec, log) -> {
							var transferCall = contractCall(
									TRANSFERRING_CONTRACT,
									"transferToCaller", 0)
									.payingWith(DEFAULT_CONTRACT_SENDER).via(transferTxn).logged();

							var saveTxnRecord =
									getTxnRecord(transferTxn).saveTxnRecordToRegistry("txn_registry").payingWith(
											GENESIS);
							var saveAccountInfoAfterCall = getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(
									"accountInfoAfterCall").payingWith(GENESIS);
							var saveContractInfo = getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(
									"contract_from");

							allRunFor(spec, transferCall, saveTxnRecord, saveAccountInfoAfterCall, saveContractInfo);
						})
				)
				.then(
						assertionsHold((spec, opLog) -> {
							final var fee = spec.registry().getTransactionRecord("txn_registry").getTransactionFee();
							final var accountBalanceBeforeCall =
									spec.registry().getAccountInfo("accountInfo").getBalance();
							final var accountBalanceAfterCall =
									spec.registry().getAccountInfo("accountInfoAfterCall").getBalance();
							final var contractBalanceAfterCall =
									spec.registry().getContractInfo("contract_from").getBalance();

							Assertions.assertEquals(accountBalanceAfterCall,
									accountBalanceBeforeCall - fee);
							Assertions.assertEquals(contractBalanceAfterCall,
									10_000L);
						})
				);
	}

	private HapiApiSpec transferZeroHbars() {
		final var ACCOUNT = "account";
		final var transferTxn = "transferTxn";
		return defaultHapiSpec("transferZeroHbars")
				.given(
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate("receiver").balance(10_000L),
						uploadInitCode(TRANSFERRING_CONTRACT),
						contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
						getAccountInfo("receiver").savingSnapshot("receiverInfo")
				)
				.when(
						withOpContext((spec, log) -> {
							var receiverAddr = spec.registry().getAccountInfo("receiverInfo").getContractAccountID();

							var transferCall = contractCall(
									TRANSFERRING_CONTRACT,
									"transferToAddress", receiverAddr, 0)
									.payingWith(ACCOUNT).via(transferTxn).logged();

							var saveContractInfo = getContractInfo(TRANSFERRING_CONTRACT).saveToRegistry(
									"contract_from");

							allRunFor(spec, transferCall, saveContractInfo);
						})
				)
				.then(
						assertionsHold((spec, opLog) -> {
							final var contractBalanceAfterCall =
									spec.registry().getContractInfo("contract_from").getBalance();

							Assertions.assertEquals(contractBalanceAfterCall,
									10_000L);
						}),
						getAccountBalance("receiver").hasTinyBars(10_000L)
				);
	}

	private String getNestedContractAddress(final String contract, final HapiApiSpec spec) {
		return HapiPropertySource.asHexedSolidityAddress(spec.registry().getContractId(contract));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private ByteString bookInterpolated(final byte[] jurisdictionInitcode, final String addressBookMirror) {
		return ByteString.copyFrom(
				new String(jurisdictionInitcode)
						.replaceAll("_+AddressBook.sol:AddressBook_+", addressBookMirror)
						.getBytes());
	}

	private static final String explicitJurisdictionConsParams =
			"45fd06740000000000000000000000001234567890123456789012345678901234567890";
	private static final String explicitMinterConsParamsTpl =
			"1c26cc85%s0000000000000000000000001234567890123456789012345678901234567890";
	private static final String explicitMinterConfigParamsTpl =
			"da71addf000000000000000000000000%s";
	private static final String explicitJurisdictionsAddParams =
			"218c66ea0000000000000000000000000000000000000000000000000000000000000080000000000000000000000000" +
					"0000000000000000000000000000000000000339000000000000000000000000123456789012345678901234" +
					"5678901234567890000000000000000000000000123456789012345678901234567890123456789000000000" +
					"000000000000000000000000000000000000000000000000000000026e790000000000000000000000000000" +
					"00000000000000000000000000000000";
}