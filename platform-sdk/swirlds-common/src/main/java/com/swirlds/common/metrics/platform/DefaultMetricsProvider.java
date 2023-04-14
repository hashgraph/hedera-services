/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;

import com.sun.net.httpserver.HttpServer;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.Lifecycle;
import com.swirlds.common.utility.LifecyclePhase;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The default implementation of {@link MetricsProvider}
 */
public class DefaultMetricsProvider implements MetricsProvider, Lifecycle {

    private static final Logger logger = LogManager.getLogger(DefaultMetricsProvider.class);

    private final MetricsFactory factory = new DefaultMetricsFactory();
    private final ScheduledExecutorService executor = getStaticThreadManager()
            .newScheduledExecutorServiceConfiguration("platform-core: MetricsThread")
            .build();

    private final MetricKeyRegistry metricKeyRegistry = new MetricKeyRegistry();
    private final DefaultMetrics globalMetrics;
    private final ConcurrentMap<NodeId, DefaultMetrics> platformMetrics = new ConcurrentHashMap<>();
    private final PrometheusEndpoint prometheusEndpoint;
    private final SnapshotService snapshotService;
    private final MetricsConfig metricsConfig;

    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    /**
     * Constructor of {@code DefaultMetricsProvider}
     */
    public DefaultMetricsProvider(final Configuration configuration) {
        CommonUtils.throwArgNull(configuration, "configuration");

        metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final PrometheusConfig prometheusConfig = configuration.getConfigData(PrometheusConfig.class);

        globalMetrics = new DefaultMetrics(null, metricKeyRegistry, executor, factory, metricsConfig);

        // setup SnapshotService
        snapshotService = new SnapshotService(globalMetrics, executor, metricsConfig.getMetricsSnapshotDuration());

        // setup Prometheus endpoint
        PrometheusEndpoint endpoint = null;
        if (!metricsConfig.disableMetricsOutput() && prometheusConfig.prometheusEndpointEnabled()) {
            final InetSocketAddress address = new InetSocketAddress(prometheusConfig.prometheusEndpointPortNumber());
            try {
                final HttpServer httpServer =
                        HttpServer.create(address, prometheusConfig.prometheusEndpointMaxBacklogAllowed());
                endpoint = new PrometheusEndpoint(httpServer);

                globalMetrics.subscribe(endpoint::handleMetricsChange);
                snapshotService.subscribe(endpoint::handleSnapshots);
            } catch (final IOException e) {
                logger.error("Exception while setting up Prometheus endpoint", e);
            }
        }
        prometheusEndpoint = endpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Metrics createGlobalMetrics() {
        return globalMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Metrics createPlatformMetrics(final NodeId nodeId) {
        CommonUtils.throwArgNull(nodeId, "selfId");

        final DefaultMetrics newMetrics =
                new DefaultMetrics(nodeId, metricKeyRegistry, executor, factory, metricsConfig);

        final DefaultMetrics oldMetrics = platformMetrics.putIfAbsent(nodeId, newMetrics);
        if (oldMetrics != null) {
            throw new IllegalStateException(String.format("PlatformMetrics for %s already exists", nodeId));
        }
        globalMetrics.subscribe(newMetrics::handleGlobalMetrics);

        if (lifecyclePhase == LifecyclePhase.STARTED) {
            newMetrics.start();
        }
        snapshotService.addPlatformMetric(newMetrics);

        if (!metricsConfig.disableMetricsOutput()) {
            final String folderName = metricsConfig.csvOutputFolder();
            final Path folderPath = Path.of(StringUtils.isBlank(folderName) ? FileUtils.getUserDir() : folderName);

            // setup LegacyCsvWriter
            if (StringUtils.isNotBlank(metricsConfig.csvFileName())) {
                final LegacyCsvWriter legacyCsvWriter = new LegacyCsvWriter(nodeId, folderPath, metricsConfig);
                snapshotService.subscribe(legacyCsvWriter::handleSnapshots);
            }

            // setup Prometheus Endpoint
            if (prometheusEndpoint != null) {
                newMetrics.subscribe(prometheusEndpoint::handleMetricsChange);
            }
        }

        return newMetrics;
    }

    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (lifecyclePhase == LifecyclePhase.NOT_STARTED) {
            globalMetrics.start();
            for (final DefaultMetrics metrics : platformMetrics.values()) {
                metrics.start();
            }
            snapshotService.start();
            lifecyclePhase = LifecyclePhase.STARTED;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        if (lifecyclePhase == LifecyclePhase.STARTED) {
            snapshotService.shutdown();
            prometheusEndpoint.close();
            lifecyclePhase = LifecyclePhase.STOPPED;
        }
    }
}
