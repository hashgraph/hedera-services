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

package com.hedera.services.bdd.suites.perf.topic;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SubmitMessageLoadTest extends LoadTest {

    private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(SubmitMessageLoadTest.class);
    private static String topicID = null;
    private static int messageSize = 256;
    private static String pemFile = null;
    private static final long TEST_ACCOUNT_STARTS_FROM = 1001L;

    private static Random r = new Random();

    public static void main(String... args) {
        int usedArgs = parseArgs(args);

        // Usage
        //
        // 1) create new topic with auto generated key, an topicSubmitKey.pem will ge exported for
        // later use
        // args: [size]
        //
        // 2) create new topic with pre-exist PEM file
        // args: [size] [pemFile]
        //
        // 3) submit message to pre-exist topic
        // args: [size] [pemFile] [topicID]
        //

        // parsing local argument specific to this test
        if (args.length > (usedArgs)) {
            messageSize = Integer.parseInt(args[usedArgs]);
            log.info("Set messageSize as " + messageSize);
            usedArgs++;
        }

        if (args.length > (usedArgs)) {
            pemFile = args[usedArgs];
            log.info("Set pemFile as " + pemFile);
            usedArgs++;
        }

        if (args.length > usedArgs) {
            topicID = args[usedArgs];
            log.info("Set topicID as " + topicID);
            usedArgs++;
        }

        SubmitMessageLoadTest suite = new SubmitMessageLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runSubmitMessages());
    }

    private static HapiSpec runSubmitMessages() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        final AtomicInteger submittedSoFar = new AtomicInteger(0);
        Supplier<HapiSpecOperation[]> submitBurst =
                () -> new HapiSpecOperation[] {opSupplier(settings).get()};

        return defaultHapiSpec("RunSubmitMessages")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        // if no pem file defined then create a new submitKey
                        pemFile == null
                                ? newKeyNamed("submitKey")
                                : keyFromPem(pemFile)
                                        .name("submitKey")
                                        .simpleWacl()
                                        .passphrase(KeyFactory.PEM_PASSPHRASE),
                        // if just created a new key then export spec for later reuse
                        pemFile == null
                                ? withOpContext((spec, ignore) ->
                                        spec.keys().exportSimpleKey("topicSubmitKey.pem", "submitKey"))
                                : sleepFor(100),
                        logIt(ignore -> settings.toString()))
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of(
                                        "hapi.throttling.buckets.fastOpBucket.capacity",
                                        "4000",
                                        "hapi.throttling.ops.consensusSubmitMessage.capacityRequired",
                                        "1.0")),
                        cryptoCreate("sender")
                                .balance(ignore -> settings.getInitialBalance())
                                .withRecharging()
                                .rechargeWindow(3)
                                .hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
                        topicID == null
                                ? createTopic("topic")
                                        .submitKeyName("submitKey")
                                        .hasRetryPrecheckFrom(
                                                BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
                                : sleepFor(100),
                        sleepFor(10000) // wait all other thread ready
                        )
                .then(
                        defaultLoadTest(submitBurst, settings),
                        getAccountBalance("sender").logged());
    }

    private static Supplier<HapiSpecOperation> opSupplier(PerfTestLoadSettings settings) {
        int msgSize = (r.nextInt(2) == 1)
                ? settings.getIntProperty("messageSize", messageSize) + r.nextInt(settings.getHcsSubmitMessageSizeVar())
                : settings.getIntProperty("messageSize", messageSize)
                        - r.nextInt(settings.getHcsSubmitMessageSizeVar());

        String senderId = "sender";
        String topicId = "topic";
        String senderKey = "sender";
        String submitKey = "submitKey";
        if (settings.getTotalAccounts() > 1) {
            int s = r.nextInt(settings.getTotalAccounts());
            int re = 0;
            do {
                re = r.nextInt(settings.getTotalAccounts());
            } while (re == s);
            // maybe use some more realistic distributions to simulate real world scenarios
            senderId = String.format("0.0.%d", TEST_ACCOUNT_STARTS_FROM + r.nextInt(settings.getTotalAccounts()));
            topicId = String.format(
                    "0.0.%d",
                    TEST_ACCOUNT_STARTS_FROM + settings.getTotalAccounts() + r.nextInt(settings.getTotalTopics()));
            senderKey = GENESIS;
            submitKey = GENESIS;
        }

        if (log.isDebugEnabled()) {
            log.debug("{} will submit a message of size {} to topic {}", senderId, msgSize, topicId);
        }
        var op = submitMessageTo(topicId)
                .message(ArrayUtils.addAll(
                        ByteBuffer.allocate(8)
                                .putLong(Instant.now().toEpochMilli())
                                .array(),
                        randomUtf8Bytes(msgSize - 8)))
                .noLogging()
                .payingWith(senderId)
                .signedBy(senderKey, submitKey)
                .fee(100_000_000)
                .suppressStats(true)
                .hasRetryPrecheckFrom(
                        BUSY,
                        DUPLICATE_TRANSACTION,
                        PLATFORM_TRANSACTION_NOT_CREATED,
                        TOPIC_EXPIRED,
                        INVALID_TOPIC_ID,
                        INSUFFICIENT_PAYER_BALANCE)
                .hasKnownStatusFrom(
                        SUCCESS,
                        OK,
                        INVALID_TOPIC_ID,
                        INSUFFICIENT_PAYER_BALANCE,
                        UNKNOWN,
                        TRANSACTION_EXPIRED,
                        MESSAGE_SIZE_TOO_LARGE)
                .deferStatusResolution();
        if (settings.getBooleanProperty("isChunk", false)) {
            return () -> op.chunkInfo(1, 1).usePresetTimestamp();
        }
        return () -> op;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
