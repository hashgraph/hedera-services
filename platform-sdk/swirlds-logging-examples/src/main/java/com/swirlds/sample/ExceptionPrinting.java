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

package com.swirlds.sample;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.LoggingSystem;

public class ExceptionPrinting {

    private static void createDeepStackTrace(int levelsToGo, int throwModulo, String exceptionMessage) {
        if (levelsToGo <= 0) {
            throw new RuntimeException(exceptionMessage);
        } else {
            if (levelsToGo % throwModulo == 0) {
                try {
                    createDeepStackTrace(levelsToGo - 1, throwModulo, exceptionMessage);
                } catch (Exception e) {
                    throw new RuntimeException(exceptionMessage + "in level " + levelsToGo, e);
                }
            } else {
                createDeepStackTrace(levelsToGo - 1, throwModulo, exceptionMessage);
            }
        }
    }

    public static void main(String[] args) {
        try {
            createDeepStackTrace(100, 5, "This is a test exception");
        } catch (Exception e) {
            Configuration configuration = ConfigurationBuilder.create().build();
            LoggingSystem loggingSystem = new LoggingSystem(configuration);
            loggingSystem.addHandler(new LogHandler() {
                @Override
                public void accept(LogEvent logEvent) {
                    // NOOP
                }
            });
            Logger logger = loggingSystem.getLogger(ExceptionPrinting.class.getSimpleName());
            while (true) {
                logger.error("Exception caught", e);
            }
        }
    }
}
