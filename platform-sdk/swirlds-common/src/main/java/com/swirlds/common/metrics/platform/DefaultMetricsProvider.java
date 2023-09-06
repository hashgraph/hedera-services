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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.sun.net.httpserver.HttpServer;
import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The default implementation of {@link MetricsProvider}
 */
public class DefaultMetricsProvider implements MetricsProvider, Lifecycle {

    private static final Logger logger = LogManager.getLogger(DefaultMetricsProvider.class);

    private final MetricsFactory factory;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            getStaticThreadManager().createThreadFactory("platform-core", "MetricsThread"));

    private final MetricKeyRegistry metricKeyRegistry = new MetricKeyRegistry();
    private final DefaultMetrics globalMetrics;
    private final ConcurrentMap<NodeId, DefaultMetrics> platformMetrics = new ConcurrentHashMap<>();
    private final PrometheusEndpoint prometheusEndpoint;
    private final SnapshotService snapshotService;
    private final MetricsConfig metricsConfig;
    private final PathsConfig pathsConfig;
    private final Configuration configuration;

    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    /**
     * Constructor of {@code DefaultMetricsProvider}
     */
    public DefaultMetricsProvider(@NonNull final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration is null");

        pathsConfig = configuration.getConfigData(PathsConfig.class);
        metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final PrometheusConfig prometheusConfig = configuration.getConfigData(PrometheusConfig.class);
        factory = new DefaultMetricsFactory(metricsConfig);

        globalMetrics = new DefaultMetrics(null, metricKeyRegistry, executor, factory, metricsConfig);

        // setup SnapshotService
        snapshotService = new SnapshotService(globalMetrics, executor, metricsConfig.getMetricsSnapshotDuration());

        // setup Prometheus endpoint
        PrometheusEndpoint endpoint = null;
        if (!metricsConfig.disableMetricsOutput() && prometheusConfig.endpointEnabled()) {
            final InetSocketAddress address = new InetSocketAddress(prometheusConfig.endpointPortNumber());
            try {
                final HttpServer httpServer = HttpServer.create(address, prometheusConfig.endpointMaxBacklogAllowed());
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
    public Metrics createPlatformMetrics(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "selfId is null");

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
            final Path folderPath = folderName.isBlank() ? pathsConfig.getWorkingDirPath() : Path.of(folderName);

            // setup LegacyCsvWriter
            if (!metricsConfig.csvFileName().isBlank()) {
                final LegacyCsvWriter legacyCsvWriter = new LegacyCsvWriter(nodeId, folderPath, configuration);
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
