package com.hedera.services.bdd.suites.reconnect;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FIBONACCI_PLUS_CONSTRUCTOR_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FIBONACCI_PLUS_PATH;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_MINS_FOR_RECONNECT_TESTS;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_THREADS_FOR_RECONNECT_TESTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class CreateContractsBeforeReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreateContractsBeforeReconnect.class);
	private static final AtomicInteger contractNumber = new AtomicInteger(0);
	private static final int CONTRACT_CREATION_LIMIT = 1000;
	private static final int CONTRACT_CREATION_RECONNECT_TPS = 5;
	private static final int NUM_SLOTS = 20;
	private static final long GAS_TO_OFFER = 300_000L;
	private static final String fiboPlusBytecode = "fiboPlusBytecode";

	public static void main(String... args) {
		new CreateContractsBeforeReconnect().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runCreateContracts()
		);
	}

	private synchronized HapiSpecOperation generateContractCreateOperation() {
		final long contract = contractNumber.getAndIncrement();
		if (contract >= CONTRACT_CREATION_LIMIT) {
			return noOp();
		}

		return contractCreate("contract" + contract, FIBONACCI_PLUS_CONSTRUCTOR_ABI, NUM_SLOTS)
				.bytecode(fiboPlusBytecode)
				.noLogging()
				.gas(GAS_TO_OFFER)
				.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
				.deferStatusResolution();
	}

	private HapiApiSpec runCreateContracts() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings(
				CONTRACT_CREATION_RECONNECT_TPS,
				DEFAULT_MINS_FOR_RECONNECT_TESTS,
				DEFAULT_THREADS_FOR_RECONNECT_TESTS);

		Supplier<HapiSpecOperation[]> createBurst = () -> new HapiSpecOperation[] {
				generateContractCreateOperation()
		};

		return defaultHapiSpec("RunCreateContracts")
				.given(
						logIt(ignore -> settings.toString()),
						fileCreate(fiboPlusBytecode)
								.path(FIBONACCI_PLUS_PATH)
								.noLogging()
				).when()
				.then(
						defaultLoadTest(createBurst, settings)
				);
	}
}
