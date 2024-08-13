package com.swirlds.platform.event.hashing;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.platform.config.SemanticVersionConverter;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.state.spi.HapiUtils;

public class HashingMigrationUtils {
    private static final SemanticVersionConverter converter = new SemanticVersionConverter();

    public static CombinedEventHasher getEventHasher(final EventConfig eventConfig,
            final SemanticVersion currentSoftwareVersion) {
        return getEventHasher(
                convertMigrationVersion(eventConfig),
                currentSoftwareVersion
        );
    }

    public static SemanticVersion convertMigrationVersion(final EventConfig eventConfig) {
        final String migrationVersionString = eventConfig.hashingMigrationVersion().trim();
        if (migrationVersionString.isEmpty()) {
            // no migration version set, use the old event hashing algorithm
            return null;
        }
        return converter.convert(migrationVersionString);
    }

    public static CombinedEventHasher getEventHasher(final SemanticVersion migrationVersion,
            final SemanticVersion currentSoftwareVersion) {
        if (migrationVersion == null) {
            // no migration version set, use the old event hashing algorithm
            return new StatefulEventHasher();
        }
        return HapiUtils.SEMANTIC_VERSION_COMPARATOR.compare(currentSoftwareVersion, migrationVersion) >= 0
                ? new PbjHasher()
                : new StatefulEventHasher();
    }
}
