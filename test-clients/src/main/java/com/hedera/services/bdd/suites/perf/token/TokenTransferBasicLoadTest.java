/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenTransferBasicLoadTest extends LoadTest {

    private static final org.apache.logging.log4j.Logger LOG =
            LogManager.getLogger(TokenTransferBasicLoadTest.class);
    private static final int EXPECTED_MAX_OPS_PER_SEC = 5_000;
    private static final int MAX_PENDING_OPS_FOR_SETUP = 10_000;
    private static final int ESTIMATED_TOKEN_CREATION_RATE = 50;

    @SuppressWarnings("java:S2245")
    private static final Random r = new Random();

    public static final String ACCOUNT_FORMAT = "0.0.%d";

    public static void main(String... args) {
        parseArgs(args);
        TokenTransferBasicLoadTest suite = new TokenTransferBasicLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runTokenTransferBasicLoadTest());
    }

    private static String tokenRegistryName(int id) {
        return "TestToken" + id;
    }

    private Function<HapiSpec, OpProvider> tokenCreatesFactory(PerfTestLoadSettings settings) {
        int numTotalTokens = settings.getTotalTokens();
        int totalClients = settings.getTotalClients();
        int numActiveTokens = (totalClients >= 1) ? numTotalTokens / totalClients : numTotalTokens;
        AtomicInteger remaining = new AtomicInteger(numActiveTokens - 1);

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        int next;
                        if ((next = remaining.getAndDecrement()) < 0) {
                            return Optional.empty();
                        }
                        var payingTreasury =
                                String.format(
                                        ACCOUNT_FORMAT,
                                        settings.getTestTreasureStartAccount() + next);
                        var op =
                                tokenCreate(tokenRegistryName(next))
                                        .payingWith(DEFAULT_PAYER)
                                        .signedBy(DEFAULT_PAYER)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .initialSupply(100_000_000_000L)
                                        .treasury(payingTreasury)
                                        .hasRetryPrecheckFrom(
                                                BUSY,
                                                PLATFORM_TRANSACTION_NOT_CREATED,
                                                DUPLICATE_TRANSACTION,
                                                INSUFFICIENT_PAYER_BALANCE)
                                        .hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
                                        .hasKnownStatusFrom(
                                                SUCCESS,
                                                TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
                                                FAIL_INVALID)
                                        .suppressStats(true)
                                        .noLogging();
                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiSpec, OpProvider> activeTokenAssociatesFactory(
            PerfTestLoadSettings settings) {
        int numTotalTokens = settings.getTotalTokens();
        int numActiveTokenAccounts = settings.getTotalTestTokenAccounts();
        int totalClients = settings.getTotalClients();
        int numActiveTokens = (totalClients >= 1) ? numTotalTokens / totalClients : numTotalTokens;
        AtomicLong remainingAssociations =
                new AtomicLong(numActiveTokens * numActiveTokenAccounts - 1L);

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Total active token accounts {}, total test tokens {}, my portion of tokens {}",
                    numActiveTokenAccounts,
                    numTotalTokens,
                    numActiveTokens);
        }

        long startAccountId = settings.getTestTreasureStartAccount();
        /* We are useing first portion, 10K, 100K or any other number of total existing accounts to test
           token performance. Thus the total account number (currently 1M+) won't impact the algorithm
           here to build the associations of active testing accounts and newly created active tokens.
           Given n accounts, the association between the i-th token and the j-th account has id
                assocId = i * numActiveTokenAccounts + j
           Where:
            - i is in the range [0, numActiveTokens)
            - j is in the range [0, numActiveTokenAccounts)
        */

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        long nextAssocId;
                        if ((nextAssocId = remainingAssociations.getAndDecrement()) < 0) {
                            return Optional.empty();
                        }
                        int curToken = (int) nextAssocId / numActiveTokenAccounts;
                        long curAccount = nextAssocId % numActiveTokenAccounts;
                        var accountId = "0.0." + (startAccountId + curAccount);
                        var op =
                                tokenAssociate(accountId, tokenRegistryName(curToken))
                                        .payingWith(DEFAULT_PAYER)
                                        .signedBy(DEFAULT_PAYER)
                                        .hasRetryPrecheckFrom(
                                                BUSY,
                                                PLATFORM_TRANSACTION_NOT_CREATED,
                                                DUPLICATE_TRANSACTION,
                                                INSUFFICIENT_PAYER_BALANCE)
                                        .hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
                                        .hasKnownStatusFrom(
                                                SUCCESS,
                                                TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
                                                INVALID_TOKEN_ID,
                                                TRANSACTION_EXPIRED,
                                                FAIL_INVALID,
                                                OK)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .noLogging()
                                        .suppressStats(true)
                                        .deferStatusResolution();
                        return Optional.of(op);
                    }
                };
    }

    private HapiSpec runTokenTransferBasicLoadTest() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        Supplier<HapiSpecOperation[]> tokenTransferBurst =
                () -> new HapiSpecOperation[] {opSupplier(settings).get()};
        return defaultHapiSpec("TokenTransferBasicLoadTest")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())))
                .when(
                        sourcing(
                                () ->
                                        runWithProvider(tokenCreatesFactory(settings))
                                                .lasting(
                                                        () ->
                                                                settings.getTotalTokens()
                                                                                / ESTIMATED_TOKEN_CREATION_RATE
                                                                        + 10, // 10s as buffering
                                                        // time
                                                        () -> TimeUnit.SECONDS)
                                                .totalOpsToSumbit(
                                                        () ->
                                                                (int)
                                                                        Math.ceil(
                                                                                (double)
                                                                                                (settings
                                                                                                        .getTotalTokens())
                                                                                        / settings
                                                                                                .getTotalClients()))
                                                .maxOpsPerSec(
                                                        () ->
                                                                (EXPECTED_MAX_OPS_PER_SEC
                                                                        / settings
                                                                                .getTotalClients()))
                                                .maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),
                        sourcing(
                                () ->
                                        runWithProvider(activeTokenAssociatesFactory(settings))
                                                .lasting(
                                                        () ->
                                                                (settings.getTotalTokens()
                                                                                * settings
                                                                                        .getTotalTestTokenAccounts()
                                                                                / EXPECTED_MAX_OPS_PER_SEC)
                                                                        + 30, // 30s as buffering
                                                        // time
                                                        () -> TimeUnit.SECONDS)
                                                .totalOpsToSumbit(
                                                        () ->
                                                                (int)
                                                                                Math.ceil(
                                                                                        (double)
                                                                                                        (settings
                                                                                                                .getTotalTokens())
                                                                                                / settings
                                                                                                        .getTotalClients())
                                                                        * settings
                                                                                .getTotalTestTokenAccounts())
                                                .maxOpsPerSec(
                                                        () ->
                                                                (EXPECTED_MAX_OPS_PER_SEC
                                                                        / settings
                                                                                .getTotalClients()))
                                                .maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),
                        sleepFor(2000))
                .then(defaultLoadTest(tokenTransferBurst, settings));
    }

    private static Supplier<HapiSpecOperation> opSupplier(PerfTestLoadSettings settings) {
        int tokenNum = r.nextInt(settings.getTotalTokens() / settings.getTotalClients());
        int sender = r.nextInt(settings.getTotalTestTokenAccounts());
        int receiver = r.nextInt(settings.getTotalTestTokenAccounts());
        while (receiver == sender) {
            receiver = r.nextInt(settings.getTotalTestTokenAccounts());
        }
        String senderAcct =
                String.format(ACCOUNT_FORMAT, settings.getTestTreasureStartAccount() + sender);
        String receiverAcct =
                String.format(ACCOUNT_FORMAT, settings.getTestTreasureStartAccount() + receiver);

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Account 0.0.{} will send  1 token of testToken{} to account 0.0.{}",
                    senderAcct,
                    tokenNum,
                    receiverAcct);
        }

        var op =
                cryptoTransfer(
                                moving(1, tokenRegistryName(tokenNum))
                                        .between(senderAcct, receiverAcct))
                        .payingWith(senderAcct)
                        .signedBy(GENESIS)
                        .fee(ONE_HUNDRED_HBARS)
                        .noLogging()
                        .suppressStats(true)
                        .hasPrecheckFrom(
                                OK,
                                INSUFFICIENT_PAYER_BALANCE,
                                EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS,
                                DUPLICATE_TRANSACTION)
                        .hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED)
                        .hasKnownStatusFrom(
                                SUCCESS,
                                OK,
                                INSUFFICIENT_TOKEN_BALANCE,
                                TRANSACTION_EXPIRED,
                                INVALID_TOKEN_ID,
                                UNKNOWN,
                                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
                        .deferStatusResolution();
        return () -> op;
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
