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

package com.swirlds.platform.components;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;

/**
 * A component that represents a core responsibility of the platform. Platform components interact with each other via
 * these formal interfaces.
 */
public interface PlatformComponent extends Startable, Stoppable {

    /**
     * Invoked when a fatal error has occurred. This method gives the component a chance to take any final actions
     * before operations are halted, such as cleaning up resources, writing data to disk, etc.
     */
    default void onFatalError() {}

    /**
     * {@inheritDoc}
     * <p>
     * Starts the platform component. This method must be called after the component construction. The component may not
     * be functional until {@link #start()} has completed. Components should not start threads outside of this method.
     */
    @Override
    default void start() {}

    /**
     * {@inheritDoc}
     * <p>
     * Stops any threads that were started by the component in {@link #start()}. Once stopped, components may not be
     * started again. This method is intended for use in unit testing where threads should not live beyond the scope of
     * the test.
     */
    @Override
    default void stop() {}
}
