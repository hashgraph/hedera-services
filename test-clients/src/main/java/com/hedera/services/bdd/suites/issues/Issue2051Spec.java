package com.hedera.services.bdd.suites.issues;

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
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;

import java.util.List;

public class Issue2051Spec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue2051Spec.class);

	public static void main(String... args) {
		new Issue2051Spec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
						transferAccountCannotBeDeletedForContractTarget(),
						transferAccountCannotBeDeleted(),
						tbdCanPayForItsOwnDeletion(),
				}
		);
	}

	private HapiApiSpec tbdCanPayForItsOwnDeletion() {
		return defaultHapiSpec("TbdCanPayForItsOwnDeletion")
				.given(
						cryptoCreate("tbd"),
						cryptoCreate("transfer")
				).when( ).then(
						cryptoDelete("tbd")
								.via("selfFinanced")
								.payingWith("tbd")
								.transfer("transfer"),
						getTxnRecord("selfFinanced").logged()
				);
	}

	private HapiApiSpec transferAccountCannotBeDeleted() {
		return defaultHapiSpec("TransferAccountCannotBeDeleted")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("transfer"),
						cryptoCreate("tbd")
				).when(
						cryptoDelete("transfer")
				).then(
						balanceSnapshot("snapshot", "payer"),
						cryptoDelete("tbd")
								.via("deleteTxn")
								.payingWith("payer")
								.transfer("transfer")
								.hasKnownStatus(ACCOUNT_DELETED),
						getTxnRecord("deleteTxn").logged(),
						getAccountBalance("payer")
								.hasTinyBars(
										approxChangeFromSnapshot("snapshot", -9295610, 1000))
				);
	}

	private HapiApiSpec transferAccountCannotBeDeletedForContractTarget() {
		return defaultHapiSpec("TransferAccountCannotBeDeletedForContractTarget")
				.given(
						cryptoCreate("transfer"),
						contractCreate("tbd"),
						contractCreate("transferContract")
				).when(
						cryptoDelete("transfer"),
						contractDelete("transferContract")
				).then(
						balanceSnapshot("snapshot", GENESIS),
						contractDelete("tbd")
								.via("deleteTxn")
								.transferAccount("transfer")
								.hasKnownStatus(ACCOUNT_DELETED),
						contractDelete("tbd")
								.via("deleteTxn")
								.transferContract("transferContract")
								.hasKnownStatus(CONTRACT_DELETED),
						getTxnRecord("deleteTxn").logged(),
						getAccountBalance(GENESIS)
								.hasTinyBars(
										approxChangeFromSnapshot("snapshot", -18985232, 1000))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

