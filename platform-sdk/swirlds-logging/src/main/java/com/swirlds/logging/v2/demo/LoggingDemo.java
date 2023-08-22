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

package com.swirlds.logging.v2.demo;

import com.swirlds.base.context.Context;
import com.swirlds.logging.v2.Logger;
import com.swirlds.logging.v2.Loggers;
import java.util.concurrent.Executors;

public class LoggingDemo {

    public static void main(String[] args) throws Exception {
        Logger logger = Loggers.get(LoggingDemo.class);

        logger.info("Hello world!");
        logger.debug("Hello world!");
        logger.warn("Hello world!");
        logger.error("Hello world!");

        logger.info("Hello world!", new RuntimeException("OH NO!"));
        logger.debug("Hello world!", new RuntimeException("OH NO!"));
        logger.warn("Hello world!", new RuntimeException("OH NO!"));
        logger.error("Hello world!", new RuntimeException("OH NO!"));

        logger.info(() -> "Hello world!");
        logger.debug(() -> "Hello world!");
        logger.warn(() -> "Hello world!");
        logger.error(() -> "Hello world!");

        logger.info(() -> "Hello world!", new RuntimeException("OH NO!"));
        logger.debug(() -> "Hello world!", new RuntimeException("OH NO!"));
        logger.warn(() -> "Hello world!", new RuntimeException("OH NO!"));
        logger.error(() -> "Hello world!", new RuntimeException("OH NO!"));

        logger.info("Hello {}!", "world");
        logger.debug("Hello {}!", "world");
        logger.warn("Hello {}!", "world");
        logger.error("Hello {}!", "world");

        logger.info("Hello {}!", new RuntimeException("OH NO!"), "world");
        logger.debug("Hello {}!", new RuntimeException("OH NO!"), "world");
        logger.warn("Hello {}!", new RuntimeException("OH NO!"), "world");
        logger.error("Hello {}!", new RuntimeException("OH NO!"), "world");

        logger.withMarker("LOGGING_DEMO").info("Hello world!");
        logger.withMarker("LOGGING_DEMO").debug("Hello world!");
        logger.withMarker("LOGGING_DEMO").warn("Hello world!");
        logger.withMarker("LOGGING_DEMO").error("Hello world!");

        logger.withMarker("LOGGING_DEMO").info("Hello world!", new RuntimeException("OH NO!"));
        logger.withMarker("LOGGING_DEMO").debug("Hello world!", new RuntimeException("OH NO!"));
        logger.withMarker("LOGGING_DEMO").warn("Hello world!", new RuntimeException("OH NO!"));
        logger.withMarker("LOGGING_DEMO").error("Hello world!", new RuntimeException("OH NO!"));

        logger.withContext("context", "value").info("Hello world!");
        logger.withContext("context", "value").debug("Hello world!");
        logger.withContext("context", "value").warn("Hello world!");
        logger.withContext("context", "value").error("Hello world!");

        logger.withContext("context", "value").info("Hello world!", new RuntimeException("OH NO!"));
        logger.withContext("context", "value").debug("Hello world!", new RuntimeException("OH NO!"));
        logger.withContext("context", "value").warn("Hello world!", new RuntimeException("OH NO!"));
        logger.withContext("context", "value").error("Hello world!", new RuntimeException("OH NO!"));

        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .info("Hello world!");
        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .debug("Hello world!");
        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .warn("Hello world!");
        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .error("Hello world!");

        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .info("Hello world!", new RuntimeException("OH NO!"));
        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .debug("Hello world!", new RuntimeException("OH NO!"));
        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .warn("Hello world!", new RuntimeException("OH NO!"));
        logger.withContext("context1", "value1")
                .withContext("context2", "value2")
                .error("Hello world!", new RuntimeException("OH NO!"));

        logger.withMarker("LOGGING_DEMO").withContext("context", "value").info("Hello world!");
        logger.withMarker("LOGGING_DEMO").withContext("context", "value").debug("Hello world!");
        logger.withMarker("LOGGING_DEMO").withContext("context", "value").warn("Hello world!");
        logger.withMarker("LOGGING_DEMO").withContext("context", "value").error("Hello world!");

        logger.withMarker("LOGGING_DEMO")
                .withContext("context", "value")
                .info("Hello world!", new RuntimeException("OH NO!"));
        logger.withMarker("LOGGING_DEMO")
                .withContext("context", "value")
                .debug("Hello world!", new RuntimeException("OH NO!"));
        logger.withMarker("LOGGING_DEMO")
                .withContext("context", "value")
                .warn("Hello world!", new RuntimeException("OH NO!"));
        logger.withMarker("LOGGING_DEMO")
                .withContext("context", "value")
                .error("Hello world!", new RuntimeException("OH NO!"));

        Context.getGlobalContext().put("app", "demo");

        logger.info("Hello world!");
        logger.debug("Hello world!");
        logger.warn("Hello world!");
        logger.error("Hello world!");

        logger.withContext("context", "value").info("Hello world!");
        logger.withContext("context", "value").debug("Hello world!");
        logger.withContext("context", "value").warn("Hello world!");
        logger.withContext("context", "value").error("Hello world!");

        Context.getThreadLocalContext().put("transaction", "17");

        logger.info("Hello world!");
        logger.debug("Hello world!");
        logger.warn("Hello world!");
        logger.error("Hello world!");

        logger.withContext("context", "value").info("Hello world!");
        logger.withContext("context", "value").debug("Hello world!");
        logger.withContext("context", "value").warn("Hello world!");
        logger.withContext("context", "value").error("Hello world!");

        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    Context.getThreadLocalContext().put("transaction", "18");

                    logger.info("Hello world!");
                    logger.debug("Hello world!");
                    logger.warn("Hello world!");
                    logger.error("Hello world!");

                    logger.withContext("context", "value").info("Hello world!");
                    logger.withContext("context", "value").debug("Hello world!");
                    logger.withContext("context", "value").warn("Hello world!");
                    logger.withContext("context", "value").error("Hello world!");
                })
                .get();

        logger.info("Hello world!");
        logger.debug("Hello world!");
        logger.warn("Hello world!");
        logger.error("Hello world!");

        logger.withContext("context", "value").info("Hello world!");
        logger.withContext("context", "value").debug("Hello world!");
        logger.withContext("context", "value").warn("Hello world!");
        logger.withContext("context", "value").error("Hello world!");
    }
}
