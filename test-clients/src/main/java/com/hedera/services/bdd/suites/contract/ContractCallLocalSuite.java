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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STORAGE_ACCESS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractCallLocalSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallLocalSuite.class);

	public static void main(String... args) {
		new ContractCallLocalSuite().runSuiteAsync();
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

	private List<HapiApiSpec> negativeSpecs() {
		return Arrays.asList(
				deletedContract(),
				invalidContractID(),
				impureCallFails(),
				insufficientFeeFails(),
				lowBalanceFails(),
				rejectsInvalidStorageAccessKey()
		);
	}

	private List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList(
				vanillaSuccess(),
				contractCallLocalAccountAndStorageKeysWarmUpReducesGasCost()
		);
	}

	private HapiApiSpec vanillaSuccess() {
		return defaultHapiSpec("VanillaSuccess")
				.given(
						fileCreate("parentDelegateBytecode").path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode").adminKey(THRESHOLD)
				).when(
						contractCall("parentDelegate", ContractResources.CREATE_CHILD_ABI)
				).then(
						sleepFor(3_000L),
						contractCallLocal("parentDelegate", ContractResources.GET_CHILD_RESULT_ABI)
								.has(resultWith().resultThruAbi(
										ContractResources.GET_CHILD_RESULT_ABI,
										isLiteralResult(new Object[] { BigInteger.valueOf(7L) })))
				);
	}

	private HapiApiSpec impureCallFails() {
		return defaultHapiSpec("ImpureCallFails")
				.given(
						fileCreate("parentDelegateBytecode").path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode").adminKey(THRESHOLD)
				).when().then(
						sleepFor(3_000L),
						contractCallLocal("parentDelegate", ContractResources.CREATE_CHILD_ABI)
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION)
				);
	}

	private HapiApiSpec deletedContract() {
		return defaultHapiSpec("InvalidDeletedContract")
				.given(
						fileCreate("parentDelegateBytecode").path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode")
				).when(
						contractDelete("parentDelegate")
				).then(
						contractCallLocal("parentDelegate", ContractResources.CREATE_CHILD_ABI)
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(CONTRACT_DELETED)
				);
	}

	private HapiApiSpec invalidContractID() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();
		return defaultHapiSpec("InvalidContractID")
				.given(
				).when()
				.then(
						contractCallLocal(invalidContract, ContractResources.CREATE_CHILD_ABI)
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(INVALID_CONTRACT_ID),
						contractCallLocal("0.0.0", ContractResources.CREATE_CHILD_ABI)
								.nodePayment(1_234_567)
								.hasAnswerOnlyPrecheck(INVALID_CONTRACT_ID)
				);
	}

	private HapiApiSpec insufficientFeeFails() {
		final long ADEQUATE_QUERY_PAYMENT = 500_000L;

		return defaultHapiSpec("InsufficientFee")
				.given(
						cryptoCreate("payer"),
						fileCreate("parentDelegateBytecode")
								.path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate")
								.bytecode("parentDelegateBytecode")
				).when(
						contractCall("parentDelegate", ContractResources.CREATE_CHILD_ABI)
				).then(
						sleepFor(3_000L),
						contractCallLocal("parentDelegate", ContractResources.GET_CHILD_RESULT_ABI)
								.nodePayment(ADEQUATE_QUERY_PAYMENT)
								.fee(0L)
								.payingWith("payer")
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE));
	}

	private HapiApiSpec lowBalanceFails() {
		final long ADEQUATE_QUERY_PAYMENT = 500_000_000L;

		return defaultHapiSpec("LowBalanceFails")
				.given(
						fileCreate("parentDelegateBytecode")
								.path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode"),
						cryptoCreate("payer").balance(ADEQUATE_QUERY_PAYMENT)
				).when(
						contractCall("parentDelegate", ContractResources.CREATE_CHILD_ABI)
				).then(
						sleepFor(3_000L),
						contractCallLocal("parentDelegate", ContractResources.GET_CHILD_RESULT_ABI)
								.logged()
								.payingWith("payer")
								.nodePayment(ADEQUATE_QUERY_PAYMENT)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE),
						getAccountBalance("payer").logged(),
						sleepFor(1_000L),
						getAccountBalance("payer").logged()
				);
	}

	private HapiApiSpec contractCallLocalAccountAndStorageKeysWarmUpReducesGasCost() {

		return defaultHapiSpec("ContractCallLocalAccountAndStorageKeysWarmUpReducesGasCost")
				.given(
						fileCreate("contractInteractionBaseBytecode").path(ContractResources.CONTRACT_INTERACTIONS_BASE),
						fileCreate("contractInteractionExtraBytecode").path(ContractResources.CONTRACT_INTERACTIONS_EXTRA)
				).when(
						contractCreate("baseContract").bytecode("contractInteractionBaseBytecode").gas(1_000_000L)
				).then(
						withOpContext((spec, opLog) -> {
							var baseContractId = spec.registry().getContractId("baseContract");
							var baseContractAddress = CommonUtils.calculateSolidityAddress(
									(int) baseContractId.getShardNum(),
									baseContractId.getRealmNum(),
									baseContractId.getContractNum());

							var baseContractSetParamsCall = contractCall("baseContract",
									ContractResources.BASE_CONTRACT_SET_PARAMS_ABI,
									1, 1).hasKnownStatus(SUCCESS).via("baseContractSetParamsCallTx");
							var extraContractCreate = contractCreate("extraContract",
									ContractResources.EXTRA_CONTRACT_CONSTRUCTOR_ABI, baseContractAddress)
									.bytecode("contractInteractionExtraBytecode")
									.gas(1_000_000L)
									.via("extraContractCreateTx");

							var contractCallLocal = contractCallLocal("extraContract",
									ContractResources.EXTRA_CONTRACT_GET_SUM_OF_BASE_A_AND_B_ABI)
									.has(resultWith().gasUsed(10_499L));

							var contractCallLocalWithAccountWarmUp = contractCallLocal(
									"extraContract",
									ContractResources.EXTRA_CONTRACT_GET_SUM_OF_BASE_A_AND_B_ABI)
									.withAccessList(baseContractId)
									.has(resultWith().gasUsed(10_399L));

							var contractCallLocalWithAccountAndStorageKeyWarmUp = contractCallLocal(
									"extraContract",
									ContractResources.EXTRA_CONTRACT_GET_SUM_OF_BASE_A_AND_B_ABI)
									.withAccessList(baseContractId,
											ByteString.copyFromUtf8("\000"),
											ByteString.copyFromUtf8("\001"))
									.has(resultWith().gasUsed(10_199L));

							CustomSpecAssert.allRunFor(spec,
									baseContractSetParamsCall,
									extraContractCreate,
									contractCallLocal,
									contractCallLocalWithAccountWarmUp,
									contractCallLocalWithAccountAndStorageKeyWarmUp);
						})
				);
	}

	private HapiApiSpec rejectsInvalidStorageAccessKey() {

		return defaultHapiSpec("RejectsInvalidStorageAccessKey")
				.given(
						fileCreate("contractInteractionBaseBytecode").path(ContractResources.CONTRACT_INTERACTIONS_BASE),
						fileCreate("contractInteractionExtraBytecode").path(ContractResources.CONTRACT_INTERACTIONS_EXTRA)
				).when(
						contractCreate("baseContract").bytecode("contractInteractionBaseBytecode").gas(1_000_000L)
				).then(
						withOpContext((spec, opLog) -> {
							var baseContractId = spec.registry().getContractId("baseContract");
							var baseContractAddress = CommonUtils.calculateSolidityAddress(
									(int) baseContractId.getShardNum(),
									baseContractId.getRealmNum(),
									baseContractId.getContractNum());

							var baseContractSetParamsCall = contractCall("baseContract",
									ContractResources.BASE_CONTRACT_SET_PARAMS_ABI,
									1, 1).hasKnownStatus(SUCCESS).via("baseContractSetParamsCallTx");
							var extraContractCreate = contractCreate("extraContract",
									ContractResources.EXTRA_CONTRACT_CONSTRUCTOR_ABI, baseContractAddress)
									.bytecode("contractInteractionExtraBytecode")
									.gas(1_000_000L)
									.via("extraContractCreateTx");

							var contractCallLocal = contractCallLocal("extraContract",
									ContractResources.EXTRA_CONTRACT_GET_SUM_OF_BASE_A_AND_B_ABI)
									.withAccessList(baseContractId,
											ByteString.copyFromUtf8("\000\000\000\000\000\000\000\000\000\000\000\000" +
													"\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000" +
													"\000\000\000\000\000"),
											ByteString.copyFromUtf8("\001"))
									.hasAnswerOnlyPrecheck(INVALID_STORAGE_ACCESS_KEY);

							CustomSpecAssert.allRunFor(spec,
									baseContractSetParamsCall,
									extraContractCreate,
									contractCallLocal);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
