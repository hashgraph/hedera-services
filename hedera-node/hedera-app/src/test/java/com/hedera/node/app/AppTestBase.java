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
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.fixtures.TestBase;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AppTestBase extends TestBase implements TransactionFactory, Scenarios {

    // For many of our tests we need to have metrics available, and an easy way to test the metrics
    // are being set appropriately.
    /** Used as a dependency to the {@link Metrics} system. */
    private static final ScheduledExecutorService METRIC_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";
    public static final String ALICE_ALIAS = "Alice Alias";
    protected MapWritableKVState<AccountID, Account> accountsState;
    protected MapWritableKVState<String, AccountID> aliasesState;
    protected HederaState state;

    protected void setupStandardStates() {
        accountsState = new MapWritableKVState<>(
                ACCOUNTS_KEY,
                Map.of(
                        ALICE.accountID(), ALICE.account(),
                        ERIN.accountID(), ERIN.account(),
                        STAKING_REWARD_ACCOUNT.accountID(), STAKING_REWARD_ACCOUNT.account()));
        aliasesState = new MapWritableKVState<>(ALIASES_KEY, Map.of());
        final var writableStates = MapWritableStates.builder()
                .state(accountsState)
                .state(aliasesState)
                .build();

        state = new HederaState() {
            @NonNull
            @Override
            public ReadableStates createReadableStates(@NonNull String serviceName) {
                return serviceName == TokenService.NAME ? writableStates : null;
            }

            @NonNull
            @Override
            public WritableStates createWritableStates(@NonNull String serviceName) {
                return serviceName == TokenService.NAME ? writableStates : null;
            }
        };
    }

    /** Represents "this node" in our tests. */
    private final NodeId nodeSelfId = new NodeId(7);
    /** The AccountID of "this node" in our tests. */
    protected final AccountID nodeSelfAccountId =
            AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(8).build();

    /**
     * The gRPC system has extensive metrics. This object allows us to inspect them and make sure they are being set
     * correctly for different types of calls.
     */
    protected final Metrics metrics;

    public AppTestBase() {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(MetricsConfig.class)
                .build();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        this.metrics = new DefaultMetrics(
                nodeSelfId, new MetricKeyRegistry(), METRIC_EXECUTOR, new DefaultMetricsFactory(), metricsConfig);
    }

    protected Counter counterMetric(final String name) {
        return (Counter) metrics.getMetric("app", name);
    }

    protected SpeedometerMetric speedometerMetric(final String name) {
        return (SpeedometerMetric) metrics.getMetric("app", name);
    }
}
