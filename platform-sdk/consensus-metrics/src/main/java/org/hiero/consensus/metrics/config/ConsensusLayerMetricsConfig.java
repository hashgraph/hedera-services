// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.metrics.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;
import org.hiero.consensus.metrics.statistics.EventPipelineTracker;

/**
 * Configuration of the latency metrics of the consensus layer: the {@link EventPipelineTracker}, which reports the
 * delay of events at each stage of the event pipeline, and {@code eventLatency_R2C} in {@code ConsensusMetricsImpl},
 * the delay of gossiped events from receipt until the consensus engine emits their round.
 * <p>
 * A latency window should hold about 100 events beyond its highest percentile {@code p}, so it needs a length of at
 * least {@code 100 / ((1 - p) * rate)}. A node creates its own events at up to {@code event.creation.maxCreationRate}
 * per second, and in a network of {@code N} nodes a stage that all events pass sees up to {@code N} times as many. The
 * defaults assume the default rate of 20 per second and 30 nodes: p99.9 of all events needs at least 2.8 minutes, p99
 * of own events alone at least 8.3 minutes. A longer window costs no memory, but reacts more slowly.
 *
 * @param averageAndMaxEnabled    if true, the platform will collect and report the average and maximum delay of events
 *                                at each stage of the event pipeline
 * @param latencyEnabled          if true, the platform will collect and report percentiles and the maximum of the
 *                                delay of events at each stage of the event pipeline and of {@code eventLatency_R2C},
 *                                over a sliding window
 * @param latencyWindow           the length of the sliding window of stages that gossiped events pass and of
 *                                {@code eventLatency_R2C}, which report percentiles up to p99.9
 * @param selfEventsLatencyWindow the length of the sliding window of stages that only the node's self events pass,
 *                                which report percentiles up to p99
 */
@ConfigData("event.pipeline.metrics")
public record ConsensusLayerMetricsConfig(
        @ConfigProperty(defaultValue = "true") boolean averageAndMaxEnabled,
        @ConfigProperty(defaultValue = "true") boolean latencyEnabled,
        @ConfigProperty(defaultValue = "5m") Duration latencyWindow,
        @ConfigProperty(defaultValue = "10m") Duration selfEventsLatencyWindow) {}
