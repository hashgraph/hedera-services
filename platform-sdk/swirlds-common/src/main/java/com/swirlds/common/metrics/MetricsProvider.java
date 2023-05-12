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

package com.swirlds.common.metrics;

import com.swirlds.common.system.NodeId;

/**
 * An implementation of this class is responsible for creating {@link Metrics}-implementations.
 * <p>
 * The platform provides (at least) one default implementation, but if application developers want to use their
 * own implementations of {@code Metrics}, they have to set up their own provider.
 */
public interface MetricsProvider {

    /**
     * Creates the global {@link Metrics}-instance, that keeps global metrics.
     * <p>
     * During normal execution, there will be only one global {@code Metrics}, which will be shared between
     * all platforms. Accordingly, this method will be called only once.
     *
     * @return the new instance of {@code Metrics}
     */
    Metrics createGlobalMetrics();

    /**
     * Creates a platform-specific {@link Metrics}-instance.
     *
     * @param selfId
     * 		the {@link NodeId} of the platform
     * @return the new instance of {@code Metrics}
     */
    Metrics createPlatformMetrics(NodeId selfId);
}
