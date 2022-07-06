/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.perf;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.exportAccountBalances;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.RegistryNotFound;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountBalancesClientSaveLoadTest extends LoadTest {
    private static final Logger log = LogManager.getLogger(AccountBalancesClientSaveLoadTest.class);
    static final int MAX_PENDING_OPS_FOR_SETUP = 10_000;
    static final int TOTAL_ACCOUNT = 200_000;
    static final int ESTIMATED_TOKEN_CREATION_RATE = 50;
    static final int ESTIMATED_CRYPTO_CREATION_RATE = 500;
    static final long MIN_ACCOUNT_BALANCE = 1_000_000_000L;
    static final int MIN_TOKEN_SUPPLY = 1000;
    static final int MAX_TOKEN_SUPPLY = 1_000_000;
    static final int MAX_TOKEN_TRANSFER = 100;
    static final int SECOND = 1000;
    private static final Random r = new Random();

    private static final int TOTAL_TEST_TOKENS = 500;
    private static final String ACCT_NAME_PREFIX = "acct-";
    private static final String TOKEN_NAME_PREFIX = "token-";

    private static int normal_acct_created = 0;

    private static final String ACCOUNT_FILE_EXPORT_DIR =
            "src/main/resource/accountBalancesClient.pb";

    private int totalTestTokens = TOTAL_TEST_TOKENS;
    private int totalAccounts = TOTAL_ACCOUNT;
    private int totalTreasureAccounts = totalTestTokens;

    List<Pair<Integer, Integer>> tokenAcctAssociations = new ArrayList<>();

    private final ResponseCodeEnum[] permissiblePrechecks =
            new ResponseCodeEnum[] {
                OK,
                BUSY,
                DUPLICATE_TRANSACTION,
                PLATFORM_TRANSACTION_NOT_CREATED,
                ACCOUNT_ID_DOES_NOT_EXIST,
                ACCOUNT_DELETED
            };

    public static void main(String... args) {
        parseArgs(args);
        AccountBalancesClientSaveLoadTest suite = new AccountBalancesClientSaveLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(new HapiApiSpec[] {runAccountBalancesClientSaveLoadTest()});
    }

    private HapiApiSpec runAccountBalancesClientSaveLoadTest() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        var throttlesForJRS =
                protoDefsFromResource("testSystemFiles/throttles-for-acct-balances-tests.json");
        return defaultHapiSpec("AccountBalancesClientSaveLoadTest")
                .given(
                        tokenOpsEnablement(),
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()),
                        sourcing(
                                () ->
                                        fileUpdate(APP_PROPERTIES)
                                                .payingWith(GENESIS)
                                                .overridingProps(
                                                        Map.of(
                                                                "balances.exportPeriodSecs",
                                                                String.format(
                                                                        "%d",
                                                                        settings
                                                                                .getBalancesExportPeriodSecs())))
                                                .erasingProps(
                                                        Set.of(
                                                                "accountBalanceExportPeriodMinutes"))),
                        fileUpdate(THROTTLE_DEFS)
                                .payingWith(GENESIS)
                                .contents(throttlesForJRS.toByteArray()))
                .when(
                        sourcing(
                                () ->
                                        runWithProvider(accountsCreate(settings))
                                                .lasting(
                                                        () ->
                                                                totalAccounts
                                                                                / ESTIMATED_CRYPTO_CREATION_RATE
                                                                        + 10,
                                                        () -> TimeUnit.SECONDS)
                                                .totalOpsToSumbit(
                                                        () -> (int) Math.ceil(totalAccounts))
                                                .maxOpsPerSec(() -> settings.getTps())
                                                .maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),
                        sleepFor(10 * SECOND),
                        sourcing(
                                () ->
                                        runWithProvider(tokensCreate(settings))
                                                .lasting(
                                                        () ->
                                                                totalTestTokens
                                                                                / ESTIMATED_TOKEN_CREATION_RATE
                                                                        + 10,
                                                        () -> TimeUnit.SECONDS)
                                                .totalOpsToSumbit(
                                                        () -> (int) Math.ceil(totalTestTokens))
                                                .maxOpsPerSec(() -> settings.getTps())
                                                .maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),
                        sleepFor(10 * SECOND),
                        sourcing(
                                () ->
                                        runWithProvider(randomTokenAssociate(settings))
                                                .lasting(
                                                        () ->
                                                                settings
                                                                        .getDurationCreateTokenAssociation(),
                                                        () -> TimeUnit.SECONDS)
                                                .maxOpsPerSec(() -> settings.getTps())
                                                .maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)),
                        sleepFor(15 * SECOND),
                        sourcing(
                                () ->
                                        runWithProvider(randomTransfer(settings))
                                                .lasting(
                                                        () -> settings.getDurationTokenTransfer(),
                                                        () -> TimeUnit.SECONDS)
                                                .maxOpsPerSec(() -> settings.getTps())
                                                .maxPendingOps(() -> MAX_PENDING_OPS_FOR_SETUP)))
                .then(
                        sleepFor(10 * SECOND),
                        withOpContext(
                                (spec, log) -> {
                                    if (settings.getBooleanProperty(
                                            "clientToExportBalances", false)) {
                                        log.info(
                                                "Now get all {} accounts created and save them",
                                                totalAccounts);
                                        AccountID acctID;
                                        String lastGoodAcct = null;
                                        int acctProcessed = 0;
                                        int batchSize = 10000;
                                        while (acctProcessed <= totalAccounts) {
                                            List<HapiSpecOperation> ops = new ArrayList<>();
                                            String acctName = null;
                                            for (int i = acctProcessed + 1;
                                                    i <= acctProcessed + batchSize
                                                            && i <= totalAccounts;
                                                    i++) {
                                                acctName = ACCT_NAME_PREFIX + i;
                                                // Make sure the named account was created before
                                                // query its balances.
                                                try {
                                                    acctID = spec.registry().getAccountID(acctName);
                                                } catch (RegistryNotFound e) {
                                                    log.info(
                                                            acctName
                                                                    + " was not created"
                                                                    + " successfully.");
                                                    continue;
                                                }
                                                var op =
                                                        getAccountBalance(
                                                                        HapiPropertySource
                                                                                .asAccountString(
                                                                                        acctID))
                                                                .hasAnswerOnlyPrecheckFrom(
                                                                        permissiblePrechecks)
                                                                .persists(true)
                                                                .noLogging();
                                                ops.add(op);
                                                lastGoodAcct = acctName;
                                            }
                                            ops.add(getAccountInfo(lastGoodAcct).fee(ONE_HBAR));
                                            allRunFor(spec, ops);
                                            acctProcessed += batchSize;
                                        }
                                    } else { // debug
                                        log.info("Don't dump account balances from client side.");
                                    }
                                }),
                        sleepFor(10 * SECOND),
                        exportAccountBalances(() -> ACCOUNT_FILE_EXPORT_DIR),
                        freezeOnly().payingWith(GENESIS).startingIn(10).seconds());
    }

    private Function<HapiApiSpec, OpProvider> accountsCreate(PerfTestLoadSettings settings) {
        totalTestTokens =
                settings.getTotalTokens() > 10 ? settings.getTotalTokens() : TOTAL_TEST_TOKENS;
        totalTreasureAccounts = totalTestTokens;
        totalAccounts =
                settings.getTotalAccounts() > 100 ? settings.getTotalAccounts() : TOTAL_ACCOUNT;

        log.info("Total accounts: {}", totalAccounts);
        log.info("Total tokens: {}", totalTestTokens);

        AtomicInteger moreToCreate = new AtomicInteger(totalAccounts);

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        log.info("Now running accountsCreate initializer");
                        return Collections.emptyList();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        int next;
                        next = moreToCreate.getAndDecrement();
                        if (next <= 0) {
                            return Optional.empty();
                        }

                        var op =
                                cryptoCreate(String.format("%s%d", ACCT_NAME_PREFIX, next))
                                        .balance(
                                                (long)
                                                        (r.nextInt((int) ONE_HBAR)
                                                                + MIN_ACCOUNT_BALANCE))
                                        .key(GENESIS)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .withRecharging()
                                        .rechargeWindow(30)
                                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                        .hasPrecheckFrom(
                                                DUPLICATE_TRANSACTION, OK, INSUFFICIENT_TX_FEE)
                                        .hasKnownStatusFrom(SUCCESS, INVALID_SIGNATURE)
                                        .noLogging()
                                        .deferStatusResolution();

                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiApiSpec, OpProvider> tokensCreate(PerfTestLoadSettings settings) {
        AtomicInteger createdSofar = new AtomicInteger(0);

        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        log.info("Now running tokensCreate initializer");
                        return Collections.emptyList();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        int next;
                        next = createdSofar.getAndIncrement();
                        if (next >= totalTestTokens) {
                            return Optional.empty();
                        }
                        var payingTreasury = String.format(ACCT_NAME_PREFIX + next);
                        var op =
                                tokenCreate(TOKEN_NAME_PREFIX + next)
                                        .signedBy(GENESIS)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .initialSupply(
                                                MIN_TOKEN_SUPPLY + r.nextInt(MAX_TOKEN_SUPPLY))
                                        .treasury(payingTreasury)
                                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                        .hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
                                        .hasKnownStatusFrom(
                                                SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
                                        .suppressStats(true)
                                        .noLogging();
                        if (next > 0) {
                            op.deferStatusResolution();
                        }
                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiApiSpec, OpProvider> randomTokenAssociate(PerfTestLoadSettings settings) {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        log.info("Now running tokenAssociatesFactory initializer");
                        return Collections.emptyList();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        int tokenNum = r.nextInt(totalTestTokens - 1);
                        int acctNum = r.nextInt(totalAccounts - 1);
                        String tokenName = TOKEN_NAME_PREFIX + tokenNum;
                        String accountName = ACCT_NAME_PREFIX + acctNum;
                        tokenAcctAssociations.add(Pair.of(tokenNum, acctNum));

                        var op =
                                tokenAssociate(accountName, tokenName)
                                        .signedBy(GENESIS)
                                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                        .hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
                                        .hasKnownStatusFrom(
                                                SUCCESS,
                                                TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
                                                INVALID_SIGNATURE,
                                                TRANSACTION_EXPIRED)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .noLogging()
                                        .suppressStats(true)
                                        .deferStatusResolution();

                        return Optional.of(op);
                    }
                };
    }

    private Function<HapiApiSpec, OpProvider> randomTransfer(PerfTestLoadSettings settings) {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        log.info("Now running tokenTransferFactory initializer");
                        return Collections.emptyList();
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        int nextTransfer = r.nextInt(tokenAcctAssociations.size());
                        int tokenAndSenderOrd = tokenAcctAssociations.get(nextTransfer).getLeft();
                        int receiverOrd = tokenAcctAssociations.get(nextTransfer).getRight();
                        String tokenName = TOKEN_NAME_PREFIX + tokenAndSenderOrd;
                        String senderAcctName = ACCT_NAME_PREFIX + tokenAndSenderOrd;
                        String receivedAcctName = ACCT_NAME_PREFIX + receiverOrd;
                        var op =
                                cryptoTransfer(
                                                moving(r.nextInt(MAX_TOKEN_TRANSFER) + 1, tokenName)
                                                        .between(senderAcctName, receivedAcctName))
                                        .hasKnownStatusFrom(
                                                OK,
                                                SUCCESS,
                                                DUPLICATE_TRANSACTION,
                                                INVALID_SIGNATURE,
                                                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
                                                INSUFFICIENT_TOKEN_BALANCE)
                                        .hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
                                        .hasPrecheckFrom(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, OK)
                                        .noLogging()
                                        .signedBy(GENESIS)
                                        .suppressStats(true)
                                        .fee(ONE_HUNDRED_HBARS)
                                        .deferStatusResolution();

                        return Optional.of(op);
                    }
                };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
