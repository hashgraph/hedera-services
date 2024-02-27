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

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.console.ConsoleHandler;
import com.swirlds.logging.file.FileHandlerFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Threads(5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
public class LoggingBenchmark {

    private static final String MESSAGE = "This is a simple log message";

    private static final String MESSAGE_WITH_PLACEHOLDER = "This is a {} log message";

    private static final String MESSAGE_WITH_MANY_PLACEHOLDERS =
            "This is a {} log message that counts up: one, {},{},{},{},{},{},{},{},{},{},{}";

    private static final String PLACEHOLDER_1 = "combined";

    private static final String PLACEHOLDER_2 = "two";

    private static final String PLACEHOLDER_3 = "three";

    private static final String PLACEHOLDER_4 = "four";

    private static final String PLACEHOLDER_5 = "five";

    private static final String PLACEHOLDER_6 = "six";

    private static final String PLACEHOLDER_7 = "seven";

    private static final String PLACEHOLDER_8 = "eight";

    private static final String PLACEHOLDER_9 = "nine";

    private static final String PLACEHOLDER_10 = "ten";

    private static final String PLACEHOLDER_11 = "eleven";

    private static final String PLACEHOLDER_12 = "twelve";

    private static final String EXCEPTION_MESSAGE = "Error while doing something";

    private static final String LONG_MESSAGE =
            """
            Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.
            Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.
            Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.
            Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.
            Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis.
            At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, At accusam aliquyam diam diam dolore dolores duo eirmod eos erat, et nonumy sed tempor et et invidunt justo labore Stet clita ea et gubergren, kasd magna no rebum. sanctus sea sed takimata ut vero voluptua. est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat.
            Consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus.
            Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.
            Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.
            Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.
            Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo""";

    private static final String MARKER_1 = "MARKER_1";

    private static final String MARKER_2 = "MARKER_2";

    private static final String MARKER_3 = "MARKER_3";

    private static final String CONTEXT_1_KEY = "name";

    private static final String CONTEXT_1_VALUE = "benchmark";

    private static final String CONTEXT_2_KEY = "type";

    private static final String CONTEXT_2_VALUE = "jmh";

    private static final String CONTEXT_3_KEY = "state";

    private static final String CONTEXT_3_VALUE = "running";

    private static void createDeepStackTrace(int levelsToGo, String exceptionMessage) {
        if (levelsToGo <= 0) {
            throw new RuntimeException(exceptionMessage);
        } else {
            createDeepStackTrace(levelsToGo - 1, exceptionMessage);
        }
    }

    private static void createRecursiveDeepStackTrace(int levelsToGo, int throwModulo, String exceptionMessage) {
        if (levelsToGo <= 0) {
            throw new RuntimeException(exceptionMessage);
        } else {
            if (levelsToGo % throwModulo == 0) {
                try {
                    createRecursiveDeepStackTrace(levelsToGo - 1, throwModulo, exceptionMessage);
                } catch (Exception e) {
                    throw new RuntimeException(exceptionMessage + "in level " + levelsToGo, e);
                }
            } else {
                createRecursiveDeepStackTrace(levelsToGo - 1, throwModulo, exceptionMessage);
            }
        }
    }

    private Logger logger;

    private Exception exceptionWithNormalStackTrace;

    private Exception exceptionWithNormalStackTraceAndLongMessage;

    private Exception exceptionWithDeepStackTrace;

    private Exception exceptionWithDeepStackTraceAndDeepCause;

    @Param({"ONLY_EMERGENCY", "CONSOLE_HANDLER", "NOOP_HANDLER", "LEVEL_OFF", "FILE_HANDLER"})
    public String setup;

    @Setup(Level.Iteration)
    public void setup() throws IOException, URISyntaxException {
        final LoggingSystem loggingSystem;
        if (Objects.equals(setup, "ONLY_EMERGENCY")) {
            final Configuration configuration = ConfigurationBuilder.create()
                    .withConverter(new ConfigLevelConverter())
                    .withConverter(new MarkerStateConverter())
                    .withValue("logging.level", "trace")
                    .build();
            loggingSystem = new LoggingSystem(configuration);
        } else if (Objects.equals(setup, "CONSOLE_HANDLER")) {
            final Configuration configuration = ConfigurationBuilder.create()
                    .withConverter(new ConfigLevelConverter())
                    .withConverter(new MarkerStateConverter())
                    .withValue("logging.level", "trace")
                    .withValue("logging.handler.console.type", "console")
                    .withValue("logging.handler.console.active", "true")
                    .withValue("logging.handler.console.level", "trace")
                    .build();
            loggingSystem = new LoggingSystem(configuration);
            loggingSystem.addHandler(new ConsoleHandler("console", configuration));
        } else if (Objects.equals(setup, "NOOP_HANDLER")) {
            final Configuration configuration = ConfigurationBuilder.create()
                    .withConverter(new ConfigLevelConverter())
                    .withConverter(new MarkerStateConverter())
                    .withValue("logging.level", "trace")
                    .build();
            loggingSystem = new LoggingSystem(configuration);
            loggingSystem.addHandler(logEvent -> {
                // NOOP
            });
        } else if (Objects.equals(setup, "FILE_HANDLER")) {
            final Configuration configuration = ConfigurationBuilder.create()
                    .withConverter(new ConfigLevelConverter())
                    .withConverter(new MarkerStateConverter())
                    .withValue("logging.level", "trace")
                    .withValue("logging.handler.file.type", "file")
                    .withValue("logging.handler.file.active", "true")
                    .withValue("logging.handler.file.level", "trace")
                    .withValue("logging.handler.file.file", "benchmark.log")
                    .build();
            final LogHandler fileHandler = new FileHandlerFactory().create("file", configuration);
            loggingSystem = new LoggingSystem(configuration);
            loggingSystem.addHandler(fileHandler);
        } else {
            final Configuration configuration = ConfigurationBuilder.create()
                    .withConverter(new ConfigLevelConverter())
                    .withConverter(new MarkerStateConverter())
                    .withValue("logging.level", "off")
                    .build();
            loggingSystem = new LoggingSystem(configuration);
        }
        logger = loggingSystem.getLogger(LoggingBenchmark.class.getName() + "." + setup.substring(0, 9));
        exceptionWithNormalStackTrace = new RuntimeException(EXCEPTION_MESSAGE);
        exceptionWithNormalStackTraceAndLongMessage = new RuntimeException(LONG_MESSAGE);
        try {
            createDeepStackTrace(200, EXCEPTION_MESSAGE);
        } catch (final RuntimeException e) {
            exceptionWithDeepStackTrace = e;
        }
        try {
            createRecursiveDeepStackTrace(200, 10, EXCEPTION_MESSAGE);
        } catch (final RuntimeException e) {
            exceptionWithDeepStackTraceAndDeepCause = e;
        }
    }

    
    public void executeSimpleLog() {
        logger.info(MESSAGE);
    }

    
    public void executeSimpleLogWithMarker() {
        logger.withMarker(MARKER_1).info(MESSAGE);
    }

    
    public void executeSimpleLogWithMultipleMarkers() {
        logger.withMarker(MARKER_1).withMarker(MARKER_2).withMarker(MARKER_3).info(MESSAGE);
    }

    
    public void executeSimpleLogWithLongMessage() {
        logger.info(LONG_MESSAGE);
    }

    
    public void executeSimpleLogWithException() {
        logger.info(MESSAGE, exceptionWithNormalStackTrace);
    }

    
    public void executeSimpleLogWithExceptionWithLongMessage() {
        logger.info(MESSAGE, exceptionWithNormalStackTraceAndLongMessage);
    }

    
    public void executeSimpleLogWithExceptionWithDeepStackTrace() {
        logger.info(MESSAGE, exceptionWithDeepStackTrace);
    }

    
    public void executeSimpleLogWithExceptionWithDeepStackTraceAndDeepCause() {
        logger.info(MESSAGE, exceptionWithDeepStackTraceAndDeepCause);
    }

    
    public void executeSimpleLogWithMessageWithPlaceholder() {
        logger.info(MESSAGE_WITH_PLACEHOLDER, PLACEHOLDER_1);
    }

    
    public void executeSimpleLogWithMessageWithMultiplePlaceholders() {
        logger.info(
                MESSAGE_WITH_MANY_PLACEHOLDERS,
                PLACEHOLDER_1,
                PLACEHOLDER_2,
                PLACEHOLDER_3,
                PLACEHOLDER_4,
                PLACEHOLDER_5,
                PLACEHOLDER_6,
                PLACEHOLDER_7,
                PLACEHOLDER_8,
                PLACEHOLDER_9,
                PLACEHOLDER_10,
                PLACEHOLDER_11,
                PLACEHOLDER_12);
    }

    
    public void executeSimpleLogWithContextValue() {
        logger.withContext(CONTEXT_1_KEY, CONTEXT_1_VALUE).info(MESSAGE);
    }

    
    public void executeSimpleLogWithMultiplyContextValues() {
        logger.withContext(CONTEXT_1_KEY, CONTEXT_1_VALUE)
                .withContext(CONTEXT_2_KEY, CONTEXT_3_VALUE)
                .withContext(CONTEXT_3_KEY, CONTEXT_3_VALUE)
                .info(MESSAGE);
    }
}
