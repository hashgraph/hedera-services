/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.spi.fixtures.TestBase;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AppTestBase extends TestBase implements TransactionFactory {

    // For many of our tests we need to have metrics available, and an easy way to test the metrics
    // are being set appropriately.
    /** Used as a dependency to the {@link Metrics} system. */
    private static final ScheduledExecutorService METRIC_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    /** Represents "this node" in our tests. */
    private final NodeId nodeSelfId = new NodeId(false, 7);
    /** The AccountID of "this node" in our tests. */
    protected final AccountID nodeSelfAccountId =
            AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(8).build();

    /**
     * The gRPC system has extensive metrics. This object allows us to inspect them and make sure
     * they are being set correctly for different types of calls.
     */
    protected Metrics metrics =
            new DefaultMetrics(nodeSelfId, new MetricKeyRegistry(), METRIC_EXECUTOR, new DefaultMetricsFactory());

    protected Counter counterMetric(String name) {
        return (Counter) metrics.getMetric("app", name);
    }

    protected SpeedometerMetric speedometerMetric(String name) {
        return (SpeedometerMetric) metrics.getMetric("app", name);
    }
}
