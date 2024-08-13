/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import com.swirlds.platform.config.SemanticVersionConverter;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.state.spi.HapiUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Utility class for handling event hashing migration
 */
public final class HashingMigrationUtils {
    /** Converts a string to a SemanticVersion */
    private static final SemanticVersionConverter VERSION_CONVERTER = new SemanticVersionConverter();

    private HashingMigrationUtils() {
        // prevent instantiation
    }

    /**
     * Create an appropriate {@link UnsignedEventHasher} for new events that are created depending on the configuration
     * and the current software version.
     *
     * @param eventConfig            the event configuration
     * @param currentSoftwareVersion the current software version
     * @return a new {@link UnsignedEventHasher}
     */
    public static @NonNull UnsignedEventHasher getUnsignedEventHasher(
            @NonNull final EventConfig eventConfig, @NonNull final SemanticVersion currentSoftwareVersion) {
        return getEventHasher(convertMigrationVersion(eventConfig), currentSoftwareVersion);
    }

    /**
     * Converts the migration version string from the event configuration to a {@link SemanticVersion}. If the migration
     * version is empty, returns null.
     *
     * @param eventConfig the event configuration
     * @return the migration version as a {@link SemanticVersion} or null if not set
     */
    public static @Nullable SemanticVersion convertMigrationVersion(@NonNull final EventConfig eventConfig) {
        final String migrationVersionString =
                eventConfig.hashingMigrationVersion().trim();
        if (migrationVersionString.isEmpty()) {
            // no migration version set, use the old event hashing algorithm
            return null;
        }
        return VERSION_CONVERTER.convert(migrationVersionString);
    }

    /**
     * Get the appropriate {@link CombinedEventHasher} for the given migration version and event version.
     *
     * @param migrationVersion the migration version
     * @param eventVersion     the event version
     * @return a new {@link CombinedEventHasher}
     */
    public static @NonNull CombinedEventHasher getEventHasher(
            @Nullable final SemanticVersion migrationVersion, @NonNull final SemanticVersion eventVersion) {
        if (migrationVersion == null) {
            // no migration version set, use the old event hashing algorithm
            return new StatefulEventHasher();
        }
        return HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(eventVersion, migrationVersion) >= 0
                ? new PbjHasher()
                : new StatefulEventHasher();
    }
}
