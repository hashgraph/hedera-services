/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform.prometheus;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * Configuration concerning the Prometheus endpoint.
 *
 * @param endpointEnabled
 *         Flag that is {@code true}, if the endpoint should be endpointEnabled, {@code false otherwise}.
 * @param endpointPortNumber
 *         Port of the Prometheus endpoint.
 * @param endpointMaxBacklogAllowed
 *         The maximum number of incoming TCP connections which the system will queue internally.
 *         May be {@code 1}, in which case a system default value is used.
 */
@ConfigData("prometheus")
public record PrometheusConfig(
        @ConfigProperty(defaultValue = "true") boolean endpointEnabled,
        @Min(0) @Max(65535) @ConfigProperty(defaultValue = "9999") int endpointPortNumber,
        @Min(0) @ConfigProperty(defaultValue = "1") int endpointMaxBacklogAllowed) {}
