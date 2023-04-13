package com.hedera.node.app.service.mono.state.migration;

import java.time.Instant;

public interface MigrationManager {
    void publishMigrationRecords(Instant now);
}
