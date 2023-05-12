/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.extendable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An {@link InputStream} where data passes through it and provides methods to do extra work with that data
 */
public class ExtendableInputStream extends InputStream {

    private final InputStream stream;
    private final InputStreamExtension extension;

    /**
     * Extend an input stream.
     *
     * @param stream
     * 		a stream to extend
     * @param extensions
     * 		zero or more extensions
     * @return an extended stream
     */
    public static InputStream extendInputStream(final InputStream stream, final InputStreamExtension... extensions) {
        if (extensions == null) {
            return stream;
        }
        InputStream s = stream;
        for (final InputStreamExtension extension : extensions) {
            s = new ExtendableInputStream(s, extension);
        }
        return s;
    }

    /**
     * Create a new input stream.
     *
     * @param stream
     * 		the base stream
     * @param extension
     * 		an extension
     */
    public ExtendableInputStream(final InputStream stream, final InputStreamExtension extension) {
        this.stream = Objects.requireNonNull(stream, "stream must not be null");
        this.extension = Objects.requireNonNull(extension, "extension must not be null");
        extension.init(stream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        return extension.read();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] bytes, final int offset, final int length) throws IOException {
        return extension.read(bytes, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] readNBytes(final int length) throws IOException {
        return extension.readNBytes(length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readNBytes(final byte[] bytes, final int offset, final int length) throws IOException {
        return extension.readNBytes(bytes, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(final long n) throws IOException {
        return stream.skip(n);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void skipNBytes(final long n) throws IOException {
        stream.skipNBytes(n);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() throws IOException {
        return stream.available();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        stream.close();
        extension.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mark(final int readLimit) {
        stream.mark(readLimit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean markSupported() {
        return stream.markSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() throws IOException {
        stream.reset();
    }
}
