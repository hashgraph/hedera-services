/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.protocol.input;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import java.util.List;

/**
 * Handles messages of a single type
 *
 * @param <T>
 * 		the type of message
 */
public class MessageTypeHandler<T extends SelfSerializable> {
    private final List<MessageHandler<T>> handlers;
    private final Class<T> messageType;

    public MessageTypeHandler(final List<MessageHandler<T>> handlers, final Class<T> messageType) {
        this.handlers = handlers;
        this.messageType = messageType;
    }

    /**
     * If the message is the appropriate type, cast it and pass it on to handlers for the message type
     *
     * @param message
     * 		the message to cast and handle
     * @return true if the message is the appropriate type and is handled by this instance, false otherwise
     */
    public boolean castHandleMessage(final SelfSerializable message) {
        if (messageType.isInstance(message)) {
            final T cast = messageType.cast(message);
            for (final MessageHandler<T> handler : handlers) {
                handler.handleMessage(cast);
            }
            return true;
        }
        return false;
    }
}
