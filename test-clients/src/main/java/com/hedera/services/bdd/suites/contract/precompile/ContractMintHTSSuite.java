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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_BRRR_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_MINT_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_MINT_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_FUNGIBLE_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MINT_NON_FUNGIBLE_CALL_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractMintHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractMintHTSSuite.class);
	private static final String TOKEN_TREASURY = "treasury";

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
		return List.of();
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				helloWorldFungibleMint(),
				helloWorldNftMint(),
				happyPathFungibleTokenMint(),
				happyPathNonFungibleTokenMint()
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
		final var hwMintInitcode = "hwMintInitcode";

		final var nonfungibleToken = "nonfungibleToken";
		final var multiKey = "purpose";
		final var contractKey = "meaning";
		final var hwMint = "hwMint";
		final var firstMintTxn = "firstMintTxn";
		final var secondMintTxn = "secondMintTxn";

		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("HelloWorldNftMint")
				.given(
						newKeyNamed(multiKey),
						fileCreate(hwMintInitcode)
								.path(ContractResources.HW_MINT_PATH),
						tokenCreate(nonfungibleToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> nonFungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(hwMint, HW_MINT_CONS_ABI, nonFungibleNum.get())
								.bytecode(hwMintInitcode)
								.gas(300_000L))
				).then(
						contractCall(hwMint, HW_MINT_CALL_ABI)
								.via(firstMintTxn)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(nonfungibleToken).hasTotalSupply(1),
						/* And now make the token contract-controlled so no explicit supply sig is required */
						newKeyNamed(contractKey)
								.shape(DELEGATE_CONTRACT.signedWith(hwMint)),
						tokenUpdate(nonfungibleToken)
								.supplyKey(contractKey),
						getTokenInfo(nonfungibleToken).logged(),
						contractCall(hwMint, HW_MINT_CALL_ABI)
								.via(secondMintTxn),
						getTxnRecord(secondMintTxn).andAllChildRecords().logged(),
						getTokenInfo(nonfungibleToken).hasTotalSupply(2),
						getTokenNftInfo(nonfungibleToken, 2L).logged()
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
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(mintContractByteCode)
								.path(ContractResources.MINT_CONTRACT).payingWith(theAccount),
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
						contractCall(theContract, MINT_FUNGIBLE_CALL_ABI, amount)
								.via(firstMintTxn).payingWith(theAccount)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

	private HapiApiSpec happyPathNonFungibleTokenMint() {
		final var theAccount = "anybody";
		final var mintContractByteCode = "mintContractByteCode";
		final var numberOfTokens = 2;
		final var nonFungibleToken = "nonFungibleToken";
		final var multiKey = "purpose";
		final var theContract = "mintContract";
		final var firstMintTxn = "firstMintTxn";

		byte[] metadataBytes = getMetadata();
		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("NonFungibleMint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						fileCreate(mintContractByteCode)
								.path(ContractResources.MINT_CONTRACT).payingWith(theAccount),
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
						contractCall(theContract, MINT_NON_FUNGIBLE_CALL_ABI, metadataBytes)
								.via(firstMintTxn).payingWith(theAccount)
								.alsoSigningWithFullPrefix(multiKey),
						getTxnRecord(firstMintTxn).andAllChildRecords().logged(),
						getTokenInfo(nonFungibleToken).hasTotalSupply(2),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(nonFungibleToken, 2)
				);
	}

	private byte[] getMetadata() {
		final List<String> metadataList = new ArrayList<>();
		metadataList.add("Test metadata 1");
		metadataList.add("Test metadata 2");
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(baos);

		for (final String element : metadataList) {
			try {
				out.writeUTF(element);
			} catch (IOException e) {
				log.warn("Invalid parsing of metadata list!");
			}
		}

		return baos.toByteArray();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
