/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.sample.metrics;

import com.swirlds.base.sample.persistence.WalletDao;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.metrics.api.Counter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.temporal.ChronoUnit;

/**
 * All application
 */
public class ApplicationMetrics {

    public static final String APPLICATION_CATEGORY = "app";
    public static final String REST_API = "restapi";
    public static final Counter.Config REQUEST_COUNT =
            new Counter.Config(REST_API, "requests_count").withDescription("total number of request");
    public static final Counter.Config ERROR_COUNT =
            new Counter.Config(REST_API, "error_count").withDescription("total number of errors");
    public static final RunningAverageMetric.Config REQUEST_AVG_TIME = new RunningAverageMetric.Config(
                    REST_API, "request_avg_time")
            .withUnit("ms")
            .withDescription("average request time");
    public static final CountPerSecond.Config REQUESTS_PER_SECOND = new CountPerSecond.Config(
                    REST_API, "requests_per_second")
            .withUnit("ops")
            .withDescription("requests per second");
    private static final FunctionGauge.Config<Integer> WALLETS_COUNT = new FunctionGauge.Config<>(
                    APPLICATION_CATEGORY, "wallet_total", Integer.class, () -> WalletDao.getInstance()
                            .countAll())
            .withDescription("total number of wallets in the system");

    public static final Counter.Config WALLETS_CREATION_COUNT = new Counter.Config(
            APPLICATION_CATEGORY, "wallet_creation_count")
            .withDescription("total number of created wallets with the api");
    public static final Counter.Config TRANSFERS_COUNT = new Counter.Config(
                    APPLICATION_CATEGORY, "transfer_count")
            .withDescription("total number of created transfers with the api");
    public static final DurationGauge.Config TRANSFER_TIME = new DurationGauge.Config(
                    APPLICATION_CATEGORY, "transfer_time", ChronoUnit.NANOS)
            .withDescription("the time it takes to process a transfer");

    /**
     * Register all metrics configurations
     */
    public static void registerMetrics(@NonNull final PlatformContext context) {
        context.getMetrics().getOrCreate(REQUEST_COUNT);
        context.getMetrics().getOrCreate(REQUEST_AVG_TIME);
        context.getMetrics().getOrCreate(ERROR_COUNT);
        context.getMetrics().getOrCreate(WALLETS_COUNT);
        context.getMetrics().getOrCreate(TRANSFERS_COUNT);
        context.getMetrics().getOrCreate(TRANSFER_TIME);
    }
}
