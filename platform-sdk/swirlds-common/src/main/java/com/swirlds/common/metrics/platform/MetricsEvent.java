/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.system.NodeId;

public record MetricsEvent(Type type, NodeId nodeId, Metric metric) {
    public enum Type {
        ADDED,
        REMOVED
    }

    public MetricsEvent {
        throwArgNull(type, "type");
        throwArgNull(metric, "metric");
    }
}
