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
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for {@link MessageHandler}
 *
 * @param <T>
 * 		the type of message
 */
public final class MessageTypeHandlerBuilder<T extends SelfSerializable> {
    private final Class<T> messageType;
    private final List<MessageHandler<T>> handlers;

    private MessageTypeHandlerBuilder(final Class<T> messageType) {
        this.messageType = messageType;
        this.handlers = new ArrayList<>();
    }

    /**
     * Create a new builder
     *
     * @param messageType
     * 		the class of the message to handle
     * @param <M>
     * 		the type defined by messageType
     * @return a new builder
     */
    public static <M extends SelfSerializable> MessageTypeHandlerBuilder<M> builder(final Class<M> messageType) {
        return new MessageTypeHandlerBuilder<>(messageType);
    }

    /**
     * Add a handler for the specified message type
     *
     * @param handler
     * 		the handler to add
     * @return this instance
     */
    public MessageTypeHandlerBuilder<T> addHandler(final MessageHandler<T> handler) {
        this.handlers.add(handler);
        return this;
    }

    /**
     * Same as {@link #addHandler(MessageHandler)} but for a list
     */
    public MessageTypeHandlerBuilder<T> addHandlers(final List<MessageHandler<T>> handlers) {
        this.handlers.addAll(handlers);
        return this;
    }

    /**
     * @return a new handler
     */
    public MessageTypeHandler<T> build() {
        return new MessageTypeHandler<>(handlers, messageType);
    }
}
