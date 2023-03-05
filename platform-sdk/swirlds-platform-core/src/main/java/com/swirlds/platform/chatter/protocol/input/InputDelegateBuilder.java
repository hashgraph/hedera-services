/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.metrics.extensions.CountPerSecond;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A builder for {@link InputDelegate}
 */
public final class InputDelegateBuilder {
    private final List<MessageTypeHandler<? extends SelfSerializable>> messageTypeHandlers = new ArrayList<>();
    private CountPerSecond stat;

    private InputDelegateBuilder() {}

    /**
     * @return a new builder
     */
    public static InputDelegateBuilder builder() {
        return new InputDelegateBuilder();
    }

    /**
     * Add a handler for a particular message type
     *
     * @param handler the handler to add
     * @return this instance
     */
    public InputDelegateBuilder addHandler(final MessageTypeHandler<? extends SelfSerializable> handler) {
        messageTypeHandlers.add(handler);
        return this;
    }

    /**
     * Use this statistic to track the number messages received per second
     *
     * @param stat the instance that will track
     * @return this instance
     */
    public InputDelegateBuilder setStat(final CountPerSecond stat) {
        this.stat = stat;
        return this;
    }

    /**
     * @return a new {@link InputDelegate}
     */
    public InputDelegate build() {
        if (messageTypeHandlers.isEmpty()) {
            throw new IllegalStateException("Add least 1 handler should be added");
        }
        return new InputDelegate(messageTypeHandlers, Objects.requireNonNull(stat));
    }
}
