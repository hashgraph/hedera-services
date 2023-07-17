package com.hedera.node.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class UtilsTest {
    @Test
    @DisplayName("networkProperties() returns expected properties")
    void networkProperties() {
        final var config = new TestConfigBuilder(false)
                .withConfigDataType(NoNetworkAnnotatedConfig.class)
                .withConfigDataType(MixedAnnotatedConfig.class)
                .getOrCreateConfig();

        final var propertyNames = Utils.networkProperties(config);
        assertThat(propertyNames).containsExactly("networkProperty");
    }
}
