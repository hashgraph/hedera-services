/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.extensions;

import java.io.CharArrayWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Helper class to register a started appender on a given {@code Logger}, and parse the events at
 * each log level as a {@code List<String>}.
 */
public class LogCaptor {
    private static final Pattern EVENT_PATTERN = Pattern.compile("(DEBUG|INFO|WARN|ERROR|$)");

    private static final String MINIMAL_PATTERN = "%-5level %msg";

    private final Logger logger;
    private final Appender appender;
    private final CharArrayWriter capture = new CharArrayWriter();

    public LogCaptor(org.apache.logging.log4j.Logger logger) {
        this.logger = (Logger) logger;

        appender =
                WriterAppender.newBuilder()
                        .setTarget(capture)
                        .setLayout(PatternLayout.newBuilder().withPattern(MINIMAL_PATTERN).build())
                        .setName("LogCaptor")
                        .build();

        appender.start();
        this.logger.addAppender(appender);
        this.logger.setLevel(Level.DEBUG);
    }

    public void stopCapture() {
        this.logger.removeAppender(appender);
    }

    public List<String> debugLogs() {
        return eventsAt("DEBUG");
    }

    public List<String> infoLogs() {
        return eventsAt("INFO");
    }

    public List<String> warnLogs() {
        return eventsAt("WARN");
    }

    public List<String> errorLogs() {
        return eventsAt("ERROR");
    }

    private List<String> eventsAt(String level) {
        var output = capture.toString();

        List<String> events = new ArrayList<>();
        var m = EVENT_PATTERN.matcher(output);
        String matchLevel = null;
        for (int i = -1; m.find(); ) {
            if (i != -1 && level.equals(matchLevel)) {
                events.add(output.substring(i, m.start()).trim());
            }
            i = m.end();
            matchLevel = m.group(0);
        }
        return events;
    }
}
