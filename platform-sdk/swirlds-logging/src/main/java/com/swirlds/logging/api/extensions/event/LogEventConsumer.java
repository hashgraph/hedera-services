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

package com.swirlds.logging.api.extensions.event;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A consumer that consumes log events.
 */
public interface LogEventConsumer {

    /**
     * Checks if the consumer is enabled for the given name and level.
     *
     * @param name  the name
     * @param level the level
     * @return true if the consumer is enabled, false otherwise
     */
    default boolean isEnabled(@NonNull String name, @NonNull Level level, @Nullable Marker marker) {
        return true;
    }

    void accept(@NonNull LogEvent event);
}
