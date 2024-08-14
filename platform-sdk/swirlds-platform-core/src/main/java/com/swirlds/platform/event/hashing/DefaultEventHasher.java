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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * Default implementation of the {@link EventHasher}.
 */
public class DefaultEventHasher implements EventHasher {
    private final SemanticVersion migrationVersion;

    /**
     * Constructs a new {@link DefaultEventHasher} with the given {@link SemanticVersion}.
     *
     * @param migrationVersion the version at which events should start being hashed by the new algorithm, all events
     *                         prior to this version will be hashed by the old algorithm. if null, no hashing migration
     *                         will occur
     */
    public DefaultEventHasher(@Nullable final SemanticVersion migrationVersion) {
        this.migrationVersion = migrationVersion;
    }

    @Override
    @NonNull
    public PlatformEvent hashEvent(@NonNull final PlatformEvent event) {
        Objects.requireNonNull(event);
        return HashingMigrationUtils.getEventHasher(migrationVersion, event.getSoftwareVersion())
                .hashEvent(event);
    }
}
