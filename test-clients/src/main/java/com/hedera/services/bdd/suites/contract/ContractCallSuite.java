package com.hedera.services.bdd.suites.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.*;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.*;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class ContractCallSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallSuite.class);
	final String PATH_TO_SIMPLE_STORAGE_BYTECODE = "src/main/resource/testfiles/simpleStorage.bin";
	final String PATH_TO_PAYABLE_CONTRACT_BYTECODE = "src/main/resource/PayReceivable.bin";
	final String PATH_TO_DELEGATING_CONTRACT_BYTECODE = "src/main/resource/testfiles/CreateTrivial.bin";
	final String CREATE_CHILD_ABI = "{\"constant\":false,\"inputs\":[],\"name\":\"create\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	final String GET_CHILD_RESULT_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getIndirect\",\"outputs\":[{\"name\":\"value\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	final String GET_CHILD_ADDRESS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getAddress\",\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	final String SEND_FUNDS = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	final String DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ContractCallSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs(),
				Arrays.asList(
						fridayThe13thSpec()
				)
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return Arrays.asList(
			insufficientFee(),
			invalidContract(),
			invalidAbi(),
			nonPayable()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList(
				vanillaSuccess(),
				payableSuccess(),
				depositSuccess()
		);
	}

	HapiApiSpec fridayThe13thSpec() {
		long newExpiry = Instant.now().getEpochSecond() + DEFAULT_PROPS.defaultExpirationSecs() * 2;
		long betterExpiry = Instant.now().getEpochSecond() + DEFAULT_PROPS.defaultExpirationSecs() * 3;
		final String INITIAL_MEMO = "This is a memo string with only Ascii characters";
		final String NEW_MEMO = "Turning and turning in the widening gyre, the falcon cannot hear the falconer...";
		final String BETTER_MEMO = "This was Mr. Bleaney's room...";
		KeyShape initialKeyShape = KeyShape.SIMPLE;
		KeyShape newKeyShape = listOf(3);

		return defaultHapiSpec("FridayThe13thSpec")
				.given(
						newKeyNamed("initialAdminKey").shape(initialKeyShape),
						newKeyNamed("newAdminKey").shape(newKeyShape),
						cryptoCreate("payer")
								.balance(9999999999999999L),
						fileCreate("bytecode")
								.path(PATH_TO_SIMPLE_STORAGE_BYTECODE)
								.payingWith("payer")
				).when(
						contractCreate("immutableContract")
								.payingWith("payer")
								.omitAdminKey()
								.bytecode("bytecode"),
						contractCreate("contract")
								.payingWith("payer")
								.adminKey("initialAdminKey")
								.memo(INITIAL_MEMO)
								.bytecode("bytecode"),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(INITIAL_MEMO)
										.adminKey("initialAdminKey"))
				).then(
						contractUpdate("contract")
								.payingWith("payer")
								.newKey("newAdminKey")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("contract")
								.payingWith("payer")
								.newKey("newAdminKey")
								.signedBy("payer", "newAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("contract")
								.payingWith("payer")
								.newKey("newAdminKey"),
						contractUpdate("contract")
								.payingWith("payer")
								.newExpiryTime(newExpiry)
								.newMemo(NEW_MEMO),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.solidityAddress("contract")
										.memo(NEW_MEMO)
										.expiry(newExpiry)),
						contractUpdate("contract")
								.payingWith("payer")
								.newMemo(BETTER_MEMO),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(BETTER_MEMO)
										.expiry(newExpiry)),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer")
								.newExpiryTime(betterExpiry),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(BETTER_MEMO)
										.expiry(betterExpiry)),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer")
								.newExpiryTime(newExpiry)
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer")
								.newMemo(NEW_MEMO)
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("immutableContract")
								.payingWith("payer")
								.newMemo(BETTER_MEMO)
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractDelete("immutableContract")
								.payingWith("payer")
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractUpdate("immutableContract")
								.payingWith("payer")
								.newExpiryTime(betterExpiry),
						contractDelete("contract")
								.payingWith("payer")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractDelete("contract")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractDelete("contract")
								.payingWith("payer")
								.hasKnownStatus(SUCCESS)
				);
	}

	HapiApiSpec depositSuccess() {
		return defaultHapiSpec("DepositSuccess")
				.given(
						fileCreate("payableBytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE),
						contractCreate("payableContract").bytecode("payableBytecode").adminKey(THRESHOLD)
				).when(
						contractCall("payableContract", DEPOSIT, 1_000L)
								.via("payTxn").sending(1_000L)
				).then(
						getTxnRecord("payTxn")
								.has(recordWith().contractCallResult(
										resultWith().logs(inOrder()))));
	}

	HapiApiSpec payableSuccess() {
		return defaultHapiSpec("PayableSuccess")
				.given(
						fileCreate("payableBytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE),
						contractCreate("payableContract").bytecode("payableBytecode").adminKey(THRESHOLD)
				).when(
						contractCall("payableContract").via("payTxn").sending(1_000L)
				).then(
						getTxnRecord("payTxn")
								.has(recordWith().contractCallResult(
										resultWith().logs(
												inOrder(
														logWith().longAtBytes(1_000L, 24))))));
	}

	HapiApiSpec vanillaSuccess() {
		return defaultHapiSpec("VanillaSuccess")
				.given(
						fileCreate("parentDelegateBytecode").path(PATH_TO_DELEGATING_CONTRACT_BYTECODE),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode").adminKey(THRESHOLD),
						getContractInfo("parentDelegate").saveToRegistry("parentInfo")
				).when(
						contractCall("parentDelegate", CREATE_CHILD_ABI).via("createChildTxn"),
						contractCall("parentDelegate", GET_CHILD_RESULT_ABI).via("getChildResultTxn"),
						contractCall("parentDelegate", GET_CHILD_ADDRESS_ABI).via("getChildAddressTxn")
				).then(
						getTxnRecord("createChildTxn")
								.saveCreatedContractListToRegistry("createChild")
								.logged(),
						getTxnRecord("getChildResultTxn")
								.has(recordWith().contractCallResult(
										resultWith().resultThruAbi(
												GET_CHILD_RESULT_ABI,
												isLiteralResult(new Object[] { BigInteger.valueOf(7L) })))),
						getTxnRecord("getChildAddressTxn")
								.has(recordWith().contractCallResult(
										resultWith()
											.resultThruAbi(
													GET_CHILD_ADDRESS_ABI,
													isContractWith(contractWith()
															.nonNullContractId()
															.propertiesInheritedFrom("parentInfo")))
											.logs(inOrder()))),
						contractListWithPropertiesInheritedFrom("createChildCallResult", 1, "parentInfo")
				);
	}

	HapiApiSpec insufficientFee() {
		return defaultHapiSpec("InsufficientFee")
				.given(
						cryptoCreate("accountToPay"),
						fileCreate("parentDelegateBytecode").path(PATH_TO_DELEGATING_CONTRACT_BYTECODE),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode")
				).when().then(
						contractCall("parentDelegate", CREATE_CHILD_ABI).fee(0L)
								.payingWith("accountToPay")
								.hasPrecheck(INSUFFICIENT_TX_FEE));
	}

	HapiApiSpec nonPayable() {
		return defaultHapiSpec("NonPayable")
				.given(
						fileCreate("parentDelegateBytecode").path(PATH_TO_DELEGATING_CONTRACT_BYTECODE),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode")
				).when(
						contractCall("parentDelegate", CREATE_CHILD_ABI).via("callTxn").sending(1_000L)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				).then(
						getTxnRecord("callTxn").has(
								recordWith().contractCallResult(
									resultWith().logs(inOrder())))
				);
	}

	HapiApiSpec invalidContract() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();

		return defaultHapiSpec("InvalidContract")
				.given().when().then(
						contractCall(invalidContract, CREATE_CHILD_ABI)
								.hasPrecheck(INVALID_CONTRACT_ID));
	}

	HapiApiSpec invalidAbi() {
		return defaultHapiSpec("InvalidAbi")
				.given(
						fileCreate("parentDelegateBytecode").path(PATH_TO_DELEGATING_CONTRACT_BYTECODE),
						contractCreate("parentDelegate").bytecode("parentDelegateBytecode")
				).when().then(
						contractCall("parentDelegate", SEND_FUNDS)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
