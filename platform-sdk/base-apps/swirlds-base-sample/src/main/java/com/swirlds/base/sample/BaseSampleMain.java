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
import com.swirlds.base.sample.internal.Context;
import com.swirlds.base.sample.internal.InitialData;
import com.swirlds.base.sample.internal.ServerUtils;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.metrics.BenchmarkMetrics;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.AbstractMetricAdapter;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.ClasspathFileConfigSource;
import com.swirlds.config.extensions.sources.PropertyFileConfigSource;
import com.swirlds.config.extensions.sources.SystemEnvironmentConfigSource;
import com.swirlds.config.extensions.sources.SystemPropertiesConfigSource;
import com.swirlds.metrics.api.Metrics;
import java.nio.file.Path;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This application serves as a testing environment for platform-base module behavior without the need for platform and services layers.
 */
public class BaseSampleMain {
    private static final Logger logger = LogManager.getLogger(AbstractMetricAdapter.class);
    private static final String SWIRLDS_CONFIG_PACKAGE = "com.swirlds";
    public static final String APPLICATION_PROPERTIES = "app.properties";
    public static final Path EXTERNAL_PROPERTIES = Path.of("./config/app.properties");

    public static void main(String[] args) {
        try {
            final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();
            ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, Set.of(SWIRLDS_CONFIG_PACKAGE));
            configurationBuilder
                    .withSource(SystemEnvironmentConfigSource.getInstance())
                    .withSource(SystemPropertiesConfigSource.getInstance())
                    .withSource(new ClasspathFileConfigSource(Path.of(APPLICATION_PROPERTIES)));

            if (EXTERNAL_PROPERTIES.toFile().exists()) {
                configurationBuilder.withSources(new PropertyFileConfigSource(EXTERNAL_PROPERTIES));
            }

            final Configuration configuration = configurationBuilder.build();
            final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
            final Metrics metrics = metricsProvider.createPlatformMetrics(NodeId.FIRST_NODE_ID);
            final Context context = new Context(metrics, configuration);

            // Add Benchmark metrics
            BenchmarkMetrics.registerMetrics(context);
            ApplicationMetrics.registerMetrics(context);

            // Start metric provider
            metricsProvider.start();

            final BaseApiConfig baseApiConfig = configuration.getConfigData(BaseApiConfig.class);
            logger.trace("Loaded configuration {}", baseApiConfig);
            InitialData.populate();
            ServerUtils.createServer(baseApiConfig, context);

        } catch (Exception e) {
            logger.error("Error starting up", e);
        }
    }
}
