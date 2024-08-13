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
import com.swirlds.base.time.Time;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.config.SemanticVersionConverter;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.state.spi.HapiUtils;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HashingMigrationUtils {
    private static final Logger logger = LogManager.getLogger(HashingMigrationUtils.class);
    private static final RateLimitedLogger tellMe =
            new RateLimitedLogger(logger, Time.getCurrent(), Duration.ofSeconds(1));
    private static final SemanticVersionConverter converter = new SemanticVersionConverter();

    public static UnsignedEventHasher getUnsignedEventHasher(
            final EventConfig eventConfig, final SemanticVersion currentSoftwareVersion) {
        return getEventHasher(convertMigrationVersion(eventConfig), currentSoftwareVersion);
    }

    public static SemanticVersion convertMigrationVersion(final EventConfig eventConfig) {
        final String migrationVersionString =
                eventConfig.hashingMigrationVersion().trim();
        if (migrationVersionString.isEmpty()) {
            // no migration version set, use the old event hashing algorithm
            return null;
        }
        return converter.convert(migrationVersionString);
    }

    public static CombinedEventHasher getEventHasher(
            final SemanticVersion migrationVersion, final SemanticVersion eventVersion) {
        if (migrationVersion == null) {
            // no migration version set, use the old event hashing algorithm
            return new StatefulEventHasher();
        }
        //        logger.error(
        //                LogMarker.EXCEPTION.getMarker(),
        //                """
        //                        Event version {}
        //                        Migration version {}
        //                        Comparison {}
        //                        """,
        //                eventVersion,
        //                migrationVersion,
        //                HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(eventVersion, migrationVersion)
        //        );
        return HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(eventVersion, migrationVersion) >= 0
                ? new PbjHasher()
                : new StatefulEventHasher();
    }

    public static void tellMe(final SemanticVersion migrationVersion, final SemanticVersion eventVersion) {
        System.out.printf("Current version%n %s %n", eventVersion);
        System.out.printf("Migration version%n %s %n", migrationVersion);
        System.out.printf(
                "Comparison%n %d %n", HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(eventVersion, migrationVersion));
    }
}
