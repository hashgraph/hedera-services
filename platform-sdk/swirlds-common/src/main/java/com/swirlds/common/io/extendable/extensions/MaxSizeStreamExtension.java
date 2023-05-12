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

package com.swirlds.common.io.extendable.extensions;

import com.swirlds.common.io.extendable.extensions.internal.Counter;
import com.swirlds.common.io.extendable.extensions.internal.StandardCounter;
import com.swirlds.common.io.extendable.extensions.internal.ThreadSafeCounter;
import java.io.IOException;

/**
 * This extension causes the stream to throw an exception if too many bytes pass through it.
 */
public class MaxSizeStreamExtension extends AbstractStreamExtension {

    private final long maxByteCount;
    private final Counter counter;

    /**
     * Create a new thread safe max size extension.
     *
     * @param maxByteCount
     * 		the maximum number of bytes that are allowed to pass through the stream
     */
    public MaxSizeStreamExtension(final long maxByteCount) {
        this(maxByteCount, true);
    }

    /**
     * Create an extension that limits the maximum number of bytes that pass through the stream.
     *
     * @param maxByteCount
     * 		the maximum number of bytes that are tolerated
     * @param threadSafe
     * 		if true then this extension is safe to use with multiple streams, otherwise it should only
     * 		be used by a single thread at a time
     */
    public MaxSizeStreamExtension(final long maxByteCount, final boolean threadSafe) {
        this.maxByteCount = maxByteCount;
        if (threadSafe) {
            counter = new ThreadSafeCounter();
        } else {
            counter = new StandardCounter();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newByte(final int aByte) throws IOException {
        final long count = counter.addToCount(1);
        if (count > maxByteCount) {
            throw new IOException("number of bytes exceeds maximum of " + maxByteCount);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newBytes(final byte[] bytes, final int offset, final int length) throws IOException {
        final long count = counter.addToCount(length);
        if (count > maxByteCount) {
            throw new IOException("number of bytes exceeds maximum of " + maxByteCount);
        }
    }

    /**
     * Reset the current count.
     */
    public void reset() {
        counter.resetCount();
    }
}
