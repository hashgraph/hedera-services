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

package com.swirlds.common.test.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * A stream wrapper that can be configured to block.
 */
public class BlockingInputStream extends InputStream {

    private final InputStream baseStream;
    boolean block;

    private final CountDownLatch latch = new CountDownLatch(1);

    public BlockingInputStream(final InputStream baseStream) {
        this.baseStream = baseStream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (block) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return baseStream.read(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] readNBytes(final int len) throws IOException {
        if (block) {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                throw new IOException(e);
            }
        }

        return baseStream.readNBytes(len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readNBytes(final byte[] b, final int off, final int len) throws IOException {
        if (block) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return baseStream.readNBytes(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(final long n) throws IOException {
        return baseStream.skip(n);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void skipNBytes(final long n) throws IOException {
        baseStream.skipNBytes(n);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() throws IOException {
        return baseStream.available();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        latch.countDown();
        baseStream.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void mark(final int readlimit) {
        baseStream.mark(readlimit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void reset() throws IOException {
        baseStream.reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean markSupported() {
        return baseStream.markSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long transferTo(final OutputStream out) throws IOException {
        return baseStream.transferTo(out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {

        if (block) {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                throw new IOException(e);
            }
        }

        return baseStream.read();
    }

    /**
     * Once called, this stream will block all reads until it is closed
     */
    public void blockAllReads() {
        block = true;
    }

    /**
     * Check if the stream has been closed.
     */
    public boolean isClosed() {
        return latch.getCount() == 0;
    }
}
