/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.simulated.config;

import com.swirlds.platform.test.simulated.Latency;
import java.time.Duration;

/**
 * A builder for {@link NodeConfig}
 */
public class NodeConfigBuilder {
    private Duration createEventEvery = Duration.ofMillis(20);
    private Latency customLatency = new Latency(Duration.ZERO);
    private long customBandwidth = -1;
    private Duration intakeQueueDelay = Duration.ZERO;

    private NodeConfigBuilder() {}

    public static NodeConfigBuilder builder() {
        return new NodeConfigBuilder();
    }

    public static NodeConfigBuilder builder(final NodeConfig config) {
        final NodeConfigBuilder builder = builder();
        builder.setCustomLatency(config.customLatency());
        builder.setCreateEventEvery(config.createEventEvery());
        builder.setCustomBandwidth(config.customBandwidth());
        builder.setIntakeQueueDelay(config.intakeQueueDelay());
        return builder;
    }

    public NodeConfigBuilder setCreateEventEvery(final Duration createEventEvery) {
        this.createEventEvery = createEventEvery;
        return this;
    }

    public NodeConfigBuilder setCustomLatency(final Latency customLatency) {
        this.customLatency = customLatency;
        return this;
    }

    public NodeConfigBuilder setCustomBandwidth(final long customBandwidth) {
        this.customBandwidth = customBandwidth;
        return this;
    }

    public NodeConfigBuilder setIntakeQueueDelay(final Duration intakeQueueDelay) {
        this.intakeQueueDelay = intakeQueueDelay;
        return this;
    }

    public NodeConfig build() {
        return new NodeConfig(createEventEvery, customLatency, intakeQueueDelay, customBandwidth);
    }
}
