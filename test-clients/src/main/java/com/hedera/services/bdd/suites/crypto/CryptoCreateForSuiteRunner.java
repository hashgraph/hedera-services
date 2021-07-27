package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.SuiteRunner;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * When running JRS regression tests using SuiteRunner we need to create unique payer accounts for each test client.
 * This class should be used only for that purpose and not be used in any other testing.
 */
public class CryptoCreateForSuiteRunner extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoCreateForSuiteRunner.class);
	private String nodes;
	private String defaultNode;

	// Use more initialBalance for this account as it is used as payer for the performance tests
	private static long initialBalance = 5L * LoadTest.initialBalance.getAsLong();

	public CryptoCreateForSuiteRunner(String nodes, String defaultNode) {
		this.nodes = nodes;
		this.defaultNode = defaultNode;
	}

	public static void main(String... args) {
		new CryptoCreateForSuiteRunner("localhost", "3").runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				createAccount()
		);
	}

	private HapiApiSpec createAccount() {
		int maxRetries = 5;
		return customHapiSpec("CreatePayerAccountForEachClient")
				.withProperties(Map.of(
						"nodes", nodes,
						"default.node", "0.0." + defaultNode
				)).given().when().then(
						withOpContext((spec, log) -> {
									var createdAuditablePayer = false;
									var retryCount = 0;
									while (!createdAuditablePayer && retryCount < maxRetries) {
										try {
											AccountID id = spec.registry().getAccountID(DEFAULT_PAYER);
											var cryptoCreateOp = cryptoCreate("payerAccount")
													.balance(id.getAccountNum() == 2L ? initialBalance : THOUSAND_HBAR)
													.withRecharging()
													.rechargeWindow(3)
													.key(DEFAULT_PAYER)
													.payingWith(DEFAULT_PAYER)
													.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
													.via("txn")
													.ensuringResolvedStatusIsntFromDuplicate();
											allRunFor(spec, cryptoCreateOp);
											var getRecordOp = getTxnRecord("txn")
													.assertingNothing()
													.saveTxnRecordToRegistry("savedTxnRcd")
													.hasRetryAnswerOnlyPrecheck(
															DUPLICATE_TRANSACTION,
															INVALID_TRANSACTION_ID)
													.logged();
											allRunFor(spec, getRecordOp);
											createdAuditablePayer = true;
										} catch (Throwable ignoreAgainAndRetry) {
											retryCount++;
										}
									}
									var status = spec.registry()
											.getTransactionRecord("savedTxnRcd")
											.getReceipt()
											.getStatus();
									Assert.assertEquals("Failed to create payer account!", SUCCESS, status);

									var payerAccountInfo = getAccountInfo("payerAccount")
													.hasRetryAnswerOnlyPrecheck(
															DUPLICATE_TRANSACTION,
															INVALID_ACCOUNT_ID)
													.saveToRegistry("payerAccountInfo")
													.logged();
									allRunFor(spec, payerAccountInfo);

									//TODO Should be modified in a different way to avoid setting a static variable of
									// other class
									SuiteRunner.setPayerId(String.format("0.0.%s", spec.registry()
											.getAccountInfo("payerAccountInfo")
											.getAccountID().getAccountNum()));
								}
						));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
