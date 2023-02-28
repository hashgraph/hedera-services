/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import java.util.Objects;

/**
 * Basic implementation of {@link VirtualStateAccessor} which delegates to a {@link VirtualMapState}.
 * Initially this separation of interface and implementation class was essential to the design.
 * At this point, it is just useful for testing purposes.
 */
public final class VirtualStateAccessorImpl implements VirtualStateAccessor {
    /**
     * The state. Cannot be null.
     */
    private final VirtualMapState state;

    /**
     * Create a new {@link VirtualStateAccessorImpl}.
     *
     * @param state
     * 		The state. Cannot be null.
     */
    public VirtualStateAccessorImpl(VirtualMapState state) {
        this.state = Objects.requireNonNull(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLabel() {
        return state.getLabel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFirstLeafPath() {
        return state.getFirstLeafPath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastLeafPath() {
        return state.getLastLeafPath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFirstLeafPath(final long path) {
        state.setFirstLeafPath(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLastLeafPath(final long path) {
        state.setLastLeafPath(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        return state.getSize();
    }
}
