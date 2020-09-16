package com.hedera.services.bdd.suites.issues;

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

import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.bdd.spec.HapiApiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.*;

import java.util.List;

public class Issue1744Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue1744Suite.class);

	public static void main(String... args) {
		new Issue1744Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				keepsRecordOfPayerIBE()
		);
	}

	public static HapiApiSpec keepsRecordOfPayerIBE() {
		return defaultHapiSpec("KeepsRecordOfPayerIBE")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).via("referenceTxn"),
						UtilVerbs.withOpContext((spec, ctxLog) -> {
							HapiGetTxnRecord subOp = getTxnRecord("referenceTxn");
							allRunFor(spec, subOp);
							TransactionRecord record = subOp.getResponseRecord();
							long fee = record.getTransactionFee();
							spec.registry().saveAmount("fee", fee);
							spec.registry().saveAmount("balance", fee * 2);
						})
				).when(
						cryptoCreate("payer").balance(spec -> spec.registry().getAmount("balance"))
				).then(
						UtilVerbs.inParallel(
								cryptoTransfer(
										tinyBarsFromTo(
												"payer",
												FUNDING,
												spec -> spec.registry().getAmount("fee"))
								).payingWith("payer").via("txnA").hasAnyKnownStatus(),
								cryptoTransfer(
										tinyBarsFromTo(
												"payer",
												FUNDING,
												spec -> spec.registry().getAmount("fee"))
								).payingWith("payer").via("txnB").hasAnyKnownStatus()
						),
						getTxnRecord("txnA").logged(),
						getTxnRecord("txnB").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

