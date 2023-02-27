/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Utility methods for bootstrapping log4j.
 */
public final class Log4jSetup {

    private Log4jSetup() {}

    /**
     * Start log4l.
     *
     * @param configPath
     * 		the path to the log4j configuration file. If path does not exist then this method is a no-op.
     */
    public static void startLoggingFramework(final Path configPath) {
        if (Files.exists(configPath)) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.setConfigLocation(configPath.toUri());
        }
    }
}
