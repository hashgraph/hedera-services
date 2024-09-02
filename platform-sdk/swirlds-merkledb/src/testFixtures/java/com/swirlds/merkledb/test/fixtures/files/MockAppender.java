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

package com.swirlds.merkledb.test.fixtures.files;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

/**
 * This class intended to be used in unit tests to capture log messages. It is thread-safe.
 * To make it work, add the following to your test:
 * <pre>{@code
 *         final MockAppender mockAppender = new MockAppender(<test name>);
 *         Logger logger = (Logger) LogManager.getLogger(DataFileCollection.class);
 *         mockAppender.start();
 *         logger.addAppender(mockAppender);
 *  } </pre>
 *  Then call {@code mockAppender.get(<index>)} to get the log message.
 *  After the test, call {@code mockAppender.stop()} to clean up.
 *
 */
public class MockAppender extends AbstractAppender {
    private final List<String> messages = new CopyOnWriteArrayList<>();

    public MockAppender(final String name) {
        super("MockAppender-" + name, null, null, true, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void append(final LogEvent event) {
        if (isStarted()) {
            messages.add(String.format(
                    "%s - %s - %s",
                    event.getMarker().getName(),
                    event.getLevel(),
                    event.getMessage().getFormattedMessage()));
        }
    }

    /**
     * Get the number of captured log messages.
     * @return the number of captured log messages
     */
    public int size() {
        return messages.size();
    }

    /**
     * Clear previously captured the log messages.
     */
    public void clear() {
        messages.clear();
    }

    /**
     * Get the log message at the specified index.
     * @param index the index of the message
     * @return the log message
     */
    public String get(final int index) {
        return messages.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        messages.clear();
        super.stop();
    }
}
