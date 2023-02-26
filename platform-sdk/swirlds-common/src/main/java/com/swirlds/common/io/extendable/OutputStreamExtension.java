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

package com.swirlds.common.io.extendable;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An object that extends the functionality of an {@link OutputStream}.
 */
public interface OutputStreamExtension extends Closeable {

    /**
     * Initialize the stream extension.
     *
     * @param baseStream
     * 		the base stream that is being extended
     */
    void init(final OutputStream baseStream);

    /**
     * This method is called when the {@link OutputStream#write(int)} is invoked on the underlying stream.
     * This method is required to eventually call {@link OutputStream#write(int)} on the base stream.
     *
     * @param b
     * 		a byte to be written
     * @throws IOException
     * 		if there is a problem while writing
     */
    void write(int b) throws IOException;

    /**
     * This method is called when the {@link OutputStream#write(byte[], int, int)} is invoked on the underlying stream.
     * This method is required to eventually call {@link OutputStream#write(byte[], int, int)} on the base stream.
     *
     * @param bytes
     * 		a byte array to be written
     * @param offset
     * 		the offset of the first byte to be written
     * @param length
     * 		the number of bytes to be written
     * @throws IOException
     * 		if there is a problem while writing
     */
    void write(byte[] bytes, int offset, int length) throws IOException;
}
