/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A {@link Metrics} implementation that knows about the platform. This interface has been extracted from the
 * {@link Metrics} interface to have a platform independent interface as a base.
 *
 * @deprecated Looks like this interface is only used internally and therefore could be removed. That need to be double
 * checked and discussed before it is marked as {@link Deprecated#forRemoval()}.
 */
@Deprecated
public interface PlatformMetrics extends Metrics {

    /**
     * Returns the {@link NodeId} which metrics this {@code Metrics} manages. If this {@code Metrics} manages the global
     * metrics, this method returns {@code null}.
     *
     * @return The {@code NodeId} or {@code null}
     */
    @Nullable
    NodeId getNodeId();

    /**
     * Checks if this {@code Metrics} manages global metrics.
     *
     * @return {@code true} if this {@code Metrics} manages global metrics, {@code false} otherwise
     */
    default boolean isGlobalMetrics() {
        return getNodeId() == null;
    }

    /**
     * Checks if this {@code Metrics} manages platform metrics.
     *
     * @return {@code true} if this {@code Metrics} manages platform metrics, {@code false} otherwise
     */
    default boolean isPlatformMetrics() {
        return getNodeId() != null;
    }
}
