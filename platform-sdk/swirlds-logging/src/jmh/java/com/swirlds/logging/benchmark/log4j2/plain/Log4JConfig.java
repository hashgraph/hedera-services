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

package com.swirlds.logging.benchmark.log4j2.plain;

import com.swirlds.logging.benchmark.log4j2.Log4JBaseConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class Log4JConfig extends Log4JBaseConfig {
    @NonNull
    protected AppenderComponentBuilder createFileAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder, final @NonNull String path) {
        final LayoutComponentBuilder layoutBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(Log4JBaseConfig.FILE_APPENDER_NAME, "File")
                .addAttribute("fileName", path)
                .addAttribute("append", true)
                .add(layoutBuilder);
    }
}
