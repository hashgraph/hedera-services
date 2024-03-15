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

package com.swirlds.logging.benchmark.bridged;

import com.swirlds.logging.benchmark.log4j2.Log4JLoggingBenchmarkConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.spi.LoggerContext;

public class BridgedConfiguration extends Log4JLoggingBenchmarkConfig {
    @NonNull
    public LoggerContext configureBridgedLogging() {
        System.clearProperty("log4j2.contextSelector");
        PluginManager.addPackage("com.swirlds.logging.log4j.appender");
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.DEBUG);
        builder.setConfigurationName("bridgeLoggingConfig");
        final AppenderComponentBuilder appenderBuilder =
                builder.newAppender(Log4JLoggingBenchmarkConfig.BRIDGE_APPENDER_NAME, "SwirldsLoggingAppender");
        builder.add(appenderBuilder);
        builder.add(builder.newRootLogger(Level.ALL)
                .add(builder.newAppenderRef(Log4JLoggingBenchmarkConfig.BRIDGE_APPENDER_NAME)));
        return create(builder);
    }
}
