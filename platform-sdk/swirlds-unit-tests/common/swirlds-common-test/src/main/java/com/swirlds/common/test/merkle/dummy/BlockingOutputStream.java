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
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An output stream that can be caused to block.
 */
public class BlockingOutputStream extends OutputStream {

    private final AtomicBoolean locked;
    private final OutputStream out;

    /**
     * Create a new blocking stream by wrapping another stream.
     *
     * @param out
     * 		the stream to wrap
     */
    public BlockingOutputStream(final OutputStream out) {
        locked = new AtomicBoolean(false);
        this.out = out;
    }

    /**
     * Lock the stream. No bytes will be accepted, causing write calls to block.
     */
    public void lock() {
        locked.set(true);
    }

    /**
     * Unlock the stream. Write calls will not block.
     */
    public void unlock() {
        locked.set(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final int b) throws IOException {
        while (locked.get()) {
            Thread.onSpinWait();
        }
        out.write(b);
    }
}
