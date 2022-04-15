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
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.FunctionParameters;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.utils.contracts.FunctionParameters.functionParameters;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractMintHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractMintHTSSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, KeyShape.SIMPLE,
			DELEGATE_CONTRACT);
	private static final String DELEGATE_KEY = "DelegateKey";
	private static final String CONTRACT_KEY = "ContractKey";
	private static final String MULTI_KEY = "purpose";

	private static final String MINT_CONTRACT = "MintContract";
	private static final String MINT_NFT_CONTRACT = "MintNFTContract";
	private static final String NESTED_MINT_CONTRACT = "NestedMint";
	private static final String HELLO_WORLD_MINT = "HelloWorldMint";
	private static final String ACCOUNT = "anybody";
	private static final String DELEGATE_CONTRACT_KEY_NAME = "contractKey";

	public static void main(String... args) {
		new ContractMintHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(
				rollbackOnFailedMintAfterFungibleTransfer(),
				rollbackOnFailedAssociateAfterNonFungibleMint(),
				gasCostNotMetSetsInsufficientGasStatusInChildRecord()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				helloWorldFungibleMint(),
				helloWorldNftMint(),
				happyPathFungibleTokenMint(),
				happyPathNonFungibleTokenMint(),
				transferNftAfterNestedMint()
		);
	}

	private HapiApiSpec helloWorldFungibleMint() {
		final var fungibleToken = "fungibleToken";
		final var firstMintTxn = "firstMintTxn";
		final var secondMintTxn = "secondMintTxn";
		final var amount = 1_234_567L;

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						newKeyNamed(MULTI_KEY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(HELLO_WORLD_MINT)
				).when(
						sourcing(() -> contractCreate(HELLO_WORLD_MINT, fungibleNum.get()))
				).then(
						contractCall(HELLO_WORLD_MINT, "brrr", amount
						)
								.via(firstMintTxn)
								.alsoSigningWithFullPrefix(MULTI_KEY),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						/* And now make the token contract-controlled so no explicit supply sig is required */
						newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(HELLO_WORLD_MINT)),
						tokenUpdate(fungibleToken).supplyKey(CONTRACT_KEY),
						getTokenInfo(fungibleToken).logged(),
						contractCall(HELLO_WORLD_MINT, "brrr", amount).via(secondMintTxn),
						getTxnRecord(secondMintTxn).andAllChildRecords().logged(),
						getTokenInfo(fungibleToken).hasTotalSupply(2 * amount),
						childRecordsCheck(secondMintTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.newTotalSupply(2469134L)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, DEFAULT_PAYER, amount)
										)
						)
				);
	}

	private HapiApiSpec helloWorldNftMint() {
		final var nonFungibleToken = "nonFungibleToken";
		final var firstMintTxn = "firstMintTxn";
		final var secondMintTxn = "secondMintTxn";

		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("HelloWorldNftMint")
				.given(
						newKeyNamed(MULTI_KEY),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> nonFungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(HELLO_WORLD_MINT),
						sourcing(() -> contractCreate(HELLO_WORLD_MINT, nonFungibleNum.get()))
				).when(
						contractCall(HELLO_WORLD_MINT, "mint")
								.via(firstMintTxn)
								.gas(GAS_TO_OFFER)
								.alsoSigningWithFullPrefix(MULTI_KEY),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(nonFungibleToken).hasTotalSupply(1),
						/* And now make the token contract-controlled so no explicit supply sig is required */
						newKeyNamed(CONTRACT_KEY).shape(DELEGATE_CONTRACT.signedWith(HELLO_WORLD_MINT)),
						tokenUpdate(nonFungibleToken).supplyKey(CONTRACT_KEY),
						getTokenInfo(nonFungibleToken).logged(),
						contractCall(HELLO_WORLD_MINT, "mint")
								.via(secondMintTxn)
								.gas(GAS_TO_OFFER),
						getTxnRecord(secondMintTxn).andAllChildRecords().logged()
				).then(
						getTokenInfo(nonFungibleToken).hasTotalSupply(2),
						getTokenNftInfo(nonFungibleToken, 2L).logged()
				);
	}

	private HapiApiSpec happyPathFungibleTokenMint() {
		final var fungibleToken = "fungibleToken";
		final var firstMintTxn = "firstMintTxn";
		final var amount = 10L;

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("FungibleMint")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(MINT_CONTRACT),
						sourcing(() -> contractCreate(MINT_CONTRACT, fungibleNum.get()))
				).when(
						contractCall(MINT_CONTRACT, "mintFungibleTokenWithEvent", amount
						)
								.via(firstMintTxn)
								.payingWith(ACCOUNT)
								.alsoSigningWithFullPrefix(MULTI_KEY),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTxnRecord(firstMintTxn).hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(
														parsedToByteString(amount),
														parsedToByteString(0)))))))
				).then(
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

	private HapiApiSpec happyPathNonFungibleTokenMint() {
		final var nonFungibleToken = "nonFungibleToken";
		final var firstMintTxn = "firstMintTxn";
		final var totalSupply = 2;

		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("NonFungibleMint")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> nonFungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(MINT_CONTRACT),
						sourcing(() -> contractCreate(MINT_CONTRACT, nonFungibleNum.get()))
				).when(
						contractCall(MINT_CONTRACT, "mintNonFungibleTokenWithEvent",
								Arrays.asList("Test metadata 1", "Test metadata 2")
						)
								.via(firstMintTxn).payingWith(ACCOUNT)
								.gas(GAS_TO_OFFER)
								.alsoSigningWithFullPrefix(MULTI_KEY),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTxnRecord(firstMintTxn).hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(
														parsedToByteString(totalSupply),
														parsedToByteString(1)))))))
				).then(
						getTokenInfo(nonFungibleToken).hasTotalSupply(totalSupply),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nonFungibleToken, totalSupply)
				);
	}

	private HapiApiSpec transferNftAfterNestedMint() {
		final var theAccount = "anybody";
		final var theRecipient = "recipient";
		final var nonFungibleToken = "nonFungibleToken";
		final var multiKey = "purpose";
		final var nestedTransferTxn = "nestedTransferTxn";

		final long expectedGasUsage = 1_063_830L;
		return defaultHapiSpec("TransferNftAfterNestedMint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient).maxAutomaticTokenAssociations(1),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						uploadInitCode(NESTED_MINT_CONTRACT, MINT_NFT_CONTRACT),
						contractCreate(MINT_NFT_CONTRACT)
								.gas(GAS_TO_OFFER)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(NESTED_MINT_CONTRACT,
														getNestedContractAddress(MINT_NFT_CONTRACT, spec),
														asAddress(spec.registry().getTokenID(nonFungibleToken)))
														.gas(GAS_TO_OFFER),
												newKeyNamed(DELEGATE_CONTRACT_KEY_NAME).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														NESTED_MINT_CONTRACT))),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_CONTRACT_KEY_NAME),
												tokenUpdate(nonFungibleToken).supplyKey(DELEGATE_CONTRACT_KEY_NAME),
												contractCall(NESTED_MINT_CONTRACT,
														"sendNFTAfterMint",
														asAddress(spec.registry().getAccountID(TOKEN_TREASURY)),
														asAddress(spec.registry().getAccountID(theRecipient)),
														Arrays.asList("Test metadata 1"), 1L)
														.payingWith(GENESIS)
														.alsoSigningWithFullPrefix(multiKey)
														.via(nestedTransferTxn)
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												getTxnRecord(nestedTransferTxn).andAllChildRecords().logged()
										)
						)
				).then(
						assertTxnRecordHasNoTraceabilityEnrichedContractFnResult(nestedTransferTxn),
						withOpContext((spec, opLog) -> allRunFor(spec,
								getTxnRecord(nestedTransferTxn).andAllChildRecords().logged(),
								childRecordsCheck(nestedTransferTxn, SUCCESS,
										recordWith()
												.status(SUCCESS)
												.contractCallResult(
														resultWith()
																.gasUsed(expectedGasUsage)
																.contractCallResult(htsPrecompileResult()
																		.forFunction(HTSPrecompileResult.FunctionType.MINT)
																		.withStatus(SUCCESS)
																		.withTotalSupply(1L)
																		.withSerialNumbers(1L)
																)
																.gas(3_838_738L)
																.amount(0L)
																.functionParameters(functionParameters()
																		.forFunction(FunctionParameters.PrecompileFunction.MINT)
																		.withTokenAddress(asAddress(spec.registry()
																				.getTokenID(nonFungibleToken)))
																		.withAmount(0L)
																		.withMetadata(Arrays.asList("Test metadata 1"))
																		.build()
																)
												),
										recordWith()
												.status(SUCCESS)
												.tokenTransfers(NonFungibleTransfers
														.changingNFTBalances()
														.including(nonFungibleToken, TOKEN_TREASURY, theRecipient, 1)
												)
								)))
				);
	}

	private HapiApiSpec rollbackOnFailedMintAfterFungibleTransfer() {
		final var theAccount = "anybody";
		final var theRecipient = "recipient";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var contract = "MintContract";
		final var failedMintTxn = "failedMintTxn";

		return defaultHapiSpec("RollbackOnFailedMintAfterFungibleTransfer")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						tokenAssociate(theAccount, List.of(fungibleToken)),
						tokenAssociate(theRecipient, List.of(fungibleToken)),
						cryptoTransfer(moving(200, fungibleToken).between(TOKEN_TREASURY, theAccount)),
						uploadInitCode(contract)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(contract, asAddress(spec.registry().getTokenID(fungibleToken))),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														contract))),
												cryptoUpdate(theAccount).key(DELEGATE_KEY),
												contractCall(contract, "revertMintAfterFailedMint",
														asAddress(spec.registry().getAccountID(theAccount)),
														asAddress(spec.registry().getAccountID(theRecipient)), 20
												)
														.payingWith(GENESIS).alsoSigningWithFullPrefix(multiKey)
														.via(failedMintTxn)
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
												getTxnRecord(failedMintTxn).andAllChildRecords().logged()
										)
						)
				).then(
						getAccountBalance(theAccount).hasTokenBalance(fungibleToken, 200),
						getAccountBalance(theRecipient).hasTokenBalance(fungibleToken, 0)
				);
	}

	private HapiApiSpec rollbackOnFailedAssociateAfterNonFungibleMint() {
		final var theAccount = "anybody";
		final var theRecipient = "recipient";
		final var nonFungibleToken = "nonFungibleToken";
		final var outerContract = "NestedMint";
		final var nestedContract = "MintNFTContract";
		final var nestedMintTxn = "nestedMintTxn";

		return defaultHapiSpec("RollbackOnFailedAssociateAfterNonFungibleMint")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(theAccount).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(nestedContract, outerContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec),
														asAddress(spec.registry().getTokenID(nonFungibleToken)))
														.bytecode(outerContract)
														.gas(GAS_TO_OFFER),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														outerContract))),
												cryptoUpdate(theAccount).key(DELEGATE_KEY),
												contractCall(outerContract, "revertMintAfterFailedAssociate",
														asAddress(spec.registry().getAccountID(theAccount)),
														Arrays.asList("Test metadata 1")
												)
														.payingWith(GENESIS).alsoSigningWithFullPrefix(MULTI_KEY)
														.via(nestedMintTxn)
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
												getTxnRecord(nestedMintTxn).andAllChildRecords().logged()
										)
						)
				).then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nonFungibleToken, 0)
				);
	}

	private HapiApiSpec fungibleTokenMintFailure() {
		final var theAccount = "anybody";
		final var mintContractByteCode = "mintContractByteCode";
		final var amount = "9223372036854775808";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "MintContract";
		final var firstMintTxn = "firstMintTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("FungibleMintFailure")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(mintContractByteCode).payingWith(theAccount),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						uploadInitCode(theContract),
						sourcing(() -> contractCreate(theContract, fungibleNum.get())
								.bytecode(mintContractByteCode).payingWith(theAccount)
								.gas(GAS_TO_OFFER))
				).then(
						contractCall(theContract, "mintFungibleToken", amount)
								.via(firstMintTxn).payingWith(theAccount)
								.alsoSigningWithFullPrefix(multiKey)
								.gas(2_000_000L)
								.hasKnownStatus(SUCCESS),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged()
				);
	}

	@NotNull
	private CustomSpecAssert assertTxnRecordHasNoTraceabilityEnrichedContractFnResult(final String nestedTransferTxn) {
		return assertionsHold((spec, log) -> {
			var subOp = getTxnRecord(nestedTransferTxn);
			allRunFor(spec, subOp);

			var record = subOp.getResponseRecord();

			final var contractCallResult =
					record.getContractCallResult();
			assertEquals(0L, contractCallResult.getGas());
			assertEquals(0L, contractCallResult.getAmount());
			assertEquals(ByteString.EMPTY, contractCallResult.getFunctionParameters());
		});
	}

	private HapiApiSpec gasCostNotMetSetsInsufficientGasStatusInChildRecord() {

		final var theAccount = "anybody";
		final var mintContractByteCode = "mintContractByteCode";
		final var amount = 10L;
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "MintContract";
		final var firstMintTxn = "firstMintTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("gasCostNotMetSetsInsufficientGasStatusInChildRecord")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						uploadInitCode(theContract),
						sourcing(() -> contractCreate(theContract, fungibleNum.get())
								.payingWith(theAccount)
								.gas(GAS_TO_OFFER))
				).then(
						contractCall(theContract, "mintFungibleToken", amount)
								.via(firstMintTxn)
								.payingWith(theAccount)
								.alsoSigningWithFullPrefix(multiKey)
								.gas(48_000),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						childRecordsCheck(firstMintTxn, SUCCESS, recordWith().status(INSUFFICIENT_GAS)),
						getTokenInfo(fungibleToken).hasTotalSupply(0),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, 0)
				);
	}

	@NotNull
	private String getNestedContractAddress(final String contract, final HapiApiSpec spec) {
		return AssociatePrecompileSuite.getNestedContractAddress(contract, spec);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}