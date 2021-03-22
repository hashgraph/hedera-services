package com.hedera.services.yahcli.suites;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.isIdLiteral;

public class BalanceSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(BalanceSuite.class);

	private final Map<String, String> specConfig;
	private final List<String> accounts;

	public BalanceSuite(final Map<String, String> specConfig, final String[] accounts) {
		this.specConfig = specConfig;
		this.accounts = rationalized(accounts);
	}

	private List<String> rationalized(final String[] accounts) {
		return Arrays.stream(accounts)
				.map(a -> getAccount(a))
				.collect(Collectors.toList());
	}

	private String getAccount(final String account) {
		if(isIdLiteral(account)) {
			return account;
		} else {
			try {
				int number = Integer.parseInt(account);
				return "0.0." + number;
			} catch (NumberFormatException ignore) {
				throw  new IllegalArgumentException("Named accounts not yet supported!");
			}
		}
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		List<HapiApiSpec> specToRun = new ArrayList<>();
		accounts.forEach(s -> specToRun.add(getBalance(s)));
		return specToRun;
	}

	private HapiApiSpec getBalance(String accountID) {
		return HapiApiSpec.customHapiSpec(("getBalance"))
				.withProperties(specConfig)
				.given().when()
				.then(
						getAccountBalance(accountID)
								.noLogging()
								.yahcliLogging()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
