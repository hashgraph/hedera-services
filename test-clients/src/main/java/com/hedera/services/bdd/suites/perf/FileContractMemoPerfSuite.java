package com.hedera.services.bdd.suites.perf;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class FileContractMemoPerfSuite  extends LoadTest {
	private static final Logger log = LogManager.getLogger(FileContractMemoPerfSuite.class);

	private final ResponseCodeEnum[] permissiblePrechecks = new ResponseCodeEnum[] {
			OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED
	};

	private final String INITIAL_MEMO = "InitialMemo";
	private final String FILE_MEMO = INITIAL_MEMO + " for File Entity";
	private final String CONTRACT_MEMO = INITIAL_MEMO + " for Contract Entity";
	private final String TARGET_FILE = "fileForMemo";
	private final String CONTRACT = "contractForMemo";

	public static void main(String... args) {
		parseArgs(args);

		FileContractMemoPerfSuite suite = new FileContractMemoPerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return	List.of(
				RunMixedFileContractMemoOps()
		);
	}

	// perform cryptoCreate, cryptoUpdate, TokenCreate, TokenUpdate, FileCreate, FileUpdate txs with entity memo set.
	protected HapiApiSpec RunMixedFileContractMemoOps() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger createdSoFar = new AtomicInteger(0);
		Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
				fileCreate("testFile" + createdSoFar.getAndIncrement())
						.payingWith(GENESIS)
						.entityMemo(
								new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8)
						)
						.noLogging()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				getFileInfo(TARGET_FILE+"Info")
					.hasMemo(FILE_MEMO),
				fileUpdate(TARGET_FILE)
						.payingWith(GENESIS)
						.entityMemo(
								new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8)
						)
						.noLogging()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				contractCreate("testContract" + createdSoFar.getAndIncrement())
						.payingWith(GENESIS)
						.bytecode(TARGET_FILE)
						.entityMemo(
								new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8)
						)
						.noLogging()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution(),
				getContractInfo(CONTRACT +"Info")
						.hasExpectedInfo(),
				contractUpdate(CONTRACT)
						.payingWith(GENESIS)
						.newMemo(
						new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8)
						)
						.noLogging()
						.hasPrecheckFrom(
								OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
						.deferStatusResolution()
		};
		return defaultHapiSpec("RunMixedFileContractMemoOps")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				)
				.when(
						fileCreate(TARGET_FILE)
								.payingWith(GENESIS)
								.path(HapiSpecSetup.getDefaultInstance().defaultContractPath())
								.entityMemo(FILE_MEMO)
								.logged(),
						fileCreate(TARGET_FILE+"Info")
								.payingWith(GENESIS)
								.entityMemo(FILE_MEMO)
								.logged(),
						contractCreate(CONTRACT)
								.payingWith(GENESIS)
								.bytecode(TARGET_FILE)
								.entityMemo(CONTRACT_MEMO)
								.logged(),
						contractCreate(CONTRACT +"Info")
								.payingWith(GENESIS)
								.bytecode(TARGET_FILE)
								.entityMemo(CONTRACT_MEMO)
								.logged()
				)
				.then(
						defaultLoadTest(mixedOpsBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
