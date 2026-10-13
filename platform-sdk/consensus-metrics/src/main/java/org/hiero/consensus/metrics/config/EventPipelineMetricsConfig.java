// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.metrics.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;
import org.hiero.consensus.metrics.statistics.EventPipelineTracker;

/**
 * Configuration of the {@link EventPipelineTracker}, which reports the delay of events at each stage of the event
 * pipeline.
 * <p>
 * A latency window should hold about 100 events beyond its highest percentile {@code p}, so it needs a length of at
 * least {@code 100 / ((1 - p) * rate)}. A node creates its own events at up to {@code event.creation.maxCreationRate}
 * per second, and in a network of {@code N} nodes receives up to {@code N - 1} times as many through gossip. The
 * defaults assume the default rate of 20 per second and 30 nodes: p99 of its own events needs at least 8.3 minutes,
 * p99.9 of gossiped events at least 2.9 minutes. A longer window costs no memory, but reacts more slowly.
 *
 * @param averageAndMaxEnabled if true, the platform will collect and report the average and maximum delay of events at
 *                             each stage of the event pipeline
 * @param latencyEnabled       if true, the platform will collect and report percentiles and the maximum of the delay
 *                             of its own and gossiped events at each stage of the event pipeline, over a sliding window
 * @param latencyRuntimeWindow the length of the sliding window over the delays of its own events, which report
 *                             percentiles up to p99
 * @param latencyGossipWindow  the length of the sliding window over the delays of gossiped events, which report
 *                             percentiles up to p99.9
 */
@ConfigData("event.pipeline.metrics")
public record EventPipelineMetricsConfig(
        @ConfigProperty(defaultValue = "true") boolean averageAndMaxEnabled,
        @ConfigProperty(defaultValue = "true") boolean latencyEnabled,
        @ConfigProperty(defaultValue = "10m") Duration latencyRuntimeWindow,
        @ConfigProperty(defaultValue = "5m") Duration latencyGossipWindow) {}
