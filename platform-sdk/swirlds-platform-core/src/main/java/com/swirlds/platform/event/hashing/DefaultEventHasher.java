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

package com.swirlds.platform.event.hashing;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.EventConfig.EventHashingMode;
import com.swirlds.state.spi.HapiUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Default implementation of the {@link EventHasher}.
 */
public class DefaultEventHasher implements EventHasher {
    private final SemanticVersion currentSoftwareVersion;
    private final EventConfig.EventHashingMode hashingMode;

    /**
     * Constructs a new {@link DefaultEventHasher} with the given {@link SemanticVersion} and migration flag.
     *
     * @param currentSoftwareVersion the current software version
     * @param hashingMode    the hashing mode to use
     */
    public DefaultEventHasher(
            @NonNull final SemanticVersion currentSoftwareVersion, final EventConfig.EventHashingMode hashingMode) {
        this.currentSoftwareVersion = Objects.requireNonNull(currentSoftwareVersion);
        this.hashingMode = hashingMode;
    }

    @Override
    @NonNull
    public PlatformEvent hashEvent(@NonNull final PlatformEvent event) {
        Objects.requireNonNull(event);
        if (hashingMode == EventHashingMode.NEW ||
                (hashingMode == EventHashingMode.MIGRATE
                        && HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(currentSoftwareVersion,
                        event.getSoftwareVersion())
                        == 0)) {
            new PbjHasher().hashEvent(event);
            return event;
        }
        new StatefulEventHasher().hashEvent(event);
        return event;
    }
}
