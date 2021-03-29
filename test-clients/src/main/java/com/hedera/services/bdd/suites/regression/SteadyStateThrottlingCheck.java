package com.hedera.services.bdd.suites.regression;

import com.google.common.base.Stopwatch;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SteadyStateThrottlingCheck extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SteadyStateThrottlingCheck.class);

	private static final int LOCAL_NETWORK_SIZE = 3;
	private static final int REGRESSION_NETWORK_SIZE = 4;

	private static final double THROUGHPUT_LIMITS_XFER_NETWORK_TPS = 100.0;
	private static final double TEST_THROUGHPUT_LIMITS_MINT_NETWORK_TPS = 30.0;
	private static final double PRIORITY_RESERVATIONS_CONTRACT_CALL_NETWORK_TPS = 2.0;
	private static final double CREATION_LIMITS_CRYPTO_CREATE_NETWORK_TPS = 1.0;
	private static final double FREE_QUERY_LIMITS_GET_BALANCE_NETWORK_QPS = 100.0;

	private static final int NETWORK_SIZE = LOCAL_NETWORK_SIZE;

	private static final double expectedXferTps = THROUGHPUT_LIMITS_XFER_NETWORK_TPS / NETWORK_SIZE;
	private static final double expectedMintTps = TEST_THROUGHPUT_LIMITS_MINT_NETWORK_TPS / NETWORK_SIZE;
	private static final double expectedContractCallTps = PRIORITY_RESERVATIONS_CONTRACT_CALL_NETWORK_TPS / NETWORK_SIZE;
	private static final double expectedCryptoCreateTps = CREATION_LIMITS_CRYPTO_CREATE_NETWORK_TPS / NETWORK_SIZE;
	private static final double expectedGetBalanceQps = FREE_QUERY_LIMITS_GET_BALANCE_NETWORK_QPS / NETWORK_SIZE;
	private static final double toleratedPercentDeviation = 2.0;

	private AtomicLong duration = new AtomicLong(240);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new SteadyStateThrottlingCheck().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						setArtificialLimits(),
						checkTps("Xfers", expectedXferTps, xferOps()),
						checkTps("Mints", expectedMintTps, mintOps()),
						checkTps("ContractCalls", expectedContractCallTps, scCallOps()),
						checkTps("CryptoCreates", expectedCryptoCreateTps, cryptoCreateOps()),
						checkBalanceQps(1000, expectedGetBalanceQps),
						restoreDevLimits(),
				}
		);
	}
	private HapiApiSpec setArtificialLimits() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits.json");
		return defaultHapiSpec("SetArtificialLimits")
				.given().when().then(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray())
				);
	}

	private HapiApiSpec restoreDevLimits() {
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		return defaultHapiSpec("RestoreDevLimits")
				.given().when().then(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray())
				);
	}

	private HapiApiSpec checkTps(String txn, double expectedTps, Function<HapiApiSpec, OpProvider> provider) {
		return defaultHapiSpec("Throttles" + txn + "AsExpected")
				.given().when(
						runWithProvider(provider)
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						withOpContext((spec, opLog) -> {
							var actualTps = 1.0 * spec.finalAdhoc() / duration.get();
							var percentDeviation = Math.abs(actualTps / expectedTps - 1.0) * 100.0;
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps vs {}tps expected ({}% deviation)",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", actualTps),
									String.format("%.3f", expectedTps),
									String.format("%.3f", percentDeviation));
							Assert.assertEquals(0.0, percentDeviation, toleratedPercentDeviation);
						})
				);
	}

	private HapiApiSpec checkBalanceQps(int burstSize, double expectedQps) {
		return defaultHapiSpec("CheckBalanceQps").given(
				cryptoCreate("curious").payingWith(GENESIS)
		).when().then(
				withOpContext((spec, opLog) -> {
					int numBusy = 0;
					int askedSoFar = 0;
					int secsToRun = (int) duration.get();
					var watch = Stopwatch.createStarted();
					while (watch.elapsed(SECONDS) < secsToRun) {
						var subOps = IntStream.range(0, burstSize)
								.mapToObj(ignore -> getAccountBalance("0.0.2")
										.noLogging()
										.payingWith("curious")
										.hasAnswerOnlyPrecheckFrom(BUSY, OK))
								.toArray(HapiSpecOperation[]::new);
						var burst = inParallel(subOps);
						allRunFor(spec, burst);
						askedSoFar += burstSize;
						for (int i = 0; i < burstSize; i++) {
							var op = (HapiGetAccountBalance) subOps[i];
							if (op.getResponse().getCryptogetAccountBalance().getBalance() == 0) {
								numBusy++;
							}
						}
						opLog.info("{}/{} queries BUSY so far in {}ms",
								numBusy,
								askedSoFar,
								watch.elapsed(TimeUnit.MILLISECONDS));
					}
					var elapsedMs = watch.elapsed(TimeUnit.MILLISECONDS);
					var numAnswered = askedSoFar - numBusy;
					var actualQps = (1.0 * numAnswered) / elapsedMs * 1000.0;
					var percentDeviation = Math.abs(actualQps / expectedQps - 1.0) * 100.0;
					opLog.info("Total ops accepted in {} {} = {} ==> {}qps vs {}qps expected ({}% deviation)",
							elapsedMs,
							"ms",
							numAnswered,
							String.format("%.3f", actualQps),
							String.format("%.3f", expectedQps),
							String.format("%.3f", percentDeviation));
					Assert.assertEquals(0.0, percentDeviation, toleratedPercentDeviation);
				})
		);
	}

	private Function<HapiApiSpec, OpProvider> xferOps() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian")
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS)
								.withRecharging(),
						cryptoCreate("nobody")
								.payingWith(GENESIS)
								.balance(0L)
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = cryptoTransfer(tinyBarsFromTo("civilian", "nobody", 1))
						.noLogging()
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> cryptoCreateOps() {
		var i = new AtomicInteger(0);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian")
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS)
								.withRecharging()
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = cryptoCreate("w/e" + i.getAndIncrement())
						.noLogging()
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}


	private Function<HapiApiSpec, OpProvider> scCallOps() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						fileCreate("bytecode")
								.path(ContractResources.MULTIPURPOSE_BYTECODE_PATH)
								.payingWith(GENESIS),
						contractCreate("scMulti")
								.bytecode("bytecode")
								.payingWith(GENESIS),
						cryptoCreate("civilian")
								.balance(ONE_MILLION_HBARS)
								.payingWith(GENESIS)
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = contractCall("scMulti")
						.noLogging()
						.deferStatusResolution()
						.payingWith("civilian")
						.sending(ONE_HBAR)
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> mintOps() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						newKeyNamed("supply"),
						cryptoCreate(TOKEN_TREASURY)
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS),
						tokenCreate("token")
								.treasury(TOKEN_TREASURY)
								.supplyKey("supply")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = mintToken("token", 1L)
						.noLogging()
						.deferStatusResolution()
						.signedBy(TOKEN_TREASURY, "supply")
						.payingWith(TOKEN_TREASURY)
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
