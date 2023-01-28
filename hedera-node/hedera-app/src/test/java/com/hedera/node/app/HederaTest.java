package com.hedera.node.app;

import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaTest {
    private static final SemanticVersion CURRENT_VERSION = SemanticVersion.newBuilder()
            .setMinor(34)
            .build();

    @Test
    void canRegisterServiceSchemas() {
        Hedera.registerServiceSchemasForMigration(CURRENT_VERSION);
    }
}