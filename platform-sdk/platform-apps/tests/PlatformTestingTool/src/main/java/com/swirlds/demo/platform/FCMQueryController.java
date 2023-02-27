/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.throttle.Throttle;
import com.swirlds.demo.merkle.map.FCMConfig;
import com.swirlds.demo.merkle.map.MapValueData;
import com.swirlds.demo.merkle.map.internal.ExpectedFCMFamily;
import com.swirlds.merkle.map.test.lifecycle.EntityType;
import com.swirlds.merkle.map.test.lifecycle.ExpectedValue;
import com.swirlds.merkle.map.test.lifecycle.TransactionType;
import com.swirlds.merkle.map.test.pta.MapKey;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controller that executes FCM queries randomly through multiple threads.
 */
class FCMQueryController {

    private static final Logger logger = LogManager.getLogger(FCMQueryController.class);

    private final Platform platform;
    private final int numberOfThreads;
    private final Throttle throttle;

    private static final SpeedometerMetric.Config FCM_QUERIES_ANSWERED_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    "FCM", "fcmQueriesAnsweredPerSecond")
            .withDescription("number of FCM queries have been answered per second")
            .withFormat(FORMAT_9_6);
    private final SpeedometerMetric fcmQueriesAnsweredPerSecond;

    public FCMQueryController(final FCMConfig.FCMQueryConfig fcmQueryConfig, final Platform platform) {
        this.platform = platform;
        this.numberOfThreads = fcmQueryConfig.getNumberOfThreads();
        this.throttle = new Throttle(fcmQueryConfig.getQps());
        this.fcmQueriesAnsweredPerSecond =
                platform.getContext().getMetrics().getOrCreate(FCM_QUERIES_ANSWERED_PER_SECOND_CONFIG);
    }

    public void launch() {
        final ExecutorService queryThreadPool = Executors.newFixedThreadPool(this.numberOfThreads);
        for (int index = 0; index < this.numberOfThreads; index++) {
            queryThreadPool.execute(this::execute);
        }
    }

    private void execute() {
        //noinspection InfiniteLoopStatement
        try {
            while (true) {
                while (!this.throttle.allow()) {
                    Thread.onSpinWait();
                }

                try (final AutoCloseableWrapper<PlatformTestingToolState> stateWrapper =
                        platform.getLatestImmutableState()) {
                    final PlatformTestingToolState state = stateWrapper.get();
                    if (state == null) {
                        continue;
                    }

                    executeFCMQuery(state);
                }
            }
        } catch (final Exception ex) {
            logger.error(EXCEPTION.getMarker(), "exception on query thread", ex);
        }
    }

    private void executeFCMQuery(final PlatformTestingToolState state) {
        final Optional<Pair<MapKey, MapValueData>> optionalPair = getRandomValue(state);
        if (optionalPair.isEmpty()) {
            return;
        }

        final Pair<MapKey, MapValueData> pair = optionalPair.get();
        final MapKey key = pair.getKey();
        final MapValueData data = pair.getValue();

        final ExpectedFCMFamily expectedFamily = state.getStateExpectedMap();
        final ExpectedValue expectedValue = expectedFamily.getExpectedMap().get(key);
        if (expectedValue.getUid() != data.getUid()) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Value for key {} doesn't have matching value {} != {}",
                    key,
                    expectedValue.getUid(),
                    data.getUid());
        }

        fcmQueriesAnsweredPerSecond.update(1);
    }

    private Optional<Pair<MapKey, MapValueData>> getRandomValue(final PlatformTestingToolState state) {
        final MapKey key = state.getStateExpectedMap()
                .getMapKeyForFCMTx(TransactionType.Update, EntityType.Crypto, false, false, 0);

        if (key == null) {
            return Optional.empty();
        }

        final MapValueData data = state.getStateMap().getMap().get(key);
        if (data == null) {
            return Optional.empty();
        }

        return Optional.of(Pair.of(key, data));
    }
}
