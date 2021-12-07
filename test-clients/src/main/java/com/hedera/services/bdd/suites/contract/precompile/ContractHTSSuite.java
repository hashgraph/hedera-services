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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DISSOCIATE_TOKEN;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.HW_MINT_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ZENOS_BANK_DEPOSIT_TOKENS;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ZENOS_BANK_WITHDRAW_TOKENS;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.*;

public class ContractHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new ContractHTSSuite().runSuiteAsync();
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
				depositAndWithdraw(),
				associateToken(),
				dissociateToken()
		);
	}

	private HapiApiSpec depositAndWithdraw() {
		final var theAccount = "anybody";
		final var theReceiver = "somebody";
		final var theKey = "multipurpose";
		final var theContract = "zeno's bank";
		return defaultHapiSpec("depositAndWithdraw")
				.given(
						newKeyNamed(theKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(theReceiver),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("bytecode").payingWith(theAccount),
						updateLargeFile(theAccount, "bytecode", extractByteCode(ContractResources.ZENOS_BANK_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.ZENOS_BANK_CONSTRUCTOR,
														asAddress(spec.registry().getTokenID(A_TOKEN)))
														.payingWith(theAccount)
														.bytecode("bytecode")
														.via("creationTx")
														.gas(28_000))),
						getTxnRecord("creationTx").logged(),
						tokenAssociate(theAccount, List.of(A_TOKEN)),
						tokenAssociate(theContract, List.of(A_TOKEN)),
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
				).when(
						contractCall(theContract, ZENOS_BANK_DEPOSIT_TOKENS, 50)
								.payingWith(theAccount)
								.gas(48_000)
								.via("zeno"),
						getTxnRecord("zeno").logged(),
						contractCall(theContract, ZENOS_BANK_WITHDRAW_TOKENS)
								.payingWith(theReceiver)
								.alsoSigningWithFullPrefix(theContract)
								.gas(70_000)
								.via("receiver"),
						getTxnRecord("receiver").logged()
				).then(
				);
	}

	private HapiApiSpec validatesAssociatePrecompileWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";

		final AtomicLong freezeKeyOnNum = new AtomicLong();
		final AtomicLong freezeKeyOffNum = new AtomicLong();
		final AtomicLong knowableTokenNum = new AtomicLong();
		final AtomicLong vanillaTokenNum = new AtomicLong();

		return defaultHapiSpec("associatePrecompileWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount).balance(0L),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(idLit -> freezeKeyOnNum.set(asDotDelimitedLongArray(idLit)[2])),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(idLit -> freezeKeyOffNum.set(asDotDelimitedLongArray(idLit)[2])),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey")
								.exposingCreatedIdTo(idLit -> knowableTokenNum.set(asDotDelimitedLongArray(idLit)[2])),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(idLit -> vanillaTokenNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(theContract)
								.bytecode("associateDissociateContractByteCode")
								.gas(100_000))
				).then();


	}


	private HapiApiSpec associateToken() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		return defaultHapiSpec("associateHappyPath")
				.given(
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("associateDissociateContractByteCode").payingWith(theAccount),
						updateLargeFile(theAccount, "associateDissociateContractByteCode",
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.payingWith(theAccount)
														.bytecode("associateDissociateContractByteCode")
														.via("associateTxn")
														.gas(100000),
												cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
														.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										)
						)
				).when(
						contractCall(theContract, ContractResources.ASSOCIATE_TOKEN).payingWith(theAccount).via("associateMethodCall")
				).then(
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
								.hasKnownStatus(ResponseCodeEnum.SUCCESS)
				);
	}

	private HapiApiSpec dissociateToken() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		return defaultHapiSpec("dissociateHappyPath")
				.given(
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("associateDissociateContractByteCode").payingWith(theAccount),
						updateLargeFile(theAccount, "associateDissociateContractByteCode",
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.payingWith(theAccount)
														.bytecode("associateDissociateContractByteCode")
														.via("associateTxn")
														.gas(100000),
												contractCall(theContract, ContractResources.ASSOCIATE_TOKEN).payingWith(theAccount).via("associateMethodCall"),
												cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
														.hasKnownStatus(SUCCESS),
												cryptoTransfer(moving(200, A_TOKEN).between(theAccount, TOKEN_TREASURY))
														.hasKnownStatus(SUCCESS)
										)
						)
				).when(
						contractCall(theContract, DISSOCIATE_TOKEN).payingWith(theAccount).via("dissociateMethodCall")
				).then(
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
								.hasKnownStatus(ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
