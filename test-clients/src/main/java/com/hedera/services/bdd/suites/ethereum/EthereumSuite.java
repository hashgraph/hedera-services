package com.hedera.services.bdd.suites.ethereum;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hedera.services.ethereum.EthTxData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.ethereum.EthTxData;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class EthereumSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(EthereumSuite.class);
	private static final long depositAmount = 20_000L;

	private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
	public static final String ERC20_CONTRACT = "ERC20Contract";

	private static final String FUNGIBLE_TOKEN = "fungibleToken";

	public static void main(String... args) {
		new EthereumSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				invalidTxData(),
				ETX_014_contractCreateInheritsSignerProperties(),
				invalidNonceEthereumTxFails(),
				ETX_09_callsToTokenAddresses()
		);
	}

	HapiApiSpec invalidTxData() {
		return defaultHapiSpec("InvalidTxData")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))    .via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),


						uploadInitCode(PAY_RECEIVABLE_CONTRACT)
				).when(
						ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
								.adminKey(THRESHOLD)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(0)
								.gasPrice(10L)
								.maxGasAllowance(5L)
								.maxPriorityGas(2L)
								.invalidateEthereumData()
								.gasLimit(1_000_000L).hasPrecheck(INVALID_ETHEREUM_TRANSACTION)
								.via("payTxn")
				).then();
	}


	HapiApiSpec ETX_014_contractCreateInheritsSignerProperties() {
		final AtomicReference<String> contractID = new AtomicReference<>();
		final String MEMO = "memo";
		final String PROXY = "proxy";
		final long INITIAL_BALANCE = 100L;
		final long AUTO_RENEW_PERIOD = THREE_MONTHS_IN_SECONDS + 60;
		return defaultHapiSpec("ContractCreateInheritsProperties")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						cryptoCreate(PROXY)
				).when(
						cryptoUpdateAliased(SECP_256K1_SOURCE_KEY)
								.autoRenewPeriod(AUTO_RENEW_PERIOD)
								.entityMemo(MEMO)
								.newProxy(PROXY)
								.payingWith(GENESIS)
								.signedBy(SECP_256K1_SOURCE_KEY, GENESIS),
						ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(0)
								.balance(INITIAL_BALANCE)
								.gasPrice(10L)
								.maxGasAllowance(5L)
								.maxPriorityGas(2L)
								.exposingNumTo(num -> contractID.set(
										asHexedSolidityAddress(0, 0, num)))
								.gasLimit(1_000_000L)
								.hasKnownStatus(SUCCESS),
						ethereumCall(PAY_RECEIVABLE_CONTRACT, "getBalance")
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(1L)
								.gasPrice(10L)
								.gasLimit(1_000_000L)
								.hasKnownStatus(SUCCESS)
				).then(
						getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).logged(),
						sourcing(() -> getContractInfo(contractID.get()).logged()
								.has(ContractInfoAsserts.contractWith()
										.adminKey(SECP_256K1_SOURCE_KEY)
										.autoRenew(AUTO_RENEW_PERIOD)
										.balance(INITIAL_BALANCE)
										.memo(MEMO))
						)
				);
	}

	HapiApiSpec invalidNonceEthereumTxFails() {
		return defaultHapiSpec("InvalidNonceEthereumTxFails")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),

						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.via("payTxn")
								.nonce(1l)
								.gasPrice(10L)
								.maxGasAllowance(5L)
								.maxPriorityGas(2L)
								.gasLimit(1_000_000L)
								.sending(depositAmount)
								.hasKnownStatus(ResponseCodeEnum.WRONG_NONCE),
						ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.via("payTxn")
								.nonce(-111111111l)
								.gasPrice(10L)
								.maxGasAllowance(5L)
								.maxPriorityGas(2L)
								.gasLimit(1_000_000L)
								.sending(depositAmount)
								.hasKnownStatus(ResponseCodeEnum.WRONG_NONCE)
				).then(
						getTxnRecord("payTxn")
								.hasPriority(recordWith().contractCallResult(
										resultWith().logs(inOrder()))),
						getAccountBalance(RELAYER).hasTinyBars(6 * ONE_MILLION_HBARS)
				);
	}

	HapiApiSpec ETX_09_callsToTokenAddresses() {
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();
		final var totalSupply = 50;

		return defaultHapiSpec("CallsToTokenAddresses")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),

						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(totalSupply)
								.treasury(TOKEN_TREASURY)
								.adminKey(SECP_256K1_SOURCE_KEY)
								.supplyKey(SECP_256K1_SOURCE_KEY),

						uploadInitCode(ERC20_CONTRACT),
						contractCreate(ERC20_CONTRACT).adminKey(THRESHOLD)
				).when(withOpContext(
						(spec, opLog) -> {
							tokenAddr.set(asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)));
							allRunFor(
									spec,
									ethereumCall(ERC20_CONTRACT, "totalSupply", tokenAddr.get())
											.type(EthTxData.EthTransactionType.EIP1559)
											.signingWith(SECP_256K1_SOURCE_KEY)
											.payingWith(RELAYER)
											.via("totalSupplyTxn")
											.nonce(0)
											.gasPrice(10L)
											.maxGasAllowance(5L)
											.maxPriorityGas(2L)
											.gasLimit(1_000_000L)
											.hasKnownStatus(ResponseCodeEnum.SUCCESS)
							);
						}
						)
				).then(
						childRecordsCheck("totalSupplyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(totalSupply)
														)
										)
						)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
