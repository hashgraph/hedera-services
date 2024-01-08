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

package com.swirlds.logging;

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
import com.swirlds.base.test.fixtures.concurrent.WithTestExecutor;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.api.internal.level.ConfigLevel;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.logging.api.internal.level.MarkerState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

@WithTestExecutor
public class HandlerLoggingLevelConfigStressTest {

    @Inject
    TestExecutor testExecutor;

    private static Runnable createRunnable(final HandlerLoggingLevelConfig config) {
        return () -> {
            for (int i = 0; i < 20; i++) {
                // update config
                final Configuration configuration = defaultConfigBuilder()
                        .withValue("logging.level", Level.WARN)
                        .withValue("logging.level.com.sample", Level.INFO)
                        .withValue("logging.level.com.sample.package", Level.ERROR)
                        .withValue("logging.level.com.sample.package.Class", Level.TRACE)
                        .getOrCreateConfig();
                config.update(configuration);

                // do some checks
                int count = (int) (Math.random() * 20.0d);
                for (int j = 0; j < count; j++) {
                    config.isEnabled("", Level.ERROR, null);
                    config.isEnabled("", Level.WARN, null);
                    config.isEnabled("", Level.INFO, null);
                    config.isEnabled("", Level.DEBUG, null);
                    config.isEnabled("", Level.TRACE, null);
                    config.isEnabled("", Level.ERROR, null);
                    config.isEnabled("a", Level.WARN, null);
                    config.isEnabled("b", Level.INFO, null);
                    config.isEnabled("c", Level.DEBUG, null);
                    config.isEnabled("d", Level.TRACE, null);
                    config.isEnabled("other", Level.ERROR, null);
                    config.isEnabled("other.a", Level.WARN, null);
                    config.isEnabled("other.b", Level.INFO, null);
                    config.isEnabled("other.c", Level.DEBUG, null);
                    config.isEnabled("other.d", Level.TRACE, null);
                    config.isEnabled("com.sample.package.Class", Level.ERROR, null);
                    config.isEnabled("com.sample.package.Class", Level.WARN, null);
                    config.isEnabled("com.sample.package.Class", Level.INFO, null);
                    config.isEnabled("com.sample.package.Class", Level.DEBUG, null);
                    config.isEnabled("com.sample.package.Class", Level.TRACE, null);
                    config.isEnabled("com.sample.package.Class" + j, Level.ERROR, null);
                    config.isEnabled("com.sample.package.Class" + j, Level.WARN, null);
                    config.isEnabled("com.sample.package.Class" + j, Level.INFO, null);
                    config.isEnabled("com.sample.package.Class" + j, Level.DEBUG, null);
                    config.isEnabled("com.sample.package.Class" + j, Level.TRACE, null);
                }

                try {
                    Thread.sleep((long) (Math.random() * 20.0d));
                } catch (InterruptedException e) {
                    throw new RuntimeException("INTERRUPT!", e);
                }
            }
        };
    }

    @Test
    void testWithConfig() {
        // given
        final Configuration configuration = defaultConfigBuilder().getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration);
        final List<Runnable> runnables =
                IntStream.range(0, 20).mapToObj(i -> createRunnable(config)).toList();
        testExecutor.executeAndWait(runnables);
    }

    private static TestConfigBuilder defaultConfigBuilder() {
        return new TestConfigBuilder()
                .withConverter(ConfigLevel.class, new ConfigLevelConverter())
                .withConverter(MarkerState.class, new MarkerStateConverter());
    }
}
