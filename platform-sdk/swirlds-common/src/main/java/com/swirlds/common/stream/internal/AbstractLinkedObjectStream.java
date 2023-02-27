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

package com.swirlds.common.stream.internal;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import java.util.Objects;

/**
 * This abstract class implements boiler plate functionality for a {@link LinkedObjectStream}.
 *
 * @param <T>
 * 		type of the objects to be processed by this stream
 */
public abstract class AbstractLinkedObjectStream<T extends RunningHashable & SerializableHashable>
        implements LinkedObjectStream<T> {

    private LinkedObjectStream<T> nextStream;

    protected AbstractLinkedObjectStream() {}

    protected AbstractLinkedObjectStream(final LinkedObjectStream<T> nextStream) {
        this();
        this.nextStream = nextStream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRunningHash(final Hash hash) {
        if (nextStream != null) {
            nextStream.setRunningHash(hash);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addObject(T t) {
        if (nextStream != null) {
            nextStream.addObject(Objects.requireNonNull(t));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        if (nextStream != null) {
            nextStream.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (nextStream != null) {
            nextStream.close();
        }
    }
}
