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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.metrics.api.Counter;
import java.time.temporal.ChronoUnit;

public class ApplicationMetrics {

    public static final Counter.Config REQUEST_COUNT =
            new Counter.Config(MetricsConstants.CATEGORY, "requests").withDescription("total number of request");
    public static final Counter.Config ERROR_COUNT =
            new Counter.Config(MetricsConstants.CATEGORY, "error").withDescription("total number of errors");

    public static final Counter.Config WALLETS_COUNT =
            new Counter.Config(MetricsConstants.CATEGORY, "wallets").withDescription("total number of wallets");
    public static final Counter.Config TRANSACTION_COUNT = new Counter.Config(MetricsConstants.CATEGORY, "transactions")
            .withDescription("total number of transactions");
    public static final CountPerSecond.Config TRANSACTION_PER_SECOND = new CountPerSecond.Config(
                    MetricsConstants.CATEGORY, "t_")
            .withUnit("_per_second")
            .withDescription("transactions per second");

    public static final DurationGauge.Config TRANSACTION_DURATION = new DurationGauge.Config(
                    MetricsConstants.CATEGORY, "requestProcessingTime", ChronoUnit.MILLIS)
            .withDescription("the time it takes to process a request");

    public static void registerMetrics(PlatformContext context) {
        context.getMetrics().getOrCreate(REQUEST_COUNT);
        context.getMetrics().getOrCreate(ERROR_COUNT);
        context.getMetrics().getOrCreate(WALLETS_COUNT);
        context.getMetrics().getOrCreate(TRANSACTION_COUNT);
        context.getMetrics().getOrCreate(TRANSACTION_DURATION);
    }
}
