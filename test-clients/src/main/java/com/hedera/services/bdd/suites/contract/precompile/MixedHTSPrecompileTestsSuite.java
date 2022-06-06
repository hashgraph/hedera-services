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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

public class MixedHTSPrecompileTestsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(MixedHTSPrecompileTestsSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final long TOTAL_SUPPLY = 1_000;

	public static void main(String... args) {
		new MixedHTSPrecompileTestsSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				HSCS_PREC_021_try_catch_construct_only_rolls_back_the_failed_precompile()
		);
	}

	private HapiApiSpec HSCS_PREC_021_try_catch_construct_only_rolls_back_the_failed_precompile() {
		final var theAccount = "anybody";
		final var token = "Token";
		final var outerContract = "AssociateTryCatch";
		final var nestedContract = "CalledContract";

		return defaultHapiSpec("HSCS_PREC_021_try_catch_construct_only_rolls_back_the_failed_precompile")
				.given(
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, asAddress(spec.registry().getTokenID(token))
												)
														.via("associateTxn"),
												cryptoTransfer(moving(200, token).between(TOKEN_TREASURY, theAccount))
														.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										)
						)
				).when(
						contractCall(outerContract, "associateToken"
						)
								.payingWith(theAccount)
								.gas(GAS_TO_OFFER)
								.via("associateMethodCall")
				).then(
						childRecordsCheck("associateMethodCall", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))),
								recordWith()
										.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)))),
						getAccountInfo(theAccount).hasToken(relationshipWith(token)),
						cryptoTransfer(moving(200, token).between(TOKEN_TREASURY, theAccount))
								.hasKnownStatus(SUCCESS)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}