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
package com.hedera.services.bdd.suites.perf.token;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenRelStatusChanges extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenRelStatusChanges.class);

    private AtomicLong duration = new AtomicLong(5);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

    public static void main(String... args) {
        new TokenRelStatusChanges().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    runTokenRelStatusChanges(),
                });
    }

    private HapiSpec runTokenRelStatusChanges() {
        return HapiSpec.defaultHapiSpec("RunTokenRelStatusChanges")
                .given(stdMgmtOf(duration, unit, maxOpsPerSec))
                .when()
                .then(
                        runWithProvider(statusChangesFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> statusChangesFactory() {
        var nowAssociating = new AtomicBoolean(Boolean.FALSE);
        var relatableTokens = new AtomicInteger();
        var relatableAccounts = new AtomicInteger();
        List<String> tokens = new ArrayList<>();
        List<String> accounts = new ArrayList<>();
        var nextToken = new AtomicInteger(-1);
        var nextAccount = new AtomicInteger(-1);

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        var ciProps = spec.setup().ciPropertiesMap();
                        relatableTokens.set(ciProps.getInteger("numTokens"));
                        relatableAccounts.set(ciProps.getInteger("numAccounts"));

                        List<HapiSpecOperation> initializers = new ArrayList<>();
                        initializers.add(
                                tokenOpsEnablement().hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS));
                        IntStream.range(0, relatableTokens.get())
                                .mapToObj(i -> "token" + i)
                                .forEach(tokens::add);
                        initializers.add(
                                inParallel(
                                        tokens.stream()
                                                .map(
                                                        token ->
                                                                tokenCreate(token)
                                                                        .hasRetryPrecheckFrom(
                                                                                NOISY_RETRY_PRECHECKS))
                                                .toArray(HapiSpecOperation[]::new)));
                        IntStream.range(0, relatableAccounts.get())
                                .mapToObj(i -> "account" + i)
                                .forEach(accounts::add);
                        initializers.add(
                                inParallel(
                                        accounts.stream()
                                                .map(
                                                        account ->
                                                                cryptoCreate(account)
                                                                        .hasRetryPrecheckFrom(
                                                                                NOISY_RETRY_PRECHECKS))
                                                .toArray(HapiSpecOperation[]::new)));

                        return initializers;
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        HapiSpecOperation op;
                        final int numTokens = relatableTokens.get();
                        final int numAccounts = relatableAccounts.get();

                        int token = nextToken.get() % numTokens;
                        int account = nextAccount.incrementAndGet() % numAccounts;
                        if (account == 0) {
                            token = nextToken.incrementAndGet() % numTokens;
                            if (token == 0) {
                                var current = nowAssociating.get();
                                nowAssociating.compareAndSet(current, !current);
                            }
                        }

                        if (nowAssociating.get()) {
                            op =
                                    tokenAssociate(accounts.get(account), tokens.get(token))
                                            .fee(ONE_HUNDRED_HBARS)
                                            .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                            .hasKnownStatusFrom(
                                                    OK,
                                                    SUCCESS,
                                                    DUPLICATE_TRANSACTION,
                                                    TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
                                            .noLogging()
                                            .deferStatusResolution();
                        } else {
                            op =
                                    tokenDissociate(accounts.get(account), tokens.get(token))
                                            .fee(ONE_HUNDRED_HBARS)
                                            .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                            .hasKnownStatusFrom(
                                                    OK,
                                                    SUCCESS,
                                                    DUPLICATE_TRANSACTION,
                                                    TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
                                            .noLogging()
                                            .deferStatusResolution();
                        }

                        return Optional.of(op);
                    }
                };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
