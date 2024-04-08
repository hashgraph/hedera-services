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

import com.swirlds.logging.api.extensions.event.LogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.message.Message;

/**
 * Wraps a Log4J message to be used as a LogMessage in swirlds-logging.
 *
 * @param message the Log4J message
 */
public record Log4JMessage(@NonNull Message message) implements LogMessage {
    /**
     * Formats the message if the message is used by the swirlds-logging API.
     *
     * @return formatted message
     */
    @NonNull
    @Override
    public String getMessage() {
        return message.getFormattedMessage();
    }
}
