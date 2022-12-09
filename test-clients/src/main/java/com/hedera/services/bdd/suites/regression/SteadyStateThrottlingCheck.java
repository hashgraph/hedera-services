/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Stopwatch;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class SteadyStateThrottlingCheck extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(SteadyStateThrottlingCheck.class);

    private static final String TOKENS_NFTS_MINT_THROTTLE_SCALE_FACTOR =
            "tokens.nfts.mintThrottleScaleFactor";
    private static final String DEFAULT_NFT_SCALING =
            HapiSpecSetup.getDefaultNodeProps().get(TOKENS_NFTS_MINT_THROTTLE_SCALE_FACTOR);

    private static final int REGRESSION_NETWORK_SIZE = 4;
    private static final int PREVIEWNET_NETWORK_SIZE = 7;

    private static final double THROUGHPUT_LIMITS_XFER_NETWORK_TPS = 100.0;
    private static final double THROUGHPUT_LIMITS_FUNGIBLE_MINT_NETWORK_TPS = 30.0;
    private static final double PREVIEWNET_THROUGHPUT_LIMITS_NON_FUNGIBLE_MINT_NETWORK_TPS = 50.0;

    private static final double PRIORITY_RESERVATIONS_CONTRACT_CALL_NETWORK_TPS = 2.0;
    private static final double CREATION_LIMITS_CRYPTO_CREATE_NETWORK_TPS = 1.0;
    private static final double FREE_QUERY_LIMITS_GET_BALANCE_NETWORK_QPS = 100.0;

    private static final int NETWORK_SIZE = REGRESSION_NETWORK_SIZE;

    private static final double EXPECTED_XFER_TPS =
            THROUGHPUT_LIMITS_XFER_NETWORK_TPS / NETWORK_SIZE;
    private static final double EXPECTED_FUNGIBLE_MINT_TPS =
            THROUGHPUT_LIMITS_FUNGIBLE_MINT_NETWORK_TPS / NETWORK_SIZE;

    @SuppressWarnings("java:S1068")
    private static final double EXPECTED_PREVIEWNET_NON_FUNGIBLE_MINT_TPS =
            PREVIEWNET_THROUGHPUT_LIMITS_NON_FUNGIBLE_MINT_NETWORK_TPS / PREVIEWNET_NETWORK_SIZE;

    private static final double EXPECTED_CONTRACT_CALL_TPS =
            PRIORITY_RESERVATIONS_CONTRACT_CALL_NETWORK_TPS / NETWORK_SIZE;
    private static final double EXPECTED_CRYPTO_CREATE_TPS =
            CREATION_LIMITS_CRYPTO_CREATE_NETWORK_TPS / NETWORK_SIZE;
    private static final double EXPECTED_GET_BALANCE_QPS =
            FREE_QUERY_LIMITS_GET_BALANCE_NETWORK_QPS / NETWORK_SIZE;
    private static final double TOLERATED_PERCENT_DEVIATION = 5;
    private static final String SUPPLY = "supply";
    private static final String TOKEN = "token";
    private static final String CIVILIAN = "civilian";

    private final AtomicLong duration = new AtomicLong(180);
    private final AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(500);

    public static void main(String... args) {
        new SteadyStateThrottlingCheck().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                setArtificialLimits(),
                checkTps("Xfers", EXPECTED_XFER_TPS, xferOps()),
                checkTps("FungibleMints", EXPECTED_FUNGIBLE_MINT_TPS, fungibleMintOps()),
                checkTps("ContractCalls", EXPECTED_CONTRACT_CALL_TPS, scCallOps()),
                checkTps("CryptoCreates", EXPECTED_CRYPTO_CREATE_TPS, cryptoCreateOps()),
                checkBalanceQps(1000, EXPECTED_GET_BALANCE_QPS),
                restoreDevLimits());
    }

    private HapiSpec setArtificialLimits() {
        var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits.json");

        return defaultHapiSpec("SetArtificialLimits")
                .given()
                .when()
                .then(
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(artificialLimits.toByteArray()));
    }

    private HapiSpec restoreDevLimits() {
        var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
        return defaultHapiSpec("RestoreDevLimits")
                .given()
                .when()
                .then(
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .contents(defaultThrottles.toByteArray()),
                        fileUpdate(APP_PROPERTIES)
                                .overridingProps(
                                        Map.of(
                                                TOKENS_NFTS_MINT_THROTTLE_SCALE_FACTOR,
                                                DEFAULT_NFT_SCALING))
                                .payingWith(ADDRESS_BOOK_CONTROL));
    }

    private HapiSpec checkTps(
            String txn, double expectedTps, Function<HapiSpec, OpProvider> provider) {
        return checkCustomNetworkTps(txn, expectedTps, provider, Collections.emptyMap());
    }

    /**
     * An example of how to use this in a spot check of previewnet NFT mint throttle is,
     *
     * <pre>{@code
     * checkCustomNetworkTps(
     *   "NonFungibleMints",
     *   EXPECTED_PREVIEWNET_NON_FUNGIBLE_MINT_TPS,
     *   nonFungibleMintOps(), Map.of(
     *     "nodes", "35.231.208.148",
     *     "default.payer.pemKeyLoc", "[SUPERUSER_PEM]",
     *     "default.payer.pemKeyPassphrase", "[SUPERUSER_PEM_PASSPHRASE]")));
     * }</pre>
     */
    @SuppressWarnings("java:S5960")
    private HapiSpec checkCustomNetworkTps(
            String txn,
            double expectedTps,
            Function<HapiSpec, OpProvider> provider,
            Map<String, String> custom) {
        final var name = "Throttles" + txn + "AsExpected";
        final var baseSpec =
                custom.isEmpty()
                        ? defaultHapiSpec(name)
                        : customHapiSpec(name).withProperties(custom);
        return baseSpec.given()
                .when(
                        runWithProvider(provider)
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    var actualTps = 1.0 * spec.finalAdhoc() / duration.get();
                                    var percentDeviation =
                                            Math.abs(actualTps / expectedTps - 1.0) * 100.0;
                                    opLog.info(
                                            "Total ops accepted in {} {} = {} ==> {}tps vs {}tps"
                                                    + " expected ({}% deviation)",
                                            duration.get(),
                                            unit.get(),
                                            spec.finalAdhoc(),
                                            String.format("%.3f", actualTps),
                                            String.format("%.3f", expectedTps),
                                            String.format("%.3f", percentDeviation));
                                    Assertions.assertEquals(
                                            0.0, percentDeviation, TOLERATED_PERCENT_DEVIATION);
                                }));
    }

    private HapiSpec checkBalanceQps(int burstSize, double expectedQps) {
        return defaultHapiSpec("CheckBalanceQps")
                .given(cryptoCreate("curious").payingWith(GENESIS))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    int numBusy = 0;
                                    int askedSoFar = 0;
                                    int secsToRun = (int) duration.get();
                                    var watch = Stopwatch.createStarted();
                                    int logScreen = 0;
                                    while (watch.elapsed(SECONDS) < secsToRun) {
                                        var subOps =
                                                IntStream.range(0, burstSize)
                                                        .mapToObj(
                                                                ignore ->
                                                                        getAccountBalance("0.0.2")
                                                                                .noLogging()
                                                                                .payingWith(
                                                                                        "curious")
                                                                                .hasAnswerOnlyPrecheckFrom(
                                                                                        BUSY, OK))
                                                        .toArray(HapiSpecOperation[]::new);
                                        var burst = inParallel(subOps);
                                        allRunFor(spec, burst);
                                        askedSoFar += burstSize;
                                        for (int i = 0; i < burstSize; i++) {
                                            var op = (HapiGetAccountBalance) subOps[i];
                                            if (op.getResponse()
                                                            .getCryptogetAccountBalance()
                                                            .getBalance()
                                                    == 0) {
                                                numBusy++;
                                            }
                                        }
                                        if (logScreen++ % 100 == 0) {
                                            opLog.info(
                                                    "{}/{} queries BUSY so far in {}ms",
                                                    numBusy,
                                                    askedSoFar,
                                                    watch.elapsed(TimeUnit.MILLISECONDS));
                                        }
                                    }
                                    var elapsedMs = watch.elapsed(TimeUnit.MILLISECONDS);
                                    var numAnswered = askedSoFar - numBusy;
                                    var actualQps = (1.0 * numAnswered) / elapsedMs * 1000.0;
                                    var percentDeviation =
                                            Math.abs(actualQps / expectedQps - 1.0) * 100.0;
                                    opLog.info(
                                            "Total ops accepted in {} {} = {} ==> {}qps vs {}qps"
                                                    + " expected ({}% deviation)",
                                            elapsedMs,
                                            "ms",
                                            numAnswered,
                                            String.format("%.3f", actualQps),
                                            String.format("%.3f", expectedQps),
                                            String.format("%.3f", percentDeviation));
                                    Assertions.assertEquals(
                                            0.0, percentDeviation, TOLERATED_PERCENT_DEVIATION);
                                }));
    }

    private Function<HapiSpec, OpProvider> xferOps() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                cryptoCreate(CIVILIAN)
                                        .payingWith(GENESIS)
                                        .balance(ONE_MILLION_HBARS)
                                        .withRecharging(),
                                cryptoCreate("nobody").payingWith(GENESIS).balance(0L));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var op =
                                cryptoTransfer(tinyBarsFromTo(CIVILIAN, "nobody", 1))
                                        .noLogging()
                                        .deferStatusResolution()
                                        .payingWith(CIVILIAN)
                                        .hasPrecheckFrom(OK, BUSY)
                                        /* In my local environment spec has been flaky with the first few
                                        operations here...doesn't seem to happen with other specs? */
                                        .hasKnownStatusFrom(OK, SUCCESS);
                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiSpec, OpProvider> cryptoCreateOps() {
        var i = new AtomicInteger(0);

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                cryptoCreate(CIVILIAN)
                                        .payingWith(GENESIS)
                                        .balance(ONE_MILLION_HBARS)
                                        .withRecharging());
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var op =
                                cryptoCreate("w/e" + i.getAndIncrement())
                                        .noLogging()
                                        .deferStatusResolution()
                                        .payingWith(CIVILIAN)
                                        .hasPrecheckFrom(OK, BUSY);
                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiSpec, OpProvider> scCallOps() {
        final var contract = "Multipurpose";
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                uploadInitCode(contract),
                                contractCreate(contract).payingWith(GENESIS),
                                cryptoCreate(CIVILIAN)
                                        .balance(ONE_MILLION_HBARS)
                                        .payingWith(GENESIS));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var op =
                                contractCall("scMulti")
                                        .noLogging()
                                        .deferStatusResolution()
                                        .payingWith(CIVILIAN)
                                        .sending(ONE_HBAR)
                                        .hasKnownStatusFrom(SUCCESS)
                                        .hasPrecheckFrom(OK, BUSY);
                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiSpec, OpProvider> fungibleMintOps() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                newKeyNamed(SUPPLY),
                                cryptoCreate(TOKEN_TREASURY)
                                        .payingWith(GENESIS)
                                        .balance(ONE_MILLION_HBARS),
                                tokenCreate(TOKEN).treasury(TOKEN_TREASURY).supplyKey(SUPPLY));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var op =
                                mintToken(TOKEN, 1L)
                                        .fee(ONE_HBAR)
                                        .noLogging()
                                        .rememberingNothing()
                                        .deferStatusResolution()
                                        .signedBy(TOKEN_TREASURY, SUPPLY)
                                        .payingWith(TOKEN_TREASURY)
                                        .hasKnownStatusFrom(OK, SUCCESS)
                                        .hasPrecheckFrom(OK, BUSY);
                        return Optional.of(op);
                    }
                };
    }

    @SuppressWarnings("java:S1144")
    private Function<HapiSpec, OpProvider> nonFungibleMintOps() {
        final var metadata =
                "01234567890123456789012345678901234567890123456789"
                        + "01234567890123456789012345678901234567890123456789";
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                newKeyNamed(SUPPLY),
                                cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS),
                                tokenCreate(TOKEN)
                                        .initialSupply(0)
                                        .treasury(TOKEN_TREASURY)
                                        .supplyKey(SUPPLY));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var op =
                                mintToken(TOKEN, List.of(ByteString.copyFromUtf8(metadata)))
                                        .fee(ONE_HBAR)
                                        .noLogging()
                                        .rememberingNothing()
                                        .deferStatusResolution()
                                        .signedBy(TOKEN_TREASURY, SUPPLY)
                                        .payingWith(TOKEN_TREASURY)
                                        .hasKnownStatusFrom(OK, SUCCESS)
                                        .hasPrecheckFrom(OK, BUSY);
                        return Optional.of(op);
                    }
                };
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
