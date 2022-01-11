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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_BRRR_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_MINT_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_MINT_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_FUNGIBLE_WITH_EVENT_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_NON_FUNGIBLE_WITH_EVENT_CALL_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractMintHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractMintHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, KeyShape.SIMPLE,
			DELEGATE_CONTRACT);
	private static final String DELEGATE_CONTRACT_KEY_NAME = "contractKey";

	public static void main(String... args) {
		new ContractMintHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(
				rollbackOnFailedMintAfterFungibleTransfer(),
				rollbackOnFailedAssociateAfterNonFungibleMint()
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
		final var hwMintInitcode = "hwMintInitcode";

		final var amount = 1_234_567L;
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var contractKey = "meaning";
		final var hwMint = "hwMint";
		final var firstMintTxn = "firstMintTxn";
		final var secondMintTxn = "secondMintTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						newKeyNamed(multiKey),
						fileCreate(hwMintInitcode)
								.path(ContractResources.HW_MINT_PATH),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(hwMint, HW_MINT_CONS_ABI, fungibleNum.get())
								.bytecode(hwMintInitcode)
								.gas(300_000L))
				).then(
						contractCall(hwMint, HW_BRRR_CALL_ABI, amount)
								.via(firstMintTxn)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						/* And now make the token contract-controlled so no explicit supply sig is required */
						newKeyNamed(contractKey)
								.shape(DELEGATE_CONTRACT.signedWith(hwMint)),
						tokenUpdate(fungibleToken)
								.supplyKey(contractKey),
						getTokenInfo(fungibleToken).logged(),
						contractCall(hwMint, HW_BRRR_CALL_ABI, amount)
								.via(secondMintTxn),
						getTxnRecord(secondMintTxn).andAllChildRecords().logged(),
						getTokenInfo(fungibleToken).hasTotalSupply(2 * amount),
						childRecordsCheck(secondMintTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.newTotalSupply(2469134L)
										.tokenTransfers(
												changingFungibleBalances()
														.including(fungibleToken, GENESIS, amount)
										)
						)
				);
	}

	private HapiApiSpec helloWorldNftMint() {
		final var hwMintInitCode = "hwMintInitCode";

		final var nonFungibleToken = "nonFungibleToken";
		final var multiKey = "purpose";
		final var contractKey = "meaning";
		final var hwMint = "hwMint";
		final var firstMintTxn = "firstMintTxn";
		final var secondMintTxn = "secondMintTxn";

		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("HelloWorldNftMint")
				.given(
						newKeyNamed(multiKey),
						fileCreate(hwMintInitCode)
								.path(ContractResources.HW_MINT_PATH),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> nonFungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(hwMint, HW_MINT_CONS_ABI, nonFungibleNum.get())
								.bytecode(hwMintInitCode)
								.gas(300_000L))
				).then(
						contractCall(hwMint, HW_MINT_CALL_ABI)
								.via(firstMintTxn)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(nonFungibleToken).hasTotalSupply(1),
						/* And now make the token contract-controlled so no explicit supply sig is required */
						newKeyNamed(contractKey)
								.shape(DELEGATE_CONTRACT.signedWith(hwMint)),
						tokenUpdate(nonFungibleToken)
								.supplyKey(contractKey),
						getTokenInfo(nonFungibleToken).logged(),
						contractCall(hwMint, HW_MINT_CALL_ABI)
								.via(secondMintTxn),
						getTxnRecord(secondMintTxn).andAllChildRecords().logged(),
						getTokenInfo(nonFungibleToken).hasTotalSupply(2),
						getTokenNftInfo(nonFungibleToken, 2L).logged()
				);
	}

	private HapiApiSpec happyPathFungibleTokenMint() {
		final var theAccount = "anybody";
		final var mintContractByteCode = "mintContractByteCode";
		final var amount = 10L;
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "mintContract";
		final var firstMintTxn = "firstMintTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("FungibleMint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(mintContractByteCode).payingWith(theAccount),
						updateLargeFile(theAccount, mintContractByteCode,
								extractByteCode(ContractResources.MINT_CONTRACT)),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(theContract, MINT_CONS_ABI, fungibleNum.get())
								.bytecode(mintContractByteCode).payingWith(theAccount)
								.gas(300_000L))
				).then(
						contractCall(theContract, MINT_FUNGIBLE_WITH_EVENT_CALL_ABI, amount)
								.via(firstMintTxn).payingWith(theAccount)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTxnRecord(firstMintTxn).hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(
														parsedToByteString(amount),
														parsedToByteString(0))))))),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

	private HapiApiSpec happyPathNonFungibleTokenMint() {
		final var theAccount = "anybody";
		final var mintContractByteCode = "mintContractByteCode";
		final var nonFungibleToken = "nonFungibleToken";
		final var multiKey = "purpose";
		final var theContract = "mintContract";
		final var firstMintTxn = "firstMintTxn";
		final var totalSupply = 2;

		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("NonFungibleMint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(mintContractByteCode).payingWith(theAccount),
						updateLargeFile(theAccount, mintContractByteCode,
								extractByteCode(ContractResources.MINT_CONTRACT)),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> nonFungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(theContract, MINT_CONS_ABI, nonFungibleNum.get())
								.bytecode(mintContractByteCode).payingWith(theAccount)
								.gas(300_000L))
				).then(
						contractCall(theContract, MINT_NON_FUNGIBLE_WITH_EVENT_CALL_ABI,
								Arrays.asList("Test metadata 1", "Test metadata 2"))
								.via(firstMintTxn).payingWith(theAccount)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTxnRecord(firstMintTxn).hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(
														parsedToByteString(totalSupply),
														parsedToByteString(1))))))),
						getTokenInfo(nonFungibleToken).hasTotalSupply(totalSupply),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nonFungibleToken, totalSupply)
				);
	}

	private HapiApiSpec transferNftAfterNestedMint() {
		final var theAccount = "anybody";
		final var theRecipient = "recipient";
		final var nonFungibleToken = "nonFungibleToken";
		final var multiKey = "purpose";
		final var innerContract = "mintContract";
		final var outerContract = "transferContract";
		final var nestedTransferTxn = "nestedTransferTxn";

		return defaultHapiSpec("TransferNftAfterNestedMint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(20 * ONE_MILLION_HBARS),
						cryptoCreate(theRecipient).maxAutomaticTokenAssociations(1),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(innerContract),
						updateLargeFile(theAccount, innerContract, extractByteCode(ContractResources.MINT_NFT_CONTRACT)),
						fileCreate(outerContract),
						updateLargeFile(theAccount, outerContract, extractByteCode(ContractResources.NESTED_MINT_CONTRACT)),
						contractCreate(innerContract)
								.bytecode(innerContract)
								.gas(100_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract,
														ContractResources.NESTED_MINT_CONS_ABI,
														getNestedContractAddress(innerContract, spec),
														asAddress(spec.registry().getTokenID(nonFungibleToken)))
														.bytecode(outerContract)
														.gas(300_000),
												newKeyNamed(DELEGATE_CONTRACT_KEY_NAME).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														outerContract))),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_CONTRACT_KEY_NAME),
												tokenUpdate(nonFungibleToken).supplyKey(DELEGATE_CONTRACT_KEY_NAME),
												contractCall(outerContract,
														ContractResources.NESTED_TRANSFER_NFT_AFTER_MINT_CALL_ABI,
														asAddress(spec.registry().getAccountID(TOKEN_TREASURY)),
														asAddress(spec.registry().getAccountID(theRecipient)),
														Arrays.asList("Test metadata 1"), 1L)
														.payingWith(GENESIS)
														.alsoSigningWithFullPrefix(multiKey)
														.via(nestedTransferTxn)
														.hasKnownStatus(SUCCESS),
												getTxnRecord(nestedTransferTxn).andAllChildRecords().logged()
										)
						)
				).then(
						getTxnRecord(nestedTransferTxn).andAllChildRecords().logged(),
						childRecordsCheck(nestedTransferTxn, SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS).tokenTransfers(NonFungibleTransfers.changingNFTBalances().
												including(nonFungibleToken, TOKEN_TREASURY, theRecipient, 1)))
				);
	}

	private HapiApiSpec rollbackOnFailedMintAfterFungibleTransfer() {
		final var theAccount = "anybody";
		final var theRecipient = "recipient";
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var theContract = "theContract";
		final var failedMintTxn = "failedMintTxn";

		return defaultHapiSpec("RollbackOnFailedMintAfterFungibleTransfer")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(theRecipient),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.MINT_CONTRACT)),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						tokenAssociate(theAccount, List.of(fungibleToken)),
						tokenAssociate(theRecipient, List.of(fungibleToken)),
						cryptoTransfer(moving(200, fungibleToken).between(TOKEN_TREASURY, theAccount))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, MINT_CONS_ABI,
														asAddress(spec.registry().getTokenID(fungibleToken)))
														.bytecode(theContract)
														.gas(100_000L),
												newKeyNamed(DELEGATE_CONTRACT_KEY_NAME).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														theContract))),
												cryptoUpdate(theAccount).key(DELEGATE_CONTRACT_KEY_NAME),
												contractCall(theContract,
														ContractResources.REVERT_AFTER_FAILED_MINT,
														asAddress(spec.registry().getAccountID(theAccount)),
														asAddress(spec.registry().getAccountID(theRecipient)), 20)
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
		final var multiKey = "purpose";
		final var innerContract = "mintContract";
		final var outerContract = "transferContract";
		final var nestedMintTxn = "nestedMintTxn";

		return defaultHapiSpec("RollbackOnFailedAssociateAfterNonFungibleMint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(20 * ONE_MILLION_HBARS),
						cryptoCreate(theRecipient),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(innerContract),
						updateLargeFile(theAccount, innerContract, extractByteCode(ContractResources.MINT_NFT_CONTRACT)),
						fileCreate(outerContract),
						updateLargeFile(theAccount, outerContract, extractByteCode(ContractResources.NESTED_MINT_CONTRACT)),
						tokenCreate(nonFungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						contractCreate(innerContract)
								.bytecode(innerContract)
								.gas(100_000L)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract,
														ContractResources.NESTED_MINT_CONS_ABI,
														getNestedContractAddress(innerContract, spec),
														asAddress(spec.registry().getTokenID(nonFungibleToken)))
														.bytecode(outerContract)
														.gas(100_000L),
												newKeyNamed(DELEGATE_CONTRACT_KEY_NAME).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														outerContract))),
												cryptoUpdate(theAccount).key(DELEGATE_CONTRACT_KEY_NAME),
												contractCall(outerContract,
														ContractResources.REVERT_MINT_AFTER_FAILED_ASSOCIATE,
														asAddress(spec.registry().getAccountID(theAccount)),
														Arrays.asList("Test metadata 1"))
														.payingWith(GENESIS).alsoSigningWithFullPrefix(multiKey)
														.via(nestedMintTxn)
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
												getTxnRecord(nestedMintTxn).andAllChildRecords().logged()
										)
						)
				).then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nonFungibleToken, 0)
				);
	}

	@NotNull
	private String getNestedContractAddress(final String contract, final HapiApiSpec spec) {
		return CommonUtils.calculateSolidityAddress(
				(int) spec.registry().getContractId(contract).getShardNum(),
				spec.registry().getContractId(contract).getRealmNum(),
				spec.registry().getContractId(contract).getContractNum());
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}