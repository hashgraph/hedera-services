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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_ALREADY_SCHEDULED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenTransfersLoadProvider extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenTransfersLoadProvider.class);

    private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

    public static void main(String... args) {
        new TokenTransfersLoadProvider().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    runTokenTransfers(),
                });
    }

    private HapiSpec runTokenTransfers() {
        return HapiSpec.defaultHapiSpec("RunTokenTransfers")
                .given(
                        getAccountBalance(DEFAULT_PAYER).logged(),
                        stdMgmtOf(duration, unit, maxOpsPerSec),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of(
                                                "balances.exportPeriodSecs",
                                                "300",
                                                "balances.exportDir.path",
                                                "data/accountBalances/")))
                .when(
                        runWithProvider(tokenTransfersFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get))
                .then(
                        getAccountBalance(DEFAULT_PAYER).logged(),
                        // The freeze and long wait after freeze means to keep the server in
                        // MAINTENANCE state till test
                        // end to prevent it from making new export files that may cause account
                        // balances validator to
                        // be inconsistent. The freeze shouldn't cause normal perf test any issue.
                        freezeOnly()
                                .payingWith(GENESIS)
                                .startingIn(30)
                                .seconds()
                                .hasKnownStatusFrom(SUCCESS, UNKNOWN, FREEZE_ALREADY_SCHEDULED)
                                .hasAnyPrecheck(),
                        sleepFor(60_000));
    }

    private Function<HapiSpec, OpProvider> tokenTransfersFactory() {
        var firstDir = new AtomicBoolean(Boolean.TRUE);
        var balanceInit = new AtomicLong();
        var tokensPerTxn = new AtomicInteger();
        var sendingAccountsPerToken = new AtomicInteger();
        var receivingAccountsPerToken = new AtomicInteger();
        List<String> treasuries = new ArrayList<>();
        Map<String, List<String>> senders = new HashMap<>();
        Map<String, List<String>> receivers = new HashMap<>();

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        var ciProps = spec.setup().ciPropertiesMap();
                        balanceInit.set(ciProps.getLong("balanceInit"));
                        tokensPerTxn.set(ciProps.getInteger("tokensPerTxn"));
                        sendingAccountsPerToken.set(ciProps.getInteger("sendingAccountsPerToken"));
                        receivingAccountsPerToken.set(
                                ciProps.getInteger("receivingAccountsPerToken"));

                        var initialSupply =
                                (sendingAccountsPerToken.get() + receivingAccountsPerToken.get())
                                        * balanceInit.get();
                        List<HapiSpecOperation> initializers = new ArrayList<>();
                        for (int i = 0; i < tokensPerTxn.get(); i++) {
                            var token = "token" + i;
                            var treasury = "treasury" + i;
                            initializers.add(cryptoCreate(treasury));
                            initializers.add(
                                    tokenCreate(token)
                                            .treasury(treasury)
                                            .initialSupply(initialSupply));
                            treasuries.add(treasury);
                            for (int j = 0; j < sendingAccountsPerToken.get(); j++) {
                                var sender = token + "sender" + j;
                                senders.computeIfAbsent(token, ignore -> new ArrayList<>())
                                        .add(sender);
                                initializers.add(cryptoCreate(sender));
                                initializers.add(tokenAssociate(sender, token));
                                initializers.add(
                                        cryptoTransfer(
                                                moving(balanceInit.get(), token)
                                                        .between(treasury, sender)));
                            }
                            for (int j = 0; j < receivingAccountsPerToken.get(); j++) {
                                var receiver = token + "receiver" + j;
                                receivers
                                        .computeIfAbsent(token, ignore -> new ArrayList<>())
                                        .add(receiver);
                                initializers.add(cryptoCreate(receiver));
                                initializers.add(tokenAssociate(receiver, token));
                                initializers.add(
                                        cryptoTransfer(
                                                moving(balanceInit.get(), token)
                                                        .between(treasury, receiver)));
                            }
                        }

                        for (HapiSpecOperation op : initializers) {
                            if (op instanceof HapiTxnOp) {
                                ((HapiTxnOp) op).hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS);
                            }
                        }

                        return initializers;
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        HapiSpecOperation op;
                        var numTokens = tokensPerTxn.get();
                        var numSenders = sendingAccountsPerToken.get();
                        var numReceivers = receivingAccountsPerToken.get();
                        if (firstDir.get()) {
                            var xfers = new TokenMovement[numTokens * numSenders];
                            for (int i = 0; i < numTokens; i++) {
                                var token = "token" + i;
                                for (int j = 0; j < numSenders; j++) {
                                    var receivers = new String[numReceivers];
                                    for (int k = 0; k < numReceivers; k++) {
                                        receivers[k] = token + "receiver" + k;
                                    }
                                    xfers[i * numSenders + j] =
                                            moving(numReceivers, token)
                                                    .distributing(token + "sender" + j, receivers);
                                }
                            }
                            op =
                                    cryptoTransfer(xfers)
                                            .hasKnownStatusFrom(
                                                    OK,
                                                    DUPLICATE_TRANSACTION,
                                                    SUCCESS,
                                                    UNKNOWN,
                                                    INSUFFICIENT_PAYER_BALANCE)
                                            .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                            .hasPrecheckFrom(OK, PLATFORM_NOT_ACTIVE)
                                            .noLogging()
                                            .deferStatusResolution();
                            firstDir.set(Boolean.FALSE);
                        } else {
                            var xfers = new TokenMovement[numTokens * numReceivers];
                            for (int i = 0; i < numTokens; i++) {
                                var token = "token" + i;
                                for (int j = 0; j < numReceivers; j++) {
                                    var senders = new String[numSenders];
                                    for (int k = 0; k < numSenders; k++) {
                                        senders[k] = token + "sender" + k;
                                    }
                                    xfers[i * numReceivers + j] =
                                            moving(numSenders, token)
                                                    .distributing(token + "receiver" + j, senders);
                                }
                            }
                            op =
                                    cryptoTransfer(xfers)
                                            .hasKnownStatusFrom(
                                                    OK,
                                                    DUPLICATE_TRANSACTION,
                                                    SUCCESS,
                                                    UNKNOWN,
                                                    INSUFFICIENT_PAYER_BALANCE)
                                            .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                            .hasPrecheckFrom(OK, PLATFORM_NOT_ACTIVE)
                                            .noLogging()
                                            .deferStatusResolution();
                            firstDir.set(Boolean.TRUE);
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
