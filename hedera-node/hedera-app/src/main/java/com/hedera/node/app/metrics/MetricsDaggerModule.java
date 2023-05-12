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

package com.hedera.node.app.metrics;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.system.Platform;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

/** A Dagger module for providing dependencies based on {@link Metrics}. */
@Module
public interface MetricsDaggerModule {
    @Provides
    @Singleton
    static Metrics provideMetrics(@NonNull final Platform platform) {
        return new DefaultMetrics(
                platform.getSelfId(),
                new MetricKeyRegistry(),
                Executors.newSingleThreadScheduledExecutor(),
                new DefaultMetricsFactory(),
                platform.getContext().getConfiguration().getConfigData(MetricsConfig.class));
    }
}
