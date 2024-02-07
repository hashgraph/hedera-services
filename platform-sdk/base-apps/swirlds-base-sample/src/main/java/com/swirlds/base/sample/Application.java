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

package com.swirlds.base.sample;

import com.swirlds.base.sample.config.BaseApiConfig;
import com.swirlds.base.sample.config.internal.ClasspathConfigSource;
import com.swirlds.base.sample.internal.InternalTestData;
import com.swirlds.base.sample.internal.ServerUtils;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.metrics.BenchmarkMetrics;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.AbstractMetricAdapter;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.PropertyFileConfigSource;
import com.swirlds.config.extensions.sources.SystemEnvironmentConfigSource;
import com.swirlds.config.extensions.sources.SystemPropertiesConfigSource;
import com.swirlds.metrics.api.Metrics;
import java.nio.file.Path;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Application {
    private static final Logger log = LogManager.getLogger(AbstractMetricAdapter.class);
    private static final String SWIRLDS_CONFIG_PACKAGE = "com.swirlds";
    public static final String APPLICATION_PROPERTIES = "application.properties";
    public static final Path EXTERNAL_PROPERTIES = Path.of("./config/application.properties");

    public static void main(String[] args) {
        try {
            ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();
            ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, Set.of(SWIRLDS_CONFIG_PACKAGE));
            configurationBuilder
                    .withSource(SystemEnvironmentConfigSource.getInstance())
                    .withSource(SystemPropertiesConfigSource.getInstance())
                    .withSource(new ClasspathConfigSource(Path.of(APPLICATION_PROPERTIES)));

            if (EXTERNAL_PROPERTIES.toFile().exists())
                configurationBuilder.withSources(new PropertyFileConfigSource(EXTERNAL_PROPERTIES));

            Configuration configuration = configurationBuilder.build();
            DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
            Metrics metrics = metricsProvider.createPlatformMetrics(NodeId.FIRST_NODE_ID);
            PlatformContext context = new DefaultPlatformContext(
                    configuration, metrics, CryptographyFactory.create(configuration), Time.getCurrent());

            // Add Benchmark metrics
            BenchmarkMetrics.registerMetrics(context);
            ApplicationMetrics.registerMetrics(context);

            // Start metric provider
            metricsProvider.start();

            final BaseApiConfig baseApiConfig = context.getConfiguration().getConfigData(BaseApiConfig.class);
            InternalTestData.create();
            ServerUtils.createServer(baseApiConfig, context);

            log.trace("Loaded configuration {}", baseApiConfig);

        } catch (Exception e) {
            log.error("Error starting up", e);
        }
    }
}
