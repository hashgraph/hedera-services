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

package com.swirlds.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.Test;

public class LogLevelTest {

    @Test
    void logLevelWithHandlerOverwrite() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.foo", "TRACE")
                .withValue("logging.level.com.bar", "TRACE")
                .withValue("logging.handler.HANDLER1.level.com.bar", "OFF")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // then
        assertThat(config.isEnabled("com.bar.some.Class", Level.INFO)).isFalse();
        assertThat(config.isEnabled("com.bar.some.Class", Level.ERROR)).isFalse();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE)).isTrue();
        assertThat(config.isEnabled("com.some.Class", Level.ERROR)).isTrue();
    }
}
