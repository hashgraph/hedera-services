// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.metrics;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for platform specific metrics
 *
 * @param eventPipelineMetricsEnabled        if true, the platform will collect and report the average and maximum
 *                                           delay of events at each stage of the event pipeline
 * @param eventPipelineLatencyMetricsEnabled if true, the platform will collect and report percentiles and the maximum
 *                                           of the delay of events at each stage of the event pipeline, over a sliding
 *                                           window
 */
@ConfigData("platform.metrics")
public record PlatformMetricsConfig(
        @ConfigProperty(defaultValue = "true") boolean eventPipelineMetricsEnabled,
        @ConfigProperty(defaultValue = "true") boolean eventPipelineLatencyMetricsEnabled) {}
