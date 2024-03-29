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

package com.swirlds.platform.test.chatter.network.framework;

/**
 * A class that creates simulated events.
 *
 * @param <T>
 */
public interface SimulatedEventCreator<T extends SimulatedChatterEvent> extends NodeConfigurable {

    /**
     * Creates and returns a new event, or {@code null} if no event should be created.
     *
     * @return the event, or {@code null} if no event should be created.
     */
    T maybeCreateEvent();
}
