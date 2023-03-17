/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform.prometheus;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.common.config.sources.PropertyFileConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing PrometheusConfig")
class PrometheusConfigTest {

    static final String DEFAULT_PROMETHEUS_ENDPOINT_ENABLED = "false";
    static final String DEFAULT_PROMETHEUS_ENDPOINT_PORT_NUMBER = "9999";
    static final String DEFAULT_PROMETHEUS_ENDPOINT_MAX_BACKLOG_ALLOWED = "1";

    @Test
    @DisplayName("Testing default prometheus configuration")
    void testDefaultPrometheusConfig() {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final PrometheusConfig prometheusConfig = configuration.getConfigData(PrometheusConfig.class);

        assertThat(prometheusConfig).isNotNull();
        assertThat(prometheusConfig.prometheusEndpointEnabled())
                .isEqualTo(Boolean.valueOf(DEFAULT_PROMETHEUS_ENDPOINT_ENABLED));
        assertThat(prometheusConfig.prometheusEndpointPortNumber())
                .isEqualTo(Integer.valueOf(DEFAULT_PROMETHEUS_ENDPOINT_PORT_NUMBER));
        assertThat(prometheusConfig.prometheusEndpointMaxBacklogAllowed())
                .isEqualTo(Integer.valueOf(DEFAULT_PROMETHEUS_ENDPOINT_MAX_BACKLOG_ALLOWED));
    }

    @Test
    @DisplayName("Testing custom prometheus configuration")
    void testCustomPrometheusConfig() throws Exception {
        final Path configFile = Paths.get(Objects.requireNonNull(
                        PrometheusConfigTest.class.getClassLoader().getResource("metrics-test.properties"))
                .toURI());
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new PropertyFileConfigSource(configFile))
                .getOrCreateConfig();
        final PrometheusConfig prometheusConfig = configuration.getConfigData(PrometheusConfig.class);

        assertThat(prometheusConfig).isNotNull();
        assertThat(prometheusConfig.prometheusEndpointEnabled()).isTrue();
        assertThat(prometheusConfig.prometheusEndpointPortNumber()).isEqualTo(9999);
        assertThat(prometheusConfig.prometheusEndpointMaxBacklogAllowed()).isEqualTo(2);
    }
}
