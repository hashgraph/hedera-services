package com.swirlds.baseapi;

import com.swirlds.base.time.Time;
import com.swirlds.baseapi.config.BaseApiConfig;
import com.swirlds.baseapi.config.internal.ClasspathConfigSource;
import com.swirlds.baseapi.internal.InternalTestData;
import com.swirlds.baseapi.internal.ServerUtils;
import com.swirlds.baseapi.metrics.ApplicationMetrics;
import com.swirlds.baseapi.metrics.BenchmarkMetrics;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.AbstractMetricAdapter;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
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

    public static void main(String[] args) {
        try {
            ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create();
            ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, Set.of(SWIRLDS_CONFIG_PACKAGE));
            configurationBuilder.withSource(SystemEnvironmentConfigSource.getInstance())
                    .withSource(SystemPropertiesConfigSource.getInstance())
                    .withSource(new ClasspathConfigSource(Path.of("application.properties")));

            Configuration configuration = configurationBuilder.build();
            DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
            Metrics metrics = metricsProvider.createPlatformMetrics(NodeId.FIRST_NODE_ID);
            PlatformContext context = new DefaultPlatformContext(configuration, metrics,
                    CryptographyFactory.create(configuration), Time.getCurrent());

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
