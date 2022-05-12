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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hedera.services.ethereum.EthTxData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiEthereumCall.ETH_HASH_KEY;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class EthereumSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(EthereumSuite.class);
	private static final long depositAmount = 20_000L;
	private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
	private static final String HELLO_WORLD_MINT_CONTRACT = "HelloWorldMint";
	private static final long GAS_LIMIT = 1_000_000;

	public static final String ERC20_CONTRACT = "ERC20Contract";

	private static final String FUNGIBLE_TOKEN = "fungibleToken";

	public static void main(String... args) {
		new EthereumSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return Stream.concat(
				feePaymentMatrix().stream(),
				Stream.of(
						invalidTxData(),
						ETX_010_transferToCryptoAccountSucceeds(),
						ETX_012_precompileCallSucceedsWhenNeededSignatureInEthTxn(),
						ETX_013_precompileCallSucceedsWhenNeededSignatureInHederaTxn(),
						ETX_014_contractCreateInheritsSignerProperties(),
						invalidNonceEthereumTxFails(),
						ETX_026_accountWithoutAliasCannotMakeEthTxns(),
						ETX_009_callsToTokenAddresses()
				)).toList();
	}

	HapiApiSpec ETX_010_transferToCryptoAccountSucceeds() {
		String RECEIVER = "RECEIVER";
		final String aliasBalanceSnapshot = "aliasBalance";
		return defaultHapiSpec("ETX_010_transferToCryptoAccountSucceeds")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RECEIVER)
								.balance(0L),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords()
				).when(
						balanceSnapshot(aliasBalanceSnapshot, SECP_256K1_SOURCE_KEY).accountIsAlias(),
						ethereumCryptoTransfer(RECEIVER, FIVE_HBARS)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(0)
								.maxFeePerGas(0L)
								.maxGasAllowance(FIVE_HBARS)
								.gasLimit(2_000_000L)
								.via("payTxn")
								.hasKnownStatus(SUCCESS)
				).then(
						withOpContext((spec, opLog) -> allRunFor(spec, getTxnRecord("payTxn")
								.logged()
								.hasPriority(recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.logs(inOrder())
														.senderId(spec.registry().getAccountID(
																spec.registry().aliasIdFor(SECP_256K1_SOURCE_KEY)
																		.getAlias().toStringUtf8())))
										.ethereumHash(ByteString.copyFrom(spec.registry().getBytes(ETH_HASH_KEY)))))),
						getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
								.has(accountWith().nonce(1L)),
						getAccountBalance(RECEIVER).hasTinyBars(FIVE_HBARS),
						getAliasedAccountBalance(SECP_256K1_SOURCE_KEY).hasTinyBars(
								changeFromSnapshot(aliasBalanceSnapshot, -FIVE_HBARS))
				);
	}

	List<HapiApiSpec> feePaymentMatrix() {
		final long gasPrice = 47;
		final long chargedGasLimit = GAS_LIMIT * 4 / 5;

		final long noPayment = 0L;
		final long thirdOfFee = gasPrice / 3;
		final long thirdOfPayment = thirdOfFee * chargedGasLimit;
		final long thirdOfLimit = thirdOfFee * GAS_LIMIT;
		final long fullAllowance = gasPrice * chargedGasLimit * 5 / 4;
		final long fullPayment = gasPrice * chargedGasLimit;
		final long ninteyPercentFee = gasPrice * 9 / 10;

		return Stream.of(
				new Object[] { false, noPayment, noPayment, noPayment, noPayment },
				new Object[] { false, noPayment, thirdOfPayment, noPayment, noPayment },
				new Object[] { true, noPayment, fullAllowance, noPayment, fullPayment },
				new Object[] { false, thirdOfFee, noPayment, noPayment, noPayment },
				new Object[] { false, thirdOfFee, thirdOfPayment, noPayment, noPayment },
				new Object[] { true, thirdOfFee, fullAllowance, thirdOfLimit, fullPayment - thirdOfLimit },
				new Object[] { true, thirdOfFee, fullAllowance * 9 / 10, thirdOfLimit, fullPayment - thirdOfLimit },
				new Object[] { false, ninteyPercentFee, noPayment, noPayment, noPayment },
				new Object[] { true, ninteyPercentFee, thirdOfPayment, fullPayment, noPayment },
				new Object[] { true, gasPrice, noPayment, fullPayment, noPayment },
				new Object[] { true, gasPrice, thirdOfPayment, fullPayment, noPayment },
				new Object[] { true, gasPrice, fullAllowance, fullPayment, noPayment }
		).map(params ->
				// [0] - success
				// [1] - sender gas price
				// [2] - relayer offered
				// [3] - sender charged amount
				// [4] - relayer charged amount 
				matrixedPayerRelayerTest(
						(boolean) params[0],
						(long) params[1],
						(long) params[2],
						(long) params[3],
						(long) params[4])
		).toList();
	}

	HapiApiSpec matrixedPayerRelayerTest(
			final boolean success,
			final long senderGasPrice,
			final long relayerOffered,
			final long senderCharged,
			final long relayerCharged
	) {
		return defaultHapiSpec(
				"feePaymentMatrix " + (success ? "Success/" : "Failure/") + senderGasPrice + "/" + relayerOffered)
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(
								tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						// Network and Node fees in the schedule for Ethereum transactions are 0,
						// so everything charged will be from the gas consumed in the EVM execution
						uploadDefaultFeeSchedules(GENESIS)
				).then(
						withOpContext((spec, ignore) -> {
							final String senderBalance = "senderBalance";
							final String payerBalance = "payerBalance";
							final var subop1 =
									balanceSnapshot(senderBalance, SECP_256K1_SOURCE_KEY)
											.accountIsAlias();
							final var subop2 = balanceSnapshot(payerBalance, RELAYER);
							final var subop3 = ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
									.type(EthTxData.EthTransactionType.EIP1559)
									.signingWith(SECP_256K1_SOURCE_KEY)
									.payingWith(RELAYER)
									.via("payTxn")
									.nonce(0)
									.maxGasAllowance(relayerOffered)
									.maxFeePerGas(senderGasPrice)
									.gasLimit(GAS_LIMIT)
									.sending(depositAmount)
									.hasKnownStatus(
											success ? ResponseCodeEnum.SUCCESS : ResponseCodeEnum.INSUFFICIENT_TX_FEE);

							final HapiGetTxnRecord hapiGetTxnRecord = getTxnRecord("payTxn").logged();
							allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);

							final var subop4 = getAliasedAccountBalance(SECP_256K1_SOURCE_KEY).hasTinyBars(
									changeFromSnapshot(senderBalance, success ? (-depositAmount - senderCharged) : 0));
							final var subop5 = getAccountBalance(RELAYER).hasTinyBars(
									changeFromSnapshot(payerBalance, success ? -relayerCharged : 0));
							allRunFor(spec, subop4, subop5);
						})
				);
	}

	HapiApiSpec invalidTxData() {
		return defaultHapiSpec("InvalidTxData")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(
								tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)).via(
								"autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),


						uploadInitCode(PAY_RECEIVABLE_CONTRACT)
				).when(
						ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
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
								.maxGasAllowance(ONE_HUNDRED_HBARS)
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

	HapiApiSpec ETX_026_accountWithoutAliasCannotMakeEthTxns() {
		final String ACCOUNT = "account";
		return defaultHapiSpec("accountWithoutAliasCannotMakeEthTxns")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(ACCOUNT)
								.key(SECP_256K1_SOURCE_KEY)
								.balance(ONE_HUNDRED_HBARS)
				).when(
						ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(ACCOUNT)
								.nonce(0)
								.gasLimit(GAS_LIMIT)
								.hasKnownStatus(INVALID_ACCOUNT_ID)
				).then();
	}

	HapiApiSpec ETX_012_precompileCallSucceedsWhenNeededSignatureInEthTxn() {
		final AtomicLong fungibleNum = new AtomicLong();
		final String fungibleToken = "token";
		final String mintTxn = "mintTxn";
		return defaultHapiSpec("ETX_012_precompileCallSucceedsWhenNeededSignatureInEthTxn")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.adminKey(SECP_256K1_SOURCE_KEY)
								.supplyKey(SECP_256K1_SOURCE_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(HELLO_WORLD_MINT_CONTRACT, fungibleNum.get())),
						ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", 5
						)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.nonce(0)
								.gasPrice(50L)
								.maxGasAllowance(FIVE_HBARS)
								.gasLimit(1_000_000L)
								.via(mintTxn)
								.hasKnownStatus(SUCCESS)
				).then(
						withOpContext((spec, opLog) -> allRunFor(spec, getTxnRecord(mintTxn)
								.logged()
								.hasPriority(recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.logs(inOrder())
														.senderId(spec.registry().getAccountID(
																spec.registry().aliasIdFor(SECP_256K1_SOURCE_KEY)
																		.getAlias().toStringUtf8())))
										.ethereumHash(ByteString.copyFrom(spec.registry().getBytes(ETH_HASH_KEY))))))
				);
	}

	HapiApiSpec ETX_013_precompileCallSucceedsWhenNeededSignatureInHederaTxn() {
		final AtomicLong fungibleNum = new AtomicLong();
		final String fungibleToken = "token";
		final String mintTxn = "mintTxn";
		final String MULTI_KEY = "MULTI_KEY";
		return defaultHapiSpec("ETX_013_precompileCallSucceedsWhenNeededSignatureInHederaTxn")
				.given(
						newKeyNamed(MULTI_KEY),
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(HELLO_WORLD_MINT_CONTRACT, fungibleNum.get())),
						ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", 5
						)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.alsoSigningWithFullPrefix(MULTI_KEY)
								.nonce(0)
								.gasPrice(50L)
								.maxGasAllowance(FIVE_HBARS)
								.gasLimit(1_000_000L)
								.via(mintTxn)
								.hasKnownStatus(SUCCESS)
				).then(
						withOpContext((spec, opLog) -> allRunFor(spec, getTxnRecord(mintTxn)
								.logged()
								.hasPriority(recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.logs(inOrder())
														.senderId(spec.registry().getAccountID(
																spec.registry().aliasIdFor(SECP_256K1_SOURCE_KEY)
																		.getAlias().toStringUtf8())))
										.ethereumHash(ByteString.copyFrom(spec.registry().getBytes(ETH_HASH_KEY))))))
				);
	}

	HapiApiSpec ETX_009_callsToTokenAddresses() {
		final AtomicReference<String> tokenNum = new AtomicReference<>();
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
								.supplyKey(SECP_256K1_SOURCE_KEY)
								.exposingCreatedIdTo(tokenNum::set),

						uploadInitCode(ERC20_CONTRACT),
						contractCreate(ERC20_CONTRACT).adminKey(THRESHOLD)
				).when(withOpContext(
								(spec, opLog) -> {
									allRunFor(
											spec,
											ethereumCallWithFunctionAbi(true, FUNGIBLE_TOKEN,
													getABIFor(Utils.FunctionType.FUNCTION, "totalSupply", "ERC20ABI"))
													.type(EthTxData.EthTransactionType.EIP1559)
													.signingWith(SECP_256K1_SOURCE_KEY)
													.payingWith(RELAYER)
													.via("totalSupplyTxn")
													.nonce(0)
													.gasPrice(50L)
													.maxGasAllowance(FIVE_HBARS)
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
