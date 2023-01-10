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
package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_INITIAL_BALANCE;
import static com.hedera.services.bdd.suites.perf.PerfTestLoadSettings.DEFAULT_MEMO_LENGTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadTest extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LoadTest.class);

    public static OptionalDouble targetTPS = OptionalDouble.empty();
    public static OptionalInt testDurationMinutes = OptionalInt.empty();
    public static OptionalInt threadNumber = OptionalInt.empty();
    public static OptionalInt hcsSubmitMessage = OptionalInt.empty();
    public static OptionalInt hcsSubmitMessageSizeVar = OptionalInt.empty();
    /** initial balance of account used as sender for performance test transactions */
    public static OptionalLong initialBalance = OptionalLong.of(DEFAULT_INITIAL_BALANCE);

    public static OptionalInt totalTestAccounts = OptionalInt.empty();
    public static OptionalInt totalTestTopics = OptionalInt.empty();
    public static OptionalInt totalTestTokens = OptionalInt.empty();
    public static OptionalInt durationCreateTokenAssociation = OptionalInt.empty();
    public static OptionalInt durationTokenTransfer = OptionalInt.empty();
    public static OptionalInt testTreasureStartAccount = OptionalInt.empty();
    public static OptionalInt totalTokenAssociations = OptionalInt.empty();
    public static OptionalInt totalScheduled = OptionalInt.empty();
    public static OptionalInt totalTestTokenAccounts = OptionalInt.empty();
    public static OptionalInt memoLength = OptionalInt.of(DEFAULT_MEMO_LENGTH);
    public static OptionalInt testTopicId = OptionalInt.empty();

    public static OptionalInt balancesExportPeriodSecs = OptionalInt.empty();
    public static Optional<Boolean> clientToExportBalances = Optional.empty();

    protected final ResponseCodeEnum[] standardPermissiblePrechecks =
            new ResponseCodeEnum[] {
                OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED
            };

    public static int parseArgs(String... args) {
        int usedArgs = 0;
        if (args.length > 0) {
            targetTPS = OptionalDouble.of(Double.parseDouble(args[0]));
            log.info("Set targetTPS as " + targetTPS.getAsDouble());
            usedArgs++;
        }

        if (args.length > 1) {
            testDurationMinutes = OptionalInt.of(Integer.parseInt(args[1]));
            log.info("Set testDurationMinutes as " + testDurationMinutes.getAsInt());
            usedArgs++;
        }

        if (args.length > 2) {
            threadNumber = OptionalInt.of(Integer.parseInt(args[2]));
            log.info("Set threadNumber as " + threadNumber.getAsInt());
            usedArgs++;
        }

        if (args.length > 3) {
            initialBalance = OptionalLong.of(Long.parseLong(args[3]));
            log.info("Set initialBalance as " + initialBalance.getAsLong());
            usedArgs++;
        }

        if (args.length > 4) {
            hcsSubmitMessage = OptionalInt.of(Integer.parseInt(args[4]));
            log.info("Set hcsSubmitMessageSize as " + hcsSubmitMessage.getAsInt());
            usedArgs++;
        }

        if (args.length > 5) {
            memoLength = OptionalInt.of(Integer.parseInt(args[5]));
            log.info("Set Memo Length as " + memoLength.getAsInt());
            usedArgs++;
        }

        return usedArgs;
    }

    public static double getTargetTPS() {
        return targetTPS.getAsDouble();
    }

    public static int getMemoLength() {
        return memoLength.getAsInt();
    }

    public static int getTestDurationMinutes() {
        return testDurationMinutes.getAsInt();
    }

    public static int getTestTopicId() {
        return testTopicId.getAsInt();
    }

    public static RunLoadTest defaultLoadTest(
            Supplier<HapiSpecOperation[]> opSource, PerfTestLoadSettings settings) {
        return runLoadTest(opSource)
                .tps(targetTPS.isPresent() ? LoadTest::getTargetTPS : settings::getTps)
                .tolerance(settings::getTolerancePercentage)
                .allowedSecsBelow(settings::getAllowedSecsBelow)
                .setMemoLength(settings::getMemoLength)
                .setNumberOfThreads(
                        threadNumber.isPresent() ? threadNumber::getAsInt : settings::getThreads)
                .setTotalTestAccounts(
                        totalTestAccounts.isPresent()
                                ? totalTestAccounts::getAsInt
                                : settings::getTotalAccounts)
                .setTotalTestTopics(
                        totalTestTopics.isPresent()
                                ? totalTestTopics::getAsInt
                                : settings::getTotalTopics)
                .setTotalTestTokens(
                        totalTestTokens.isPresent()
                                ? totalTestTokens::getAsInt
                                : settings::getTotalTokens)
                .setDurationCreateTokenAssociation(
                        durationCreateTokenAssociation.isPresent()
                                ? durationCreateTokenAssociation::getAsInt
                                : settings::getDurationCreateTokenAssociation)
                .setDurationTokenTransfer(
                        durationTokenTransfer.isPresent()
                                ? durationTokenTransfer::getAsInt
                                : settings::getDurationTokenTransfer)
                .setTotalTestTokenAccounts(
                        totalTestTokenAccounts.isPresent()
                                ? totalTestTokenAccounts::getAsInt
                                : settings::getTotalTestTokenAccounts)
                .setTotalTestTopics(
                        totalTestTopics.isPresent()
                                ? totalTestTopics::getAsInt
                                : settings::getTotalTopics)
                .setTotalScheduled(
                        totalScheduled.isPresent()
                                ? totalScheduled::getAsInt
                                : settings::getTotalScheduled)
                .setTotalTokenAssociations(
                        totalTokenAssociations.isPresent()
                                ? totalTokenAssociations::getAsInt
                                : settings::getTotalTokenAssociations)
                .setTestTreasureStartAccount(
                        testTreasureStartAccount.isPresent()
                                ? testTreasureStartAccount::getAsInt
                                : settings::getTestTreasureStartAccount)
                .setTestTopicId(
                        testTopicId.isPresent() ? testTopicId::getAsInt : settings::getTestTopicId)
                .setHCSSubmitMessageSize(
                        hcsSubmitMessage.isPresent()
                                ? hcsSubmitMessage::getAsInt
                                : settings::getHcsSubmitMessageSize)
                .setHCSSubmitMessageSizeVar(
                        hcsSubmitMessageSizeVar.isPresent()
                                ? hcsSubmitMessageSizeVar::getAsInt
                                : settings::getHcsSubmitMessageSizeVar)
                .setBalancesExportPeriodSecs(
                        balancesExportPeriodSecs.isPresent()
                                ? balancesExportPeriodSecs::getAsInt
                                : settings::getBalancesExportPeriodSecs)
                .setClientToExportBalances(
                        clientToExportBalances.isPresent()
                                ? clientToExportBalances::get
                                : settings::getClientToExportBalances)
                .setInitialBalance(settings::getInitialBalance)
                .lasting(
                        (testDurationMinutes.isPresent()
                                ? LoadTest::getTestDurationMinutes
                                : settings::getMins),
                        () -> MINUTES);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return null;
    }
}
