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

package com.hedera.node.app.spi.fixtures.util;

import java.io.CharArrayWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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

        appender = WriterAppender.newBuilder()
                .setTarget(capture)
                .setLayout(
                        PatternLayout.newBuilder().withPattern(MINIMAL_PATTERN).build())
                .setName("LogCaptor")
                .build();

        appender.start();
        this.logger.addAppender(appender);
        this.logger.setLevel(Level.DEBUG);
    }

    public void clear() {
        capture.reset();
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

    /**
     * Parse a log capture and return a list of events at the given log level.
     *  log text looks like this:
     *  DEBUG a log message here
     *  INFO another log message
     *  etc.
     *  This method returns an array of Strings containing just the log messages after the given level
     *  e.g. for level = "INFO" and the example input above, it will return an array of a single String "another log message"
     *
     * @param level Usually one of [DEBUG|INFO|WARN|ERROR]
     * @return List of log events at the given log level
     */
    private List<String> eventsAt(String level) {
        final String logText = capture.toString();
        final Matcher m = EVENT_PATTERN.matcher(logText);

        final List<String> logEvents = new ArrayList<>();
        int startIndex = 0;
        boolean prevLevelMatch = false;

        while (startIndex < logText.length() && m.find(startIndex)) {
            if (prevLevelMatch) {
                // add from end of previous match to start of current match
                logEvents.add(logText.substring(startIndex, m.start()).trim());
                prevLevelMatch = false;
            }
            // now check if the current match is the level we're looking for
            String matchLevel = m.group(0);
            if (matchLevel != null && level.equals(matchLevel)) {
                prevLevelMatch = true;
            }
            // move the start index for the next search to the end of the current match
            startIndex = m.end();
        }
        // last match is always '$' (end of line or end of input sequence) so no need to add the last event

        return logEvents;
    }
}
