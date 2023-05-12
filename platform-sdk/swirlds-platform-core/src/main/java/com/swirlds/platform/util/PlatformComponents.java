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

package com.swirlds.platform.util;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.base.state.Mutable;
import com.swirlds.base.state.Startable;
import com.swirlds.platform.dispatch.DispatchBuilder;
import java.util.LinkedList;
import java.util.List;

/**
 * A helper class for wiring platform components together.
 */
public class PlatformComponents implements Mutable, Startable {

    private final List<Object> components = new LinkedList<>();
    private final DispatchBuilder dispatchBuilder;

    private boolean immutable = false;

    /**
     * Create a new container for platform components.
     *
     * @param dispatchBuilder
     * 		the dispatch builder used by this platform instance.
     */
    public PlatformComponents(final DispatchBuilder dispatchBuilder) {
        this.dispatchBuilder = dispatchBuilder;
    }

    /**
     * Add a platform component that needs to be wired and/or started.
     *
     * @param component
     * 		the component
     * @param <T>
     * 		the type of the component
     * @return the component
     */
    public <T> T add(final T component) {
        throwIfImmutable();
        throwArgNull(component, "component");
        components.add(component);
        dispatchBuilder.registerObservers(component);
        return component;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfImmutable();
        immutable = true;
        dispatchBuilder.start();
        for (final Object component : components) {
            if (component instanceof final Startable startable) {
                startable.start();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return immutable;
    }
}
