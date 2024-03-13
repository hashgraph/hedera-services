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

package com.swirlds.logging.log4j.appender;

import com.google.auto.service.AutoService;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.provider.LogProvider;
import com.swirlds.logging.api.extensions.provider.LogProviderFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class is a factory for creating a Log4JProvider to install swirlds-logging to {@link SwirldsLogAppender}.
 * <p>
 * Please note that the {@code SwirldsLogAppender} only works if the log4j2 configuration is set to use the
 * {@code SwirldsLogAppender} as the appender for the root logger.
 *
 * @see SwirldsLogAppender
 * @see Log4JProvider
 * @see LogProvider
 */
@AutoService(LogProviderFactory.class)
public class Log4JProviderFactory implements LogProviderFactory {
    /**
     * Creates a new instance of the Log4JProvider.
     *
     * @param configuration the configuration to use
     * @return a new instance of the Log4JProvider
     */
    @NonNull
    @Override
    public LogProvider create(@NonNull final Configuration configuration) {
        return new Log4JProvider(configuration);
    }
}
