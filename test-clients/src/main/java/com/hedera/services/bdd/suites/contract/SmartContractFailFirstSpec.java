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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;

public class SmartContractFailFirstSpec extends HapiApiSuite  {
	private static final Logger log = LogManager.getLogger(SmartContractFailFirstSpec.class);

	private static final String A_TOKEN = "TokenA";
	private static final String B_TOKEN = "TokenB";
	private static final String FIRST_USER = "Client1";

	public static void main(String... args) {
		new SmartContractFailFirstSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				/* Stateful specs from TokenTransactSpecs */
				prechecksWork()
		});
	}

	private HapiApiSpec prechecksWork() {
		return defaultHapiSpec("PrechecksWork")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						cryptoCreate(FIRST_USER).balance(0L)
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY)
				).then(
						cryptoTransfer(
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER),
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.tokenTransfers.maxLen", "" + 2
						)).payingWith(ADDRESS_BOOK_CONTROL),
						cryptoTransfer(
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER),
								moving(1, B_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.tokenTransfers.maxLen", "" + 10
						)).payingWith(ADDRESS_BOOK_CONTROL),
						cryptoTransfer(
								movingHbar(1)
										.between(TOKEN_TREASURY, FIRST_USER),
								movingHbar(1)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						cryptoTransfer(
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER),
								moving(1, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS),
						cryptoTransfer(
								moving(0, A_TOKEN)
										.between(TOKEN_TREASURY, FIRST_USER)
						).hasPrecheck(INVALID_ACCOUNT_AMOUNTS),
						cryptoTransfer(
								moving(10, A_TOKEN)
										.from(TOKEN_TREASURY)
						).hasPrecheck(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN),
						cryptoTransfer(
								moving(10, A_TOKEN)
										.empty()
						).hasPrecheck(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
