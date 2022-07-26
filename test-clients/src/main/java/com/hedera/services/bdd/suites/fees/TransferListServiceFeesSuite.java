package com.hedera.services.bdd.suites.fees;

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
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.from;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.missingPayments;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;

public class TransferListServiceFeesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TransferListServiceFeesSuite.class);

	final Function<Long, Long> sufficientBalanceFn = fee -> 2L * fee;

	public static void main(String... args) {
		new TransferListServiceFeesSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				nobodyPaysRecordFeeForInvalidTxn(),
				noExtraFeeChargedForCreationTransfer(),
				nodeCoversForBrokePayer()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
		);
	}

	private final String FEE_CHARGED = "feeCharged";

	private final BiFunction<String, Function<Long, Long>, Function<HapiApiSpec, Long>> getBalanceFromFee =
			(name, balanceFn) -> spec -> {
		HapiGetTxnRecord referenceTransferRec = QueryVerbs.getTxnRecord(name);
		allRunFor(spec, referenceTransferRec);
		TransactionRecord record =
				referenceTransferRec.getResponse().getTransactionGetRecord().getTransactionRecord();
		long feeCharged = record.getTransactionFee();
		spec.registry().saveAmount(FEE_CHARGED, feeCharged);
		return balanceFn.apply(feeCharged);
	};

	private HapiApiSpec nodeCoversForBrokePayer() {
		final long TRANSFER_AMOUNT = 1_000L;

		return defaultFailingHapiSpec("NodeCoversForBrokePayer")
				.given(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, NODE, TRANSFER_AMOUNT)
						).via("referenceTransfer")
				).when(
						cryptoCreate("payer")
								.balance(getBalanceFromFee.apply("referenceTransfer", sufficientBalanceFn)),
						UtilVerbs.inParallel(
							cryptoTransfer(
									tinyBarsFromTo(
											"payer", EXCHANGE_RATE_CONTROL, spec -> spec.registry().getAmount(FEE_CHARGED))
							).via("txnA").payingWith("payer").hasAnyPrecheck().hasAnyKnownStatus(),
							cryptoTransfer(
									tinyBarsFromTo(
											"payer", EXCHANGE_RATE_CONTROL, spec -> spec.registry().getAmount(FEE_CHARGED))
							).via("txnB").payingWith("payer").hasAnyPrecheck().hasAnyKnownStatus()
						)
				).then(
					UtilVerbs.assertionsHold((spec, assertLog) -> {
					})
				);
	}

	private HapiApiSpec noExtraFeeChargedForCreationTransfer() {
		return defaultHapiSpec("NodeCoversExtraServiceFee")
				.given(
						cryptoCreate("anon").via("referenceCreation"),
						cryptoCreate("payer")
								.balance(getBalanceFromFee.apply("referenceCreation", sufficientBalanceFn))
				).when(
						cryptoCreate("child")
								.payingWith("payer")
								.balance(spec -> spec.registry().getAmount(FEE_CHARGED))
								.via("subjectTxn"),
						QueryVerbs.getAccountBalance("payer").hasTinyBars(0L)
				).then();
	}

	private HapiApiSpec nobodyPaysRecordFeeForInvalidTxn() {
		final long TRANSFER_AMOUNT = 1_000L;

		return defaultHapiSpec("NobodyPaysRecordFeeForInvalidTxn")
				.given(
						cryptoCreate("A"), cryptoCreate("B"), cryptoCreate("anon"),
						cryptoTransfer(
								tinyBarsFromTo("anon", "A", TRANSFER_AMOUNT),
								tinyBarsFromTo("anon", "B", TRANSFER_AMOUNT)
						).via("referenceTransfer").payingWith("anon")
				).when(
						cryptoCreate("payer")
								.balance(getBalanceFromFee.apply("referenceTransfer", Function.identity())),
						cryptoTransfer(
								tinyBarsFromTo("payer", "A", TRANSFER_AMOUNT),
								tinyBarsFromTo("payer", "B", TRANSFER_AMOUNT)
						).payingWith("payer").via("subjectTransfer").hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
				).then(
						UtilVerbs.assertionsHold((spec, assertLog) -> {
							HapiGetTxnRecord refOp = QueryVerbs.getTxnRecord("referenceTransfer");
							allRunFor(spec, refOp);
							AccountID payer = spec.registry().getAccountID("anon");
							TransactionRecord refRecord =
									refOp.getResponse().getTransactionGetRecord().getTransactionRecord();
							boolean isServicePayment = false;
							long serviceFee = 0L;
							for (AccountAmount entry : refRecord.getTransferList().getAccountAmountsList()) {
								if (entry.getAccountID().equals(payer)) {
									if (!isServicePayment) {
										isServicePayment = true;
									} else {
										serviceFee = -1L * entry.getAmount();
										break;
									}
								}
							}
							assertLog.info("Should(?!) be missing fee of " + serviceFee + "...");
							HapiGetTxnRecord subOp = QueryVerbs.getTxnRecord("subjectTransfer").noLogging()
									.hasPriority(recordWith().transfers(missingPayments(from("payer", serviceFee))));
							allRunFor(spec, subOp);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

