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

package com.swirlds.common.test.merkle.dummy;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An input stream that can be caused to block.
 */
public class BlockingInputStream extends InputStream {

    private final AtomicBoolean locked;
    private final InputStream in;

    /**
     * Create a new blocking stream by wrapping another stream.
     *
     * @param in
     * 		the stream to wrap
     */
    public BlockingInputStream(final InputStream in) {
        locked = new AtomicBoolean(false);
        this.in = in;
    }

    /**
     * Lock the stream, causing read calls to block.
     */
    public void lock() {
        locked.set(true);
    }

    /**
     * Unlock the stream. Read calls will not block.
     */
    public void unlock() {
        locked.set(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        while (locked.get()) {
            Thread.onSpinWait();
        }
        return in.read();
    }
}
