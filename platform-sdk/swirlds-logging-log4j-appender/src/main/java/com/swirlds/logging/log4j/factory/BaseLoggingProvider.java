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

package com.swirlds.logging.log4j.factory;

import com.google.auto.service.AutoService;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import org.apache.logging.log4j.spi.Provider;

/**
 * This class is a factory registering the {@link BaseLoggerContextFactory} as the provider for the Log4J2 logging system.
 * <p>
 * This will allow the {@link BaseLoggerContextFactory} to be used as backend for the slf4j api
 */
@AutoService(Provider.class)
public class BaseLoggingProvider extends Provider {
    /**
     * The priority of this provider.
     */
    private static final int PRIORITY = 15;
    /**
     * The version of the Log4J2 API that this provider supports.
     */
    private static final String SUPPORTED_VERSION = "2.6.0";
    /**
     * This is a base logger to force the initialisation of the logging system.
     */
    private static final Logger logger = Loggers.getLogger(BaseLoggingProvider.class);

    /**
     * Creates a new logging provider and logs to force the initialisation of the base logging system
     */
    public BaseLoggingProvider() {
        super(PRIORITY, SUPPORTED_VERSION, BaseLoggerContextFactory.class);
        logger.trace("Initialised the BaseLoggingProvider");
    }
}
