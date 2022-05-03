package com.hedera.services.bdd.suites.ethereum;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.ethereum.EthTxData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toList;

public class EthereumSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(EthereumSuite.class);
	private static final long depositAmount = 20_000L;

	private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";

	public static void main(String... args) {
		new EthereumSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return Stream.concat(
				feePaymentMatrix().stream(),
				Stream.of(
//						invalidTxData(),
//						ETX_014_contractCreateInheritsSignerProperties(),
//						invalidNonceEthereumTxFails(),
						ethSenderIsChargedWholeGasFeeSucceeds(),
						relayerPaysAllGasSucceeds(),
						lowGasPriceAndNoGasAllowanceFails()
				)).toList();
	}

	HapiApiSpec lowGasPriceAndNoGasAllowanceFails() {
		return defaultHapiSpec("relayerPaysAllGasSucceeds")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						uploadDefaultFeeSchedules(GENESIS)
				).then(
						ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
								.type(EthTxData.EthTransactionType.EIP1559)
								.signingWith(SECP_256K1_SOURCE_KEY)
								.payingWith(RELAYER)
								.via("payTxn")
								.nonce(0)
								.maxGasAllowance(0)
								.maxFeePerGas(4L)
								.gasLimit(1_000_000L)
								.sending(depositAmount)
								.hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
				);
	}


	HapiApiSpec ethSenderIsChargedWholeGasFeeSucceeds() {
		return defaultHapiSpec("relayerPaysAllGasSucceeds")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						uploadDefaultFeeSchedules(GENESIS)
				).then(
						withOpContext((spec, ignore) -> {
							final String senderBalance = "balance";
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
									.maxGasAllowance(ONE_HUNDRED_HBARS)
									.maxFeePerGas(50L)
									.gasLimit(1_000_000L)
									.sending(depositAmount)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);

							final HapiGetTxnRecord hapiGetTxnRecord = getTxnRecord("payTxn").logged();
							allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);
							TransactionRecord responseRecord = hapiGetTxnRecord.getResponseRecord();
							var fees = responseRecord.getTransactionFee();
							final var subop4 = getAliasedAccountBalance(SECP_256K1_SOURCE_KEY).hasTinyBars(
									changeFromSnapshot(senderBalance, -depositAmount - fees));
							final var subop5 = getAccountBalance(RELAYER).hasTinyBars(
									changeFromSnapshot(payerBalance, 0));
							allRunFor(spec, subop4, subop5);
						})
				);
	}


	HapiApiSpec relayerPaysAllGasSucceeds() {
		return defaultHapiSpec("relayerPaysAllGasSucceeds")
				.given(
						newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
						cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
						cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
								.via("autoAccount"),
						getTxnRecord("autoAccount").andAllChildRecords(),
						uploadInitCode(PAY_RECEIVABLE_CONTRACT),
						contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
				).when(
						uploadDefaultFeeSchedules(GENESIS)
				).then(
						withOpContext((spec, ignore) -> {
							final String senderBalance = "balance";
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
									.maxGasAllowance(ONE_HUNDRED_HBARS)
									.maxFeePerGas(0L)
									.gasLimit(1_000_000L)
									.sending(depositAmount)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);

							final HapiGetTxnRecord hapiGetTxnRecord = getTxnRecord("payTxn").logged();
							allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);
							final var subop4 = getAliasedAccountBalance(SECP_256K1_SOURCE_KEY).hasTinyBars(
									changeFromSnapshot(senderBalance, -depositAmount));
							var fees = hapiGetTxnRecord.getResponseRecord().getTransactionFee();
							final var subop5 = getAccountBalance(RELAYER).hasTinyBars(
									changeFromSnapshot(payerBalance, -fees));
							allRunFor(spec, subop4, subop5);
						})
				);
	}


	List<HapiApiSpec> feePaymentMatrix() {
		final long gasPrice = 47;
		final long gasLimit = 800_000;

		final long noPayment = 0L;
		final long partialFee = gasPrice / 3;
		final long partialPayment = partialFee * gasLimit;
		final long fullAllowance = gasPrice * gasLimit * 5/4;
		final long fullPayment = gasPrice * gasLimit;

		return Stream.of(
				new Object[] { false, noPayment, noPayment, noPayment, noPayment },
				new Object[] { false, noPayment, partialPayment, noPayment, noPayment },
				new Object[] { true, noPayment, fullAllowance, noPayment, fullPayment },
				new Object[] { false, partialFee, noPayment, noPayment, noPayment },
				new Object[] { false, partialFee, partialPayment, noPayment, noPayment },
				new Object[] { true, partialFee, fullAllowance, partialPayment, fullPayment - partialPayment },
				new Object[] { true, gasPrice, noPayment, fullPayment, noPayment },
				new Object[] { true, gasPrice, partialPayment, fullPayment, noPayment },
				new Object[] { true, gasPrice, fullAllowance, fullPayment, noPayment }
		).map(params ->
				// [0] - success
				// [1] - sender gas price
				// [2] - relayer offered
				// [1] - sender charged amount
				// [2] - relayer charged amount 
				matrixedPayerRelayerTest((boolean) params[0],
						(long) params[1],
						(long) params[2],
						(long) params[3],
						(long) params[4])
		).toList();
	}

	HapiApiSpec matrixedPayerRelayerTest(
			boolean success,
			long senderGasPrice,
			long relayerOffered,
			long senderCharged,
			long relayerCharged
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
									.gasLimit(1_000_000L)
									.sending(depositAmount)
									.hasKnownStatus(
											success ? ResponseCodeEnum.SUCCESS : ResponseCodeEnum.INSUFFICIENT_TX_FEE);

							final HapiGetTxnRecord hapiGetTxnRecord = getTxnRecord("payTxn").logged();
							allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);

							if (success) {
								var fees = hapiGetTxnRecord.getResponseRecord().getTransactionFee();
								final var subop4 = getAliasedAccountBalance(SECP_256K1_SOURCE_KEY).hasTinyBars(
										changeFromSnapshot(senderBalance, -depositAmount - senderCharged));
								final var subop5 = getAccountBalance(RELAYER).hasTinyBars(
										changeFromSnapshot(payerBalance, -relayerCharged));
								allRunFor(spec, subop4, subop5);
							}
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

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}