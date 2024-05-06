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

package com.swirlds.logging.benchmark.log4j2;

import static com.swirlds.logging.benchmark.config.Constants.CONSOLE_AND_FILE_TYPE;
import static com.swirlds.logging.benchmark.config.Constants.CONSOLE_TYPE;
import static com.swirlds.logging.benchmark.config.Constants.FILE_TYPE;
import static com.swirlds.logging.benchmark.config.Constants.MODE_NOT_ROLLING;
import static com.swirlds.logging.benchmark.config.Constants.MODE_ROLLING;

import com.swirlds.logging.benchmark.config.LoggingBenchmarkConfig;
import com.swirlds.logging.benchmark.util.ConfigManagement;
import com.swirlds.logging.benchmark.util.LogFiles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public abstract class AbstractLog4JBenchmark {

    @Param({CONSOLE_TYPE, FILE_TYPE, CONSOLE_AND_FILE_TYPE})
    public String loggingType;

    @Param({MODE_NOT_ROLLING, MODE_ROLLING})
    public String mode;

    @Param({"true", "false"})
    public String forwardToSwirldsLogging;

    protected Logger logger;

    protected final Log4JLoggerFactory factory = new Log4JLoggerFactory();

    @Setup(Level.Trial)
    public void init() {
        final LoggingBenchmarkConfig config =
                LoggingBenchmarkConfig.createFromStrings(loggingType, mode, forwardToSwirldsLogging);
        logger = factory.createLogger(config);
    }

    protected abstract void additionalInitialization(Logger logger);

    @TearDown(Level.Trial)
    public void tearDown() {
        LogManager.shutdown();
        if (ConfigManagement.deleteOutputFolder()) {
            LogFiles.tryDeleteDirAndContent();
        }
    }
}
