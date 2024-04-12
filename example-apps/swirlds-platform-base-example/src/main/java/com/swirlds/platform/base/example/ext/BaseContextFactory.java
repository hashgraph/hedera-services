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

package com.swirlds.platform.base.example.ext;

import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.ClasspathFileConfigSource;
import com.swirlds.config.extensions.sources.PropertyFileConfigSource;
import com.swirlds.config.extensions.sources.SystemEnvironmentConfigSource;
import com.swirlds.config.extensions.sources.SystemPropertiesConfigSource;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.base.example.BaseContext;
import java.io.IOException;
import java.nio.file.Path;

public class BaseContextFactory {

    public static final String APPLICATION_PROPERTIES = "app.properties";
    public static final Path EXTERNAL_PROPERTIES = Path.of("./config/app.properties");

    public static BaseContext create() throws IOException {
        final Configuration configuration = getConfiguration();
        final Metrics metrics = getMetrics(configuration);
        return new BaseContext(metrics, configuration);
    }

    private static Configuration getConfiguration() throws IOException {
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();

        configurationBuilder
                .withSource(SystemEnvironmentConfigSource.getInstance())
                .withSource(SystemPropertiesConfigSource.getInstance())
                .withSource(new ClasspathFileConfigSource(Path.of(APPLICATION_PROPERTIES)))
                .autoDiscoverExtensions();

        if (EXTERNAL_PROPERTIES.toFile().exists()) {
            configurationBuilder.withSources(new PropertyFileConfigSource(EXTERNAL_PROPERTIES));
        }

        final Configuration configuration = configurationBuilder.build();
        return configuration;
    }

    private static Metrics getMetrics(Configuration configuration) {
        final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        final Metrics metrics = metricsProvider.createPlatformMetrics(NodeId.FIRST_NODE_ID);
        metricsProvider.start();
        return metrics;
    }
}
