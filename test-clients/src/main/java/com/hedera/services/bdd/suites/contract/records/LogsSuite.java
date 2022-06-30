package com.hedera.services.bdd.suites.contract.records;

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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;

public class LogsSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(LogsSuite.class);
	private static final String CONTRACT = "Logs";

	public static void main(String... args) {
		new LogsSuite().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				log0Works(),
				log1Works(),
				log2Works(),
				log3Works(),
				log4Works()
		);
	}

	private HapiApiSpec log0Works() {
		return defaultHapiSpec("log0Works")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "log0", 15).via("log0")
				).then(
						getTxnRecord("log0").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(
												inOrder(logWith().noTopics().longValue(15))
										).gasUsed(22_285))),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit"));
	}

	private HapiApiSpec log1Works() {
		return defaultHapiSpec("log1Works")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "log1", 15).via("log1")
				).then(
						getTxnRecord("log1").logged().hasPriority(recordWith().contractCallResult(resultWith()
								.logs(inOrder(logWith().noData().withTopicsInOrder(
										List.of(
												eventSignatureOf("Log1(uint256)"),
												parsedToByteString(15))))
								).gasUsed(22_583))),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit"));
	}

	private HapiApiSpec log2Works() {
		return defaultHapiSpec("log2Works")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "log2", 1, 2).via("log2")
				).then(
						getTxnRecord("log2").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(
														eventSignatureOf("Log2(uint256,uint256)"),
														parsedToByteString(1),
														parsedToByteString(2))))
										).gasUsed(23_112))),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit"));
	}

	private HapiApiSpec log3Works() {
		return defaultHapiSpec("log3Works")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "log3", 1, 2, 3).via("log3")
				).then(
						getTxnRecord("log3").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(
														eventSignatureOf("Log3(uint256,uint256,uint256)"),
														parsedToByteString(1),
														parsedToByteString(2),
														parsedToByteString(3))))
										).gasUsed(23_638))),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit"));
	}

	private HapiApiSpec log4Works() {
		return defaultHapiSpec("log4Works")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						contractCall(CONTRACT, "log4", 1, 2, 3, 4).via("log4")
				).then(
						getTxnRecord("log4").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().
												longValue(4)
												.withTopicsInOrder(
														List.of(
																eventSignatureOf("Log4(uint256,uint256,uint256," +
																		"uint256)"),
																parsedToByteString(1),
																parsedToByteString(2),
																parsedToByteString(3))))
										).gasUsed(24_294))),
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit"));
	}
}
