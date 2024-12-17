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

package com.swirlds.platform.base.example.server;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ServerMetrics {

    public static final String SERVER_METRICS_CATEGORY = "server";
    public static final Counter.Config REQUEST_TOTAL =
            new Counter.Config(SERVER_METRICS_CATEGORY, "requests_total").withDescription("total number of request");
    public static final Counter.Config ERROR_TOTAL =
            new Counter.Config(SERVER_METRICS_CATEGORY, "error_total").withDescription("total number of errors");
    public static final RunningAverageMetric.Config REQUEST_AVG_TIME = new RunningAverageMetric.Config(
                    SERVER_METRICS_CATEGORY, "request_avg_time")
            .withUnit("ns")
            .withDescription("average request time");
    public static final CountPerSecond.Config REQUESTS_PER_SECOND = new CountPerSecond.Config(
                    SERVER_METRICS_CATEGORY, "requests_per_second")
            .withUnit("ops")
            .withDescription("requests per second");

    public static void registerMetrics(@NonNull final Metrics metrics) {
        metrics.getOrCreate(REQUEST_TOTAL);
        metrics.getOrCreate(REQUEST_AVG_TIME);
        metrics.getOrCreate(ERROR_TOTAL);
    }
}
