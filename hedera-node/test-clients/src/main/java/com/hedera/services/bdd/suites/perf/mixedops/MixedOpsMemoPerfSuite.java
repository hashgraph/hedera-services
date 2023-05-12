/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.perf.mixedops;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MixedOpsMemoPerfSuite extends LoadTest {
    private static final Logger log = LogManager.getLogger(MixedOpsMemoPerfSuite.class);
    private final String INITIAL_MEMO = "InitialMemo";
    private final String ACCOUNT_MEMO = INITIAL_MEMO + " for Account Entity";
    private final String TOPIC_MEMO = INITIAL_MEMO + " for Topic Entity";
    private final String TOKEN_MEMO = INITIAL_MEMO + " for Token Entity";
    private final String TARGET_ACCOUNT = "accountForMemo";
    private final String TARGET_TOKEN = "tokenForMemo";
    private final String TARGET_TOPIC = "topicForMemo";

    private final ResponseCodeEnum[] permissiblePrechecks =
            new ResponseCodeEnum[] {OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED, UNKNOWN};

    public static void main(String... args) {
        parseArgs(args);

        MixedOpsMemoPerfSuite suite = new MixedOpsMemoPerfSuite();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runMixedMemoOps());
    }

    // perform cryptoCreate, cryptoUpdate, TokenCreate, TokenUpdate, FileCreate, FileUpdate txs with
    // entity memo set.
    protected HapiSpec runMixedMemoOps() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        final AtomicInteger createdSoFar = new AtomicInteger(0);
        Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
            cryptoCreate("testAccount" + createdSoFar.getAndIncrement())
                    .balance(1L)
                    .fee(100_000_000L)
                    .payingWith(GENESIS)
                    .entityMemo(new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8))
                    .noLogging()
                    .hasPrecheckFrom(permissiblePrechecks)
                    .deferStatusResolution(),
            getAccountInfo(TARGET_ACCOUNT + "Info")
                    .payingWith(GENESIS)
                    .has(accountWith().memo(ACCOUNT_MEMO))
                    .hasAnswerOnlyPrecheckFrom(permissiblePrechecks)
                    .hasCostAnswerPrecheckFrom(permissiblePrechecks)
                    .noLogging(),
            cryptoUpdate(TARGET_ACCOUNT)
                    .payingWith(GENESIS)
                    .entityMemo(new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8))
                    .noLogging()
                    .hasPrecheckFrom(permissiblePrechecks)
                    .deferStatusResolution(),
            tokenCreate("testToken" + createdSoFar.getAndIncrement())
                    .payingWith(GENESIS)
                    .entityMemo(new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8))
                    .noLogging()
                    .hasPrecheckFrom(permissiblePrechecks)
                    .deferStatusResolution(),
            getTokenInfo(TARGET_TOKEN + "Info")
                    .payingWith(GENESIS)
                    .hasEntityMemo(TOKEN_MEMO)
                    .hasAnswerOnlyPrecheckFrom(permissiblePrechecks)
                    .hasCostAnswerPrecheckFrom(permissiblePrechecks)
                    .noLogging(),
            tokenUpdate(TARGET_TOKEN)
                    .payingWith(GENESIS)
                    .entityMemo(new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8))
                    .noLogging()
                    .hasPrecheckFrom(permissiblePrechecks)
                    .deferStatusResolution(),
            createTopic("testTopic" + createdSoFar.getAndIncrement())
                    .topicMemo(new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8))
                    .payingWith(GENESIS)
                    .adminKeyName("adminKey")
                    .noLogging()
                    .hasPrecheckFrom(permissiblePrechecks)
                    .deferStatusResolution(),
            getTopicInfo(TARGET_TOPIC + "Info")
                    .payingWith(GENESIS)
                    .hasMemo(TOPIC_MEMO)
                    .hasAnswerOnlyPrecheckFrom(permissiblePrechecks)
                    .hasCostAnswerPrecheckFrom(permissiblePrechecks)
                    .noLogging(),
            updateTopic(TARGET_TOPIC)
                    .topicMemo(new String(TxnUtils.randomUtf8Bytes(memoLength.getAsInt()), StandardCharsets.UTF_8))
                    .payingWith(GENESIS)
                    .adminKey("adminKey")
                    .noLogging()
                    .hasPrecheckFrom(permissiblePrechecks)
                    .deferStatusResolution()
        };
        return defaultHapiSpec("RunMixedMemoOps")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()),
                        tokenOpsEnablement())
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of(
                                        "hapi.throttling.buckets.fastOpBucket.capacity",
                                        "1300000.0",
                                        "hapi.throttling.ops.consensusUpdateTopic.capacityRequired",
                                        "1.0",
                                        "hapi.throttling.ops.consensusGetTopicInfo.capacityRequired",
                                        "1.0",
                                        "hapi.throttling.ops.consensusSubmitMessage.capacityRequired",
                                        "1.0",
                                        "tokens.maxPerAccount",
                                        "10000000")),
                        sleepFor(5000),
                        newKeyNamed("adminKey"),
                        logIt(ignore -> settings.toString()),
                        cryptoCreate(TARGET_ACCOUNT)
                                .fee(100_000_000L)
                                .payingWith(GENESIS)
                                .entityMemo("Memo Length :" + settings.getMemoLength())
                                .logged(),
                        getAccountInfo(TARGET_ACCOUNT).logged(),
                        cryptoCreate(TARGET_ACCOUNT + "Info")
                                .fee(100_000_000L)
                                .payingWith(GENESIS)
                                .entityMemo(ACCOUNT_MEMO)
                                .logged(),
                        createTopic(TARGET_TOPIC)
                                .topicMemo(TOPIC_MEMO)
                                .adminKeyName("adminKey")
                                .payingWith(GENESIS)
                                .logged(),
                        createTopic(TARGET_TOPIC + "Info")
                                .payingWith(GENESIS)
                                .adminKeyName("adminKey")
                                .topicMemo(TOPIC_MEMO)
                                .logged(),
                        tokenCreate(TARGET_TOKEN)
                                .entityMemo(TOKEN_MEMO)
                                .payingWith(GENESIS)
                                .logged(),
                        tokenCreate(TARGET_TOKEN + "Info")
                                .entityMemo(TOKEN_MEMO)
                                .payingWith(GENESIS)
                                .logged())
                .then(defaultLoadTest(mixedOpsBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
