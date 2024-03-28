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

import com.swirlds.base.sample.internal.Context;
import com.swirlds.base.sample.persistence.InventoryDao;
import com.swirlds.base.sample.persistence.ItemDao;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.metrics.api.Counter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * All application
 */
public class ApplicationMetrics {

    public static final String APPLICATION_CATEGORY = "app";
    public static final String REST_API = "restapi";
    public static final Counter.Config REQUEST_TOTAL =
            new Counter.Config(REST_API, "requests_total").withDescription("total number of request");
    public static final Counter.Config ERROR_TOTAL =
            new Counter.Config(REST_API, "error_total").withDescription("total number of errors");
    public static final RunningAverageMetric.Config REQUEST_AVG_TIME = new RunningAverageMetric.Config(
                    REST_API, "request_avg_time")
            .withUnit("ns")
            .withDescription("average request time");
    public static final CountPerSecond.Config REQUESTS_PER_SECOND = new CountPerSecond.Config(
                    REST_API, "requests_per_second")
            .withUnit("ops")
            .withDescription("requests per second");
    private static final FunctionGauge.Config<Integer> ITEM_TOTAL = new FunctionGauge.Config<>(
                    APPLICATION_CATEGORY, "item_total", Integer.class, () -> ItemDao.getInstance()
                            .countAll())
            .withDescription("total number of items in the system");

    public static final Counter.Config OPERATION_TOTAL = new Counter.Config(APPLICATION_CATEGORY, "operation_total")
            .withDescription("total number of created operations with the api");
    public static final DurationGauge.Config OPERATION_TIME = new DurationGauge.Config(
                    APPLICATION_CATEGORY, "operation_time", ChronoUnit.NANOS)
            .withDescription("the time it takes to process an Operation");

    private static final FunctionGauge.Config<Long> ITEMS_BELOW_STOCK = new FunctionGauge.Config<>(
                    APPLICATION_CATEGORY,
                    "itemsBelowStock_total",
                    Long.class,
                    () -> ItemDao.getInstance().findAll().stream()
                            .filter(i -> {
                                assert i.id() != null;
                                return i.minimumStockLevel()
                                        < Objects.requireNonNull(InventoryDao.getInstance()
                                                        .findByItemId(i.id()))
                                                .amount();
                            })
                            .count())
            .withDescription("total number of items below minimum stock");

    /**
     * Register all metrics configurations
     */
    public static void registerMetrics(@NonNull final Context context) {
        context.metrics().getOrCreate(REQUEST_TOTAL);
        context.metrics().getOrCreate(REQUEST_AVG_TIME);
        context.metrics().getOrCreate(ERROR_TOTAL);
        context.metrics().getOrCreate(ITEM_TOTAL);
        context.metrics().getOrCreate(OPERATION_TOTAL);
        context.metrics().getOrCreate(OPERATION_TIME);
        context.metrics().getOrCreate(ITEMS_BELOW_STOCK);
    }
}
