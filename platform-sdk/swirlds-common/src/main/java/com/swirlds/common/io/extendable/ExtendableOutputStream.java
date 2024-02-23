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
import java.io.OutputStream;
import java.util.Objects;

/**
 * An {@link OutputStream} where data passes through it and provides methods to do extra work with that data.
 */
public class ExtendableOutputStream extends OutputStream {
    private final OutputStream stream;
    private final OutputStreamExtension extension;

    /**
     * Extend an output stream.
     *
     * @param stream
     * 		a stream to extend
     * @param extensions
     * 		zero or more extensions
     * @return an extended stream
     */
    public static OutputStream extendOutputStream(
            final OutputStream stream, final OutputStreamExtension... extensions) {

        if (extensions == null) {
            return stream;
        }
        OutputStream s = stream;
        for (final OutputStreamExtension extension : extensions) {
            s = new ExtendableOutputStream(s, extension);
        }
        return s;
    }

    /**
     * Create a new output stream.
     *
     * @param stream
     * 		the stream to wrap
     * @param extension
     * 		an extension
     */
    public ExtendableOutputStream(final OutputStream stream, final OutputStreamExtension extension) {
        this.stream = Objects.requireNonNull(stream, "stream must not be null");
        this.extension = Objects.requireNonNull(extension, "extension must not be null");
        extension.init(stream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final int b) throws IOException {
        extension.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final byte[] bytes, final int offset, final int length) throws IOException {
        extension.write(bytes, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        try {
            stream.close();
        } finally {
            extension.close();
        }
    }
}
